package fr.limsi.iles.cpm.module.process

import java.util.concurrent.Executors

import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.module.definition.ModuleDef
import fr.limsi.iles.cpm.module.value.{DIR, VAL, ModuleParameterVal}
import fr.limsi.iles.cpm.module.value._
import fr.limsi.iles.cpm.server.Server
import org.zeromq.ZMQ

import scala.reflect.io.File
import scala.sys.process._
import scala.util.Random

/**
 * Created by buiquang on 9/30/15.
 */
abstract class AProcess extends LazyLogging{
  var env : RunEnv = null
  val moduleval : AbstractModuleVal

  def initRunEnv(parentRunEnv:RunEnv) = {
    logger.debug("Initializing environement for "+moduleval.moduledef.name)
    logger.debug("Parent env contains : ")
    parentRunEnv.args.foreach(elt => {
      logger.debug(elt._1+" of type "+elt._2.getClass.toGenericString+" with value "+elt._2.asString())
    })
    val newenv = new RunEnv(Map[String,ModuleParameterVal]())
    moduleval.inputs.foreach(input=>{
      logger.info("Looking in parent env for "+input._1+" of type "+input._2.getClass.toGenericString+" with value to resolve : "+input._2.asString())
      val variables = input._2.extractVariables()
      var ready = true
      variables.foreach(variable => {
        if(!parentRunEnv.args.contains(variable)){
          ready = false
        }
      })
      if(ready){
        val resolved  = parentRunEnv.resolveValueToString(input._2.asString())
        input._2.parseFromJavaYaml(resolved)
        newenv.args += (input._1 -> input._2)
      }
    });
    // moduledef.inputs must be satisfied by inputs

    moduleval.moduledef.inputs.filter(input => {
      !input._2.value.isEmpty && !newenv.args.contains(input._1)
    }).foreach(input => {
      logger.info("Adding default value for "+input._1)
      input._2.value.get.parseFromJavaYaml(parentRunEnv.resolveValueToString(input._2.value.get.asString()))
      newenv.args += (input._1 -> input._2.value.get)
    })

    val runresultdir = DIR()
    runresultdir.parseFromJavaYaml(parentRunEnv.args("_RUN_WD").asString()+"/"+moduleval.namespace)
    newenv.args += ("_RUN_WD" -> runresultdir)

    // builtin modules haven't any real definition directory, use parent's
    val defdir = if(ModuleDef.builtinmodules.contains(moduleval.moduledef.name)){
      parentRunEnv.args("_DEF_WD")
    }else{
      val x = DIR()
      x.parseFromJavaYaml(moduleval.moduledef.wd)
      x
    }
    newenv.args += ("_DEF_WD" -> defdir)

    env = newenv
    logger.debug("Child env contains : ")
    env
      .args.foreach(elt => {
      logger.debug(elt._1+" with value "+elt._2.asString())
    })
  }

  def run(parentEnv:RunEnv,ns:String,parentPort:Option[String],detached:Boolean):Unit = {
    logger.info("Executing "+moduleval.moduledef.name)


    initRunEnv(parentEnv)



    if(detached){
      logger.debug("Launching detached supervisor")
      val executorService = Executors.newSingleThreadExecutor();
      val process = executorService.execute(new Runnable {
        override def run(): Unit = {
          runProcess(ns,parentPort,detached)
          exitRoutine(parentEnv,ns,parentPort,detached)
        }
      })
      // TODO new thread stuff etc.
      //executorService.shutdown();
    }else{
      runProcess(ns,parentPort,detached)
      exitRoutine(parentEnv,ns,parentPort,detached)
    }




  }

  protected[this] def runProcess(ns:String,parentPort:Option[String],detached:Boolean):Unit
  protected[this] def exitRoutine(env:RunEnv,ns:String,parentPort:Option[String],detached:Boolean):Unit
}

object AProcess{
  var runningProcesses = Map[Int,AProcess]()
  var portUsed = Array[String]()

  def newPort() : String = {
    // TODO optimize
    var newport = Random.nextInt(65535)
    while(newport<1024 && portUsed.exists(newport.toString == _)){
      newport = Random.nextInt(65535)
    }
    newport.toString
  }

  def getStatus(process:Int)={
    runningProcesses(process)
  }

  def getResults(process:Int,param:String)={

  }


}




class ModuleProcess(override val moduleval:ModuleVal) extends AProcess{
  var runningModules = Map[String,AProcess]()
  var completedModules = Map[String,AProcess]()

  override def runProcess(ns:String,parentPort:Option[String],detached:Boolean): Unit = {
    runSupervisor()
  }




  def runSupervisor() = {
    val socket = Server.context.socket(ZMQ.PULL)
    val processPort = AProcess.newPort()
    AProcess.portUsed = AProcess.portUsed :+ processPort
    socket.bind ("tcp://*:"+processPort)
    logger.info("New supervisor at port "+processPort+" for module "+moduleval.moduledef.name)
    while(completedModules.size != moduleval.moduledef.run.size){

      next(processPort)

      val message :ProcessMessage= socket.recvStr()

      message match {
        case ValidProcessMessage(sender,status) => status match {
          case "FINISHED" => {
            logger.debug(sender + " just finished")
            // TODO message should contain new env data?
            // anyway update env here could be good since there is no need to lock...
            completedModules += (sender -> runningModules(sender))
            if(completedModules.size != moduleval.moduledef.run.size){
              next(processPort)
            }
          }
          case s : String => logger.warn("WTF? : "+s)
        }
        case InvalidProcessMessage() => "ow shit"
      }
    }
  }

