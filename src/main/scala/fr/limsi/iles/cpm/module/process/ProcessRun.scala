package fr.limsi.iles.cpm.module.process

import java.util.UUID
import java.util.concurrent.Executors

import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.module.definition.{AnonymousDef, ModuleDef}
import fr.limsi.iles.cpm.module.parameter.AbstractModuleParameter
import fr.limsi.iles.cpm.module.value.{DIR, VAL, AbstractParameterVal}
import fr.limsi.iles.cpm.module.value._
import fr.limsi.iles.cpm.server.Server
import fr.limsi.iles.cpm.utils.{Utils, ConfManager}
import org.zeromq.ZMQ

import scala.reflect.io.File
import scala.sys.process._
import scala.util.Random

abstract class ProcessStatus {
  override def toString():String={
    this.getClass.getSimpleName
  }
}
case class Running() extends ProcessStatus
case class Exited(exitcode:String) extends ProcessStatus {
  override def toString():String={
    this.getClass.getSimpleName+"("+exitcode+")"
  }
}
case class Waiting() extends ProcessStatus


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

  def fromMongoDBObject(obj:BasicDBObject):AbstractProcess = {
    /*obj.get("type") match {
      case "CMD" =>
      case "MAP" =>
      case "CUSTOM" => new CMDProcess(new CMDVal("",None),None,UUID.fromString(obj.get("ruid").toString))
      case "ANONYMOUS" =>
      case _ =>
    }*/
    new CMDProcess(new CMDVal("",None),None,UUID.fromString(obj.get("ruid").toString))
  }


}

/**
 * Created by buiquang on 9/30/15.
 */
abstract class AbstractProcess(val parentProcess:Option[AbstractProcess],val id :UUID) extends LazyLogging{

  var parentEnv : RunEnv = null
  var env : RunEnv = null
  val moduleval : AbstractModuleVal
  var parentPort : Option[String] = None
  var processPort = {
    val newport = AbstractProcess.newPort()
    AbstractProcess.portUsed = AbstractProcess.portUsed :+ newport
    newport
  }

  var childrenProcess = List[UUID]()

  var status : ProcessStatus = Waiting() // running | returncode
  var resultnamespace : String = null
//  var rawlog : List[String]

  ProcessRunManager.list += (id -> this)


  protected[this] def postInit():Unit={}
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

    // save process to db
    this.saveStateToDB()

    resultnamespace = ns
    // init runenv from parent env
    try{
      initRunEnv(parentEnv)
      postInit()
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
    var connected = 10
    while(connected!=0)
    try {
      socket.bind("tcp://*:" + processPort)
      connected = 0
    }catch {
      case e:Throwable => {
        processPort = AbstractProcess.newPort()
        connected -= 1
        logger.info("Couldn't connect at port "+processPort+" retrying (try : "+(10-connected)+")")
      }
    }
      logger.info("New supervisor at port " + processPort + " for module " + moduleval.moduledef.name)

    try{
      while (!endCondition()) {

        step() // step run

        val rawmessage = socket.recvStr()
        val message: ProcessMessage = rawmessage

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
    runresultdir.fromYaml(parentRunEnv.args("_RUN_DIR").asString()+"/"+moduleval.namespace)
    val newdir = new java.io.File(runresultdir.asString())
    newdir.mkdirs()
    newargs += ("_RUN_DIR" -> runresultdir)

    // builtin modules haven't any real definition directory, use parent's
    val defdir = if(ModuleDef.builtinmodules.contains(moduleval.moduledef.name)){
      parentRunEnv.args("_DEF_DIR")
    }else{
      val x = DIR()
      x.fromYaml(moduleval.moduledef.defdir)
      x
    }
    newargs += ("_DEF_DIR" -> defdir)

    val (mod_context,cur_mod) = if(ModuleDef.builtinmodules.contains(moduleval.moduledef.name)){
      val x = VAL()
      x.fromYaml("_MAIN")
      (parentRunEnv.args.getOrElse("_CUR_MOD",x),parentRunEnv.args.getOrElse("_CUR_MOD",x))
    }else{
      val x = VAL()
      x.fromYaml(moduleval.moduledef.name)
      (parentRunEnv.args.getOrElse("_CUR_MOD",x),x)
    }
    newargs += ("_MOD_CONTEXT" -> mod_context)
    newargs += ("_CUR_MOD" -> cur_mod)

    val donotoverride = List("_MOD_CONTEXT","_CUR_MOD","_DEF_DIR","_RUN_DIR")
    if(moduleval.moduledef.name == "_ANONYMOUS"){
      parentEnv.args.filter(arg => {
        !donotoverride.contains(arg._1)
      }).foreach(arg => {
        newargs += (arg._1 -> arg._2)
      })

    }else{
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
          logger.info("Found")
          if(moduleval.moduledef.name == "_CMD"){
            input._2.fromYaml(parentEnv.resolveValueToString(input._2.toYaml()))
          }else{
            input._2.fromYaml(parentEnv.resolveValueToYaml(input._2.toYaml()))
          }
          newargs += (input._1 -> input._2)
          /*
          variables.foreach(variable => {
            val value = if(moduleval.moduledef.inputs.contains(variable)){
              moduleval.moduledef.inputs(variable).createVal()
            }else{
              parentEnv.args(variable).newEmpty()
            }
            value.fromYaml(parentRunEnv.args(variable).asString())
            newargs += (variable -> value)
          })*/
        }
      });


    }

