package fr.limsi.iles.cpm.module.process

import java.util.UUID
import java.util.concurrent.Executors

import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.module.definition.{AnonymousDef, ModuleDef}
import fr.limsi.iles.cpm.module.value.{DIR, VAL, AbstractParameterVal}
import fr.limsi.iles.cpm.module.value._
import fr.limsi.iles.cpm.server.Server
import org.zeromq.ZMQ

import scala.reflect.io.File
import scala.sys.process._
import scala.util.Random

abstract class ProcessStatus
case class Running() extends ProcessStatus
case class Exited(exitcode:String) extends ProcessStatus
case class Waiting() extends ProcessStatus


/**
 * Created by buiquang on 9/30/15.
 */
abstract class AbstractProcess() extends LazyLogging{
  val id = UUID.randomUUID()
  var parentEnv : RunEnv = null
  var env : RunEnv = null
  val moduleval : AbstractModuleVal
  var parentPort : Option[String] = None
  val processPort = {
    val newport = AbstractProcess.newPort()
    AbstractProcess.portUsed = AbstractProcess.portUsed :+ newport
    newport
  }

  var status : ProcessStatus = Waiting() // running | returncode
  var resultnamespace : String = null
//  var rawlog : List[String]


  protected[this] def step():Unit
  protected[this] def update(message:ProcessMessage)
  protected[this] def endCondition() : Boolean
  protected[this] def updateParentEnv():Unit


  def run(parentEnv:RunEnv,ns:String,parentPort:Option[String],detached:Boolean):UUID = {
    this.parentPort = parentPort
    status match {
      case Running() => throw new Exception("Process already running")
      case Waiting() => status = Running()
      case Exited(errorcode) => throw new Exception("Process already run and exited with status code "+errorcode)
    }
    logger.info("Executing "+moduleval.moduledef.name)

    resultnamespace = ns
    // init runenv from parent env
    try{
      initRunEnv(parentEnv)
    }catch{
      case e:Throwable => logger.error(e.getMessage); exitRoutine(); return id

    }



    if(detached){
      logger.debug("Launching detached supervisor")
      val executorService = Executors.newSingleThreadExecutor()
      val process = executorService.execute(new Runnable {
        override def run(): Unit = {
          runSupervisor()
        }
      })
      // TODO new thread stuff etc.
      //executorService.shutdown();
    }else{
      runSupervisor()
    }
    id
  }





  private def runSupervisor() = {
    val socket = Server.context.socket(ZMQ.PULL)
    socket.bind("tcp://*:" + processPort)
    logger.info("New supervisor at port " + processPort + " for module " + moduleval.moduledef.name)

    try{
      while (!endCondition()) {

        step() // step run

        val message: ProcessMessage = socket.recvStr()

        update(message) // update after step

      }

    }catch{
      case e:Throwable => logger.error(e.getMessage) ; exitRoutine()
    }


    exitRoutine()
  }