  /**
   *
   * @param parentPort
   */
  def next(parentPort:String) = {
    // TODO add strict mode where the modules are launched one by one following the list definition order
    logger.debug("Trying to run next submodule for module "+moduleval.moduledef.name)
    val runnableModules = moduleval.moduledef.run.filter(module => {
      if(runningModules.contains(module.namespace)){
        false
      }else{
        module.inputs.aggregate(true)((result,input) => {
          val vars = input._2.extractVariables()
          var exist = true
          vars.foreach(varname => {
            logger.info("Looking for "+varname)
            val varexist = env.args.exists(varname == _._1)
            exist = exist && varexist
            if(varexist) logger.info("found") else logger.info("not found")
          })
          exist
        },(a,b)=>{a&&b})
      }
    });
    runnableModules.foreach(module => {
      logger.debug("Launching "+module.moduledef.name)
      val process = module.toProcess()
      runningModules += (module.namespace -> process)
      process.run(env,moduleval.moduledef.name,Some(parentPort),true) // not top level modules (called by cpm cli) always run demonized
    })
  }

  override protected[this] def exitRoutine(parentEnv: RunEnv, ns: String, parentPort: Option[String], detached: Boolean): Unit = {
    logger.debug("Finished processing for module "+moduleval.moduledef.name+", connecting to parent socket")
    val socket = parentPort match {
      case Some(port) => {
        val socket = Server.context.socket(ZMQ.PUSH)
        socket.connect("tcp://localhost:"+port)
        logger.info("Connected")
        Some(socket)
      }
      case None => {
        // this is top level module run = "pipeline"
        None
      }
    }
    logger.debug("Setting results to parent env")
    // set outputs value to env
    moduleval.moduledef.outputs.foreach(output=>{
      logger.debug("Process env contains : ")
      env.args.foreach(elt => {
        logger.debug(elt._1+" with value "+elt._2.asString())
      })
      logger.debug("Looking to resolve : "+output._2.value.get.asString())
      val x = FILE()
      logger.debug("Found :"+env.resolveValueToString(output._2.value.get.asString()))
      x.parseFromJavaYaml(env.resolveValueToString(output._2.value.get.asString()))
      val namespace = moduleval.namespace match {
        case "" => ""
        case _ => moduleval.namespace+"."
      }
      parentEnv.args += (namespace+output._1 -> x)
      logger.debug("New parent env contains : ")
      parentEnv.args.foreach(elt => {
        logger.debug(elt._1+" with value "+elt._2.asString())
      })
    });

    socket match {
      case Some(sock) => {
        logger.debug("Sending completion signal")
        sock.send(new ValidProcessMessage(moduleval.namespace,"FINISHED"))
      }
      case None => {
        logger.info("Finished executing "+moduleval.moduledef.name)
      }
    }

  }
}


class CMDProcess(override val moduleval:CMDVal) extends AProcess{
  var stdoutval : VAL = VAL()
  var stderrval : VAL = VAL()

  override def runProcess(ns:String,parentPort:Option[String],detached:Boolean): Unit = {

    logger.debug("Launching CMD "+env.resolveValueToString(moduleval.inputs("CMD").asString()))
    var stderr = ""
    var stdout = ""
    val wd = env.args("_DEF_WD").asString()
    val folder = new java.io.File(wd)
    DockerManager.baseRun(moduleval.namespace,"localhost",parentPort.get,env.resolveValueToString(moduleval.inputs("CMD").asString()),folder)
    //Process(env.resolveVars(moduleval.inputs("CMD").asString()),new java.io.File(wd)) ! ProcessLogger(line => stdout+="\n"+line,line=>stderr+="\n"+line)
    stdoutval.rawValue = stdout
    stdoutval.resolvedValue = stdout

    stderrval.rawValue = stderr
    stderrval.resolvedValue = stderr


  }

  override protected[this] def exitRoutine(parentEnv: RunEnv, ns: String, parentPort: Option[String], detached: Boolean): Unit = {
    parentEnv.logs += (moduleval.namespace -> stderrval)
    parentEnv.args += (moduleval.namespace+".STDOUT" -> stdoutval)
/*
    val socket = Server.context.socket(ZMQ.PUSH)
    socket.connect("tcp://localhost:"+parentPort.get)
    socket.send(new ValidProcessMessage(moduleval.namespace,"FINISHED"))*/
  }

}

class MAPProcess(override val moduleval:MAPVal) extends AProcess{
  override protected[this] def runProcess(ns: String, parentPort: Option[String], detached: Boolean): Unit = {
    val dir = new java.io.File(env.resolveValueToString(moduleval.inputs("IN").asString()))

    dir.listFiles().map(file => {

    })
    /*
    val cluster = input("IN")
    cluster.map(item =>
      {
        env.args += ("_" => item)
        moduleval.anonymousmodule.toProcess().run()
      }
    )



     */
  }

  override protected[this] def exitRoutine(env: RunEnv, ns: String, parentPort: Option[String], detached: Boolean): Unit = {
    val socket = Server.context.socket(ZMQ.PUSH)
    socket.connect("tcp://localhost:"+parentPort.get)
    socket.send(new ValidProcessMessage(moduleval.namespace,"FINISHED"))
  }
}

class FILTERProcess(){

}

class FILTERMAPProcess(){

}