    // done in moduleval initialization
    moduleval.moduledef.inputs.filter(input => {
      !input._2.value.isEmpty && !newargs.contains(input._1)
    }).foreach(input => {
      logger.info("Adding default value for "+input._1)
      val value = input._2.createVal() //val value = moduleval.inputs(input._1) //
      value.fromYaml(parentRunEnv.resolveValueToYaml(input._2.value.get.toYaml()))
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


    status = Exited("0")
    //ProcessRunManager.list -= id
    saveStateToDB()


    socket match {
      case Some(sock) => {
        logger.debug("Sending completion signal")
        sock.send(new ValidProcessMessage(moduleval.namespace,"FINISHED","0"))
      }
      case None => {
        logger.info("Finished executing "+moduleval.moduledef.name)
      }
    }

  }

  private def serializeToMongoObject() : MongoDBObject = {
    val obj = MongoDBObject(
      "ruid" -> id.toString,
      "def" -> moduleval.moduledef.confFilePath,
      "name" -> moduleval.moduledef.name,
      "status" -> status.toString(),
      "master" -> {parentProcess match {
        case Some(thing) => false
        case None => true
      }},
      "children" -> childrenProcess.foldLeft("")((agg:String,el:UUID)=>{
        if(agg!="")
          agg+","+el.toString
        else
          el.toString
      }),
      "env" -> {
        env match {
          case x:RunEnv => x.serialize()
          case _ => ""
        }
      }
    )
    obj
  }

  def getStatus(recursive:Boolean):String={
    if(recursive){
      this.moduleval.namespace+" : "+this.status.toString() +
      childrenProcess.reverse.foldLeft("")((agg,childid)=>{
        agg+"\n"+Utils.addOffset("\t",ProcessRunManager.getProcess(childid).getStatus(recursive))
      })
    }else{
      this.status.toString()
    }
  }


  def saveStateToDB() : Boolean = {
    val query = MongoDBObject("ruid"->id.toString)
    val result = if(ProcessRunManager.processCollection.find(query).count()>0){
      ProcessRunManager.processCollection.update(query,this.serializeToMongoObject())
    }else{
      ProcessRunManager.processCollection.insert(this.serializeToMongoObject())
    }

    // TODO check if everything went fine
    true
  }
}






class ModuleProcess(override val moduleval:ModuleVal,override val parentProcess:Option[AbstractProcess],override val id:UUID) extends AbstractProcess(parentProcess,id){
  def this(moduleval:ModuleVal,parentProcess:Option[AbstractProcess]) = this(moduleval,parentProcess,UUID.randomUUID())

  var runningModules = Map[String,AbstractProcess]()
  var completedModules = Map[String,AbstractProcess]()

  override def endCondition():Boolean={
    completedModules.size == moduleval.moduledef.exec.size
  }