  protected def initRunEnv(parentRunEnv:RunEnv) = {
    logger.debug("Initializing environement for "+moduleval.moduledef.name)
    logger.debug("Parent env contains : ")
    parentRunEnv.args.foreach(elt => {
      logger.debug(elt._1+" of type "+elt._2.getClass.toGenericString+" with value "+elt._2.asString())
    })
    parentEnv = parentRunEnv

    var newargs = Map[String,AbstractParameterVal]()

    val runresultdir = DIR()
    runresultdir.parseYaml(parentRunEnv.args("_RUN_DIR").asString()+"/"+moduleval.namespace)
    val newdir = new java.io.File(runresultdir.asString())
    newdir.mkdirs()
    newargs += ("_RUN_DIR" -> runresultdir)

    // builtin modules haven't any real definition directory, use parent's
    val defdir = if(ModuleDef.builtinmodules.contains(moduleval.moduledef.name)){
      parentRunEnv.args("_DEF_DIR")
    }else{
      val x = DIR()
      x.parseYaml(moduleval.moduledef.wd)
      x
    }
    newargs += ("_DEF_DIR" -> defdir)


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

        variables.foreach(variable => {
          val value = if(moduleval.moduledef.inputs.contains(variable)){
            moduleval.moduledef.inputs(variable).createVal()
          }else{
            parentEnv.args(variable).newEmpty()
          }
          value.fromYaml(parentRunEnv.args(variable).asString())
          newargs += (variable -> value)
        })
      }
    });

    moduleval.moduledef.inputs.filter(input => {
      !input._2.value.isEmpty && !newargs.contains(input._1)
    }).foreach(input => {
      logger.info("Adding default value for "+input._1)
      val value = input._2.createVal()
      value.parseYaml(parentRunEnv.resolveValueToString(input._2.value.get.asString()))
      newargs += (input._1 -> value)
    })


    // moduledef.inputs must be satisfied by inputs



    val newenv = new RunEnv(newargs)
    env = newenv
    logger.debug("Child env contains : ")
    env.args.foreach(elt => {
      logger.debug(elt._1+" with value "+elt._2.asString())
    })
  }


  private[this] def exitRoutine(): Unit = {
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
    updateParentEnv()

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

object AbstractProcess{
  var runningProcesses = Map[Int,AbstractProcess]()
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





class ModuleProcess(override val moduleval:ModuleVal) extends AbstractProcess{
  var runningModules = Map[String,AbstractProcess]()
  var completedModules = Map[String,AbstractProcess]()

  override def endCondition():Boolean={
    completedModules.size == moduleval.moduledef.exec.size
  }


  override def update(message:ProcessMessage)={
    message match {
      case ValidProcessMessage(sender,status) => status match {
        case "FINISHED" => {
          logger.debug(sender + " just finished")
          // TODO message should contain new env data?
          // anyway update env here could be good since there is no need to lock...

          completedModules += (sender -> runningModules(sender))
        }
        case s : String => logger.warn("WTF? : "+s)
      }
      case InvalidProcessMessage() => "ow shit"
    }
  }




  /**
   *
   */
  override def step() = {
    // TODO add strict mode where the modules are launched one by one following the list definition order
    logger.debug("Trying to run next submodule for module "+moduleval.moduledef.name)
    val runnableModules = moduleval.moduledef.exec.filter(module => {
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
      process.run(env,moduleval.moduledef.name,Some(processPort),true) // not top level modules (called by cpm cli) always run demonized
    })
  }

  override def updateParentEnv() = {
    moduleval.moduledef.outputs.foreach(output=>{
      logger.debug("Process env contains : ")
      env.args.foreach(elt => {
        logger.debug(elt._1+" with value "+elt._2.asString())
      })
      logger.debug("Looking to resolve : "+output._2.value.get.asString())
      val x = output._2.createVal()
      logger.debug("Found :"+env.resolveValueToString(output._2.value.get.asString()))
      x.parseYaml(env.resolveValueToString(output._2.value.get.asString()))
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
  }


}


class CMDProcess(override val moduleval:CMDVal) extends AbstractProcess{
  var stdoutval : VAL = VAL()
  var stderrval : VAL = VAL()
  var run = false

  override def step(): Unit = {
    run = true
    logger.debug("Launching CMD "+env.resolveValueToString(moduleval.inputs("CMD").asString()))
    var stderr = ""
    var stdout = ""
    val wd = env.args("_DEF_DIR").asString()
    val folder = new java.io.File(wd)
    DockerManager.baseRun(moduleval.namespace,"localhost",parentPort.get,env.resolveValueToString(moduleval.inputs("CMD").asString()),folder)
    //Process(env.resolveVars(moduleval.inputs("CMD").asString()),new java.io.File(wd)) ! ProcessLogger(line => stdout+="\n"+line,line=>stderr+="\n"+line)
    stdoutval.rawValue = stdout
    stdoutval.resolvedValue = stdout

    stderrval.rawValue = stderr
    stderrval.resolvedValue = stderr


  }

  override protected[this] def updateParentEnv(): Unit = {
    parentEnv.logs += (moduleval.namespace -> stderrval)
    parentEnv.args += (moduleval.namespace+".STDOUT" -> stdoutval)
/*
    val socket = Server.context.socket(ZMQ.PUSH)
    socket.connect("tcp://localhost:"+parentPort.get)
    socket.send(new ValidProcessMessage(moduleval.namespace,"FINISHED"))*/
  }

  override protected[this] def update(message: ProcessMessage): Unit = {

  }

  override protected[this] def endCondition(): Boolean = {
    run
  }
}

class MAPProcess(override val moduleval:MAPVal) extends AbstractProcess{
  val dir = new java.io.File(env.resolveValueToString(moduleval.inputs("IN").asString()))
  var chunksize = Integer.valueOf(env.resolveValueToString(moduleval.inputs("CHUNK_SIZE").asString()))
  val modules : List[AbstractModuleVal] = paramToScalaListModval(moduleval.inputs("RUN").asInstanceOf[LIST[MODVAL]])

  val files = dir.listFiles()
  var offset = 0



  def paramToScalaListModval(modulevallist:LIST[MODVAL]) : List[AbstractModuleVal]={
    var modulelist : List[AbstractModuleVal]= List[AbstractModuleVal]()
    modulevallist.list.foreach(modval=>{
      modulelist ::= modval.moduleval
    })
    modulelist.reverse
  }

  override protected[this] def updateParentEnv(): Unit = {
    modules.foreach(module=>{
      module.moduledef.outputs.foreach(el=>{
        parentEnv.args += (resultnamespace+"._MAP."+module.namespace+"."+el._1 -> env.resolveValue(el._2.value.get.asString()))
      })
    })
  }

  override protected [this] def update(message:ProcessMessage)={
    chunksize = 1
  }

  override protected[this] def endCondition():Boolean = {
    offset>=files.length
  }

  override protected[this] def step()={
    val to = if(offset+chunksize>=files.length){
      files.length-1
    }else{
      offset + chunksize
    }

    val toProcessFiles = files.slice(offset,to)

    toProcessFiles.foreach(file => {
      val newenv = env
      val x = FILE()
      x.parseYaml(file.getCanonicalPath)
      newenv.args += ("_" -> x)
      val module = new AnonymousDef(modules)
      val process = module.toProcess()
      process.run(newenv,moduleval.namespace,Some(processPort),true)
    })
  }

}

class FILTERProcess(){

}

class FILTERMAPProcess(){

}
