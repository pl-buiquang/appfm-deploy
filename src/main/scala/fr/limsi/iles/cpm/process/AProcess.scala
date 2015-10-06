package fr.limsi.iles.cpm.process

import java.util.concurrent.Executors

import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.core.Server
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
      logger.debug(elt._1+" with value "+elt._2.asString())
    })
    val newenv = new RunEnv(Map[String,ModuleParameterVal]())
    moduleval.inputs.foreach(input=>{
      logger.info("Looking in parent env for "+input._1+" with value to resolve :"+input._2.asString())
      input._2.parseFromJavaYaml(parentRunEnv.resolveVars(input._2.asString()))
      newenv.args += (input._1 -> input._2)
    });
    // moduledef.inputs must be satisfied by inputs

    moduleval.moduledef.inputs.filter(input => {
      !input._2.value.isEmpty && !newenv.args.contains(input._1)
    }).foreach(input => {
      logger.info("Adding default value for "+input._1)
      input._2.value.get.parseFromJavaYaml(parentRunEnv.resolveVars(input._2.value.get.asString()))
      newenv.args += (input._1.substring(1) -> input._2.value.get)
    })

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


class ProcessMessage

case class ValidProcessMessage(val sender:String,val status:String) extends ProcessMessage{

  override def toString(): String = {
    sender+"\n"+status
  }
}

case class InvalidProcessMessage() extends ProcessMessage

object ProcessMessage{

  implicit def parse(message:String) : ProcessMessage = {
    val components = message.split("\n")
    if(components.size==2){
      new ValidProcessMessage(components(0),components(1))
    }else{
      new InvalidProcessMessage()
    }
  }

  implicit def toString(message:ProcessMessage) : String = {
    message.toString()
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
   * TODO problem no check if module has already been launched!!
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
            exist = exist && env.args.exists(varname == _._1)
            if(exist) logger.info("found") else logger.info("not found")
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
      logger.debug("Found :"+env.resolveVars(output._2.value.get.asString()))
      x.parseFromJavaYaml(env.resolveVars(output._2.value.get.asString()))
      parentEnv.args += (moduleval.namespace+"."+output._1.substring(1) -> x)
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

    logger.debug("Launching CMD "+env.resolveVars(moduleval.inputs("CMD").asString()))
    var stderr = ""
    var stdout = ""
    val wd = "."//env.resolveVars(moduleval.inputs("WD").asString())
    Process(env.resolveVars(moduleval.inputs("CMD").asString()),new java.io.File(wd)) ! ProcessLogger(line => stdout+="\n"+line,line=>stderr+="\n"+line)
    stdoutval.rawValue = stdout
    stdoutval.resolvedValue = stdout

    stderrval.rawValue = stderr
    stderrval.resolvedValue = stderr


  }

  override protected[this] def exitRoutine(parentEnv: RunEnv, ns: String, parentPort: Option[String], detached: Boolean): Unit = {
    parentEnv.logs += (moduleval.namespace -> stderrval)
    parentEnv.args += (moduleval.namespace+".STDOUT" -> stdoutval)

    val socket = Server.context.socket(ZMQ.PUSH)
    socket.connect("tcp://localhost:"+parentPort.get)
    socket.send(new ValidProcessMessage(moduleval.namespace,"FINISHED"))
  }

}

class MAPProcess(){

}

class FILTERProcess(){

}

class FILTERMAPProcess(){

}