  override def update(message:ProcessMessage)={
    message match {
      case ValidProcessMessage(sender,status,exitval) => status match {
        case "FINISHED" => {
          logger.debug(sender + " just finished")
          // TODO message should contain new env data?
          // anyway update env here could be good since there is no need to lock...

          completedModules += (sender -> runningModules(sender))
        }
        case s : String => logger.warn("WTF? : "+s)
      }
      case _ => "ow shit"
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
        module.isExecutable(env)
      }
    });
    runnableModules.foreach(module => {
      logger.debug("Launching "+module.moduledef.name)
      val process = module.toProcess(Some(this))
      if(module.moduledef.name=="_MAP"){
        process.asInstanceOf[MAPProcess].parentInputsDef = moduleval.moduledef.inputs
        var context = List[AbstractModuleVal]()
        runningModules.foreach(elt => {
          context ::= elt._2.moduleval
        })
        process.asInstanceOf[MAPProcess].context = context
      }
      runningModules += (module.namespace -> process)
      childrenProcess ::= process.id
      this.saveStateToDB()
      process.run(env,moduleval.namespace,Some(processPort),true) // not top level modules (called by cpm cli) always run demonized
    })
  }

  override def updateParentEnv() = {
    logger.debug("Process env contains : ")
    env.args.foreach(elt => {
      logger.debug(elt._1+" with value "+elt._2.asString())
    })
    moduleval.moduledef.outputs.foreach(output=>{
      logger.debug("Looking to resolve : "+output._2.value.get.asString())
      val x = output._2.createVal()
      logger.debug("Found :"+env.resolveValueToYaml(output._2.value.get.asString()))
      x.fromYaml(env.resolveValueToYaml(output._2.value.get.toYaml()))
      val namespace = moduleval.namespace match {
        case "" => ""
        case _ => moduleval.namespace+"."
      }
      parentEnv.args += (namespace+output._1 -> x)
    });
    logger.debug("New parent env contains : ")
    parentEnv.args.foreach(elt => {
      logger.debug(elt._1+" with value "+elt._2.asString())
    })
  }


}

class AnonymousModuleProcess(override val moduleval:ModuleVal,override val parentProcess:Option[AbstractProcess],override val id:UUID)  extends ModuleProcess(moduleval,parentProcess,id){
  def this(moduleval:ModuleVal,parentProcess:Option[AbstractProcess]) = this(moduleval,parentProcess,UUID.randomUUID())

  override def updateParentEnv() = {
    logger.debug("Process env contains : ")
    (env.args ++ moduleval.inputs).foreach(elt => {
      logger.debug(elt._1+" with value "+elt._2.asString())
    })
    parentEnv.args ++= env.args.filter(elt => {

      moduleval.moduledef.exec.foldLeft(false)((agg,modval) => {
        agg || elt._1.startsWith(modval.namespace)
      })

    }).foldLeft(Map[String,AbstractParameterVal]())((map,elt)=>{map + (moduleval.namespace+"."+elt._1->elt._2)})
    logger.debug("New parent env contains : ")
    parentEnv.args.foreach(elt => {
      logger.debug(elt._1+" with value "+elt._2.asString())
    })
  }
}


class CMDProcess(override val moduleval:CMDVal,override val parentProcess:Option[AbstractProcess],override val id:UUID) extends AbstractProcess(parentProcess,id){
  def this(moduleval:CMDVal,parentProcess:Option[AbstractProcess]) = this(moduleval,parentProcess,UUID.randomUUID())
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

    val dockerimage = {
      env.resolveValueToString(moduleval.inputs("DOCKERFILE").toYaml()) match {
        case ConfManager.defaultDockerBaseImage => ConfManager.defaultDockerBaseImage
        case x :String => {
          val name = env.args("_MOD_CONTEXT").asString()
          if(!DockerManager.exist(name)){
            DockerManager.build(name,x)
          }
          name
        }
        case _ =>  ConfManager.defaultDockerBaseImage
      }
    }
    DockerManager.run(this.id,moduleval.namespace,"localhost",processPort,env.resolveValueToString(moduleval.inputs("CMD").asString()),folder,dockerimage)
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



class MAPProcess(override val moduleval:MAPVal,override val parentProcess:Option[AbstractProcess],override val id:UUID) extends AbstractProcess(parentProcess,id){
  def this(moduleval:MAPVal,parentProcess:Option[AbstractProcess]) = this(moduleval,parentProcess,UUID.randomUUID())
  var values = Map[String,Any]()

  var offset = 0
  var parentInputsDef : Map[String,AbstractModuleParameter] = Map[String,AbstractModuleParameter]()
  var context : List[AbstractModuleVal] = List[AbstractModuleVal]()

  override def postInit():Unit={
    values += ("dir" -> new java.io.File(moduleval.getInput("IN",env).asString()))
    values += ("chunksize" -> Integer.valueOf(moduleval.getInput("CHUNK_SIZE",env).asString()))
    val modvals = moduleval.getInput("RUN",env).asInstanceOf[LIST[MODVAL]]
    values += ("modules" -> AbstractParameterVal.paramToScalaListModval(modvals))
    values += ("process" -> List[AbstractProcess]())
    values += ("completed" -> 0)
  }



  override protected[this] def updateParentEnv(): Unit = {
    logger.debug("Process env contains : ")
    env.args.foreach(elt => {
      logger.debug(elt._1+" with value "+elt._2.asString())
    })

    val namespace = resultnamespace match {
      case "" => ""
      case _ => resultnamespace+"."
    }

    parentEnv.args ++= env.args.filter(elt => {
      elt._1.startsWith("_MAP.")
    }).groupBy[String](el=>{
      val modnamestartindex = el._1.substring(5).indexOf(".")
      val modnameendindex = el._1.substring(5+modnamestartindex+1).indexOf(".")
      "_MAP."+el._1.substring(5+modnamestartindex+1)//.substring(0,modnameendindex)
    }).transform((key,content) => {
      // TODO know whichever the fuck is the type and create the proper list type
      /**
       * content.head._2._mytype+"*"
       */

      val newel = LIST[AbstractParameterVal]()
      newel.list = List[AbstractParameterVal]()
      content.foldLeft(newel)((agg,elt) => {
        agg.list ::= elt._2
        agg
      })
    })

      /*.aggregate(Map[String,AbstractParameterVal]())((agg,el)=>{
      val modnamestartindex = el._1.substring(5).indexOf(".")
      val modnameendindex = el._1.substring(5+modnamestartindex+1).indexOf(".")
      val modname = el._1.substring(5+modnamestartindex+1).substring(0,modnameendindex)
      var ellist = AbstractModuleParameter.createVal(el._2._mytype+"*")
      ellist.parseYaml(el._2.asString())
      agg + (modname -> ellist)
    },(el1,el2) => {
      var el0 = Map[String,AbstractParameterVal]()
      el1.foreach(el => {
        el0 += ("_MAP."+el._1 -> el._2)
      })
      el2.foreach(el => {
        if(!el0.contains(el._1)){
          el0 += (el._1 -> el._2)
        }else{
          (el0(el._1).asInstanceOf[LIST[AbstractParameterVal]]).list :::= el._2.asInstanceOf[LIST[AbstractParameterVal]].list
        }
      })
      el0
    })*/
      /**/

    /*parentEnv.args ++= env.args.filter(elt => {
      elt._1.startsWith("_MAP.")
    }).foldLeft(Map[String,AbstractParameterVal]())((map,elt)=>{map + (namespace+"_MAP."+elt._1->elt._2)})*/
    /*values("process").asInstanceOf[List[AbstractProcess]].foreach(process=>{
      process.moduleval.moduledef.outputs.foreach(el=>{
        if(!el._2.value.isEmpty){
          parentEnv.args += (
            resultnamespace+"._MAP."+process.moduleval.namespace+"."+el._1
            ->
              RunEnv.resolveValue(env.args++process.moduleval.inputs.mapValues(input => {env.resolveValue(input)}),el._2.value.get)
            )
        }
      })
    })*/
    logger.debug("New parent env contains : ")
    parentEnv.args.foreach(elt => {
      logger.debug(elt._1+" with value "+elt._2.asString())
    })
  }

  override protected [this] def update(message:ProcessMessage)={
    values += ("chunksize" -> 1)
    val n : Int= values("completed").asInstanceOf[Int]
    values += ("completed" -> (n+1))
  }

  override protected[this] def endCondition():Boolean = {
    offset>=values("dir").asInstanceOf[java.io.File].listFiles().length &&
      values("completed").asInstanceOf[Int] == (values("process").asInstanceOf[List[AbstractProcess]]).length
  }

  override protected[this] def step()={
    val to = if(offset+values("chunksize").asInstanceOf[Int]>=values("dir").asInstanceOf[java.io.File].listFiles().length){
      values("dir").asInstanceOf[java.io.File].listFiles().length
    }else{
      offset + values("chunksize").asInstanceOf[Int]
    }

    val toProcessFiles = values("dir").asInstanceOf[java.io.File].listFiles().slice(offset,to)

    var i = 0;
    toProcessFiles.foreach(file => {
      val newenv = env
      val x = FILE()
      x.fromYaml(file.getCanonicalPath)
      newenv.args += ("_" -> x)

      val module = new AnonymousDef(values("modules").asInstanceOf[List[AbstractModuleVal]],context,parentInputsDef)

      val moduleval = new ModuleVal("_MAP."+(offset+i).toString,module,Some(Utils.scalaMap2JavaMap(newenv.args.mapValues(paramval => {
        paramval.toYaml()
      }))))
      i+=1
      val process = new AnonymousModuleProcess(moduleval,Some(this))
      childrenProcess ::= process.id
      this.saveStateToDB()
      var list = values("process").asInstanceOf[List[AbstractProcess]]
      list ::= process
      values += ("process" -> list)
      process.run(newenv,moduleval.namespace,Some(processPort),true)
    })

    offset = to
  }

}

class FILTERProcess(){

}

class FILTERMAPProcess(){

}
