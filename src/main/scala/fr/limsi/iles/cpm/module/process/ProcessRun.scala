package fr.limsi.iles.cpm.module.process

import java.io
import java.io.FilenameFilter
import java.util.UUID
import java.util.concurrent.Executors

import com.mongodb.BasicDBObject
import com.mongodb.casbah.commons.MongoDBObject
import com.mongodb.casbah.Imports._
import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.module.definition.{ModuleManager, AnonymousDef, ModuleDef}
import fr.limsi.iles.cpm.module.parameter.AbstractModuleParameter
import fr.limsi.iles.cpm.module.value.{DIR, VAL, AbstractParameterVal}
import fr.limsi.iles.cpm.module.value._
import fr.limsi.iles.cpm.server.Server
import fr.limsi.iles.cpm.utils.{YamlElt, DB, Utils, ConfManager}
import org.json.JSONObject
import org.yaml.snakeyaml.Yaml
import org.zeromq.ZMQ

import scala.io.Source
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


object AbstractProcess extends LazyLogging{
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
    val uuid = UUID.fromString(obj.get("ruid").toString)
    val parentProcess : Option[AbstractProcess] = obj.get("parentProcess") match {
      case "None" => None
      case pid:String => Some(ProcessRunManager.getProcess(UUID.fromString(pid)))
      case _ => None
    }
    val env = RunEnv.deserialize(obj.getOrDefault("env","").toString)
    val namespace = obj.getOrDefault("modvalnamespace","").toString
    val modulename = obj.get("name").toString
    val modulevalconf = obj.get("modvalconf") match{
      case "" => None
      case x:String => Some((new Yaml).load(x).asInstanceOf[java.util.Map[String,Any]])
      case _ => logger.warn("missing modval conf in serialized obj"); None
    }
    val parentPort = obj.getOrDefault("parentport","NONE") match {
      case "" => None
      case "NONE" => None
      case x:String => Some(x)
    }
    val x =  modulename match {
      case "_CMD" => new CMDProcess(new CMDVal(namespace,modulevalconf),parentProcess,uuid)
      case "_MAP" => new MAPProcess(new MAPVal(namespace,modulevalconf),parentProcess,uuid)
      //case "_ANONYMOUS" => new AnonymousModuleProcess(new ModuleVal(namespace,new AnonymousDef(),modulevalconf),parentProcess,uuid)
      case _ => new ModuleProcess(new ModuleVal(namespace,ModuleManager.modules(modulename),modulevalconf),parentProcess,uuid)
    }

    x.env = env
    x
  }


}

/**
 * Created by buiquang on 9/30/15.
 */
abstract class AbstractProcess(val parentProcess:Option[AbstractProcess],val id :UUID) extends LazyLogging{

  // TODO use these in initRunEnv to replace missing values (or force override values) and in step() method to skip proper modules
  var skipped = List[String]() // moduleval namespace to prevent from running and fetch previous result
  var replacements = Map[String,UUID]() // map (moduleval namespace -> run) result replacement

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

  def getOutput(outputName:String) = {
    env.getRawVar(outputName) match {
      case Some(thing) => thing
      case None => ""
    }
  }

  protected[this] def postInit():Unit={}
  protected[this] def step():Unit
  protected[this] def update(message:ProcessMessage)
  protected[this] def endCondition() : Boolean
  protected[this] def updateParentEnv():Unit
  protected[this] def attrserialize():(Map[String,String],Map[String,String])
  protected[this] def attrdeserialize(mixedattrs:Map[String,String]):Unit


  private def serializeToMongoObject(update:Boolean) : MongoDBObject = {
    var staticfields = List(
      "ruid" -> id.toString,
      "def" -> moduleval.moduledef.confFilePath,
      "name" -> moduleval.moduledef.name,
      "master" -> {parentProcess match {
        case Some(thing) => false
        case None => true
      }},
      "parentProcess" -> {
        if(parentProcess.isEmpty){
          "None"
        }else{
          parentProcess.get.id.toString
        }
      },
      "modvalconf" -> (new Yaml).dump(moduleval.conf.getOrElse("")),
      "modvalnamespace" -> moduleval.namespace,
      "resultnamespace" -> resultnamespace
    )
    var changingfields = List(
      "parentport" -> parentPort.getOrElse("NONE"),
      "processport" -> processPort,
      "status" -> status.toString(),
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
      },
      "parentEnv" -> {parentProcess match {
        case Some(thing) => ""
        case None => parentEnv match {
          case x:RunEnv => x.serialize()
          case _ => ""
        }
      }}
    )
    var customattrs = this.attrserialize()
    staticfields :::= customattrs._1.toList
    changingfields :::= customattrs._2.toList

    val obj = if(update) {
      $set(changingfields(0),changingfields(1),changingfields(2),changingfields(3),changingfields(4))
    }else{
      MongoDBObject(staticfields++changingfields)
    }
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
      ProcessRunManager.processCollection.update(query,this.serializeToMongoObject(true))
    }else{
      ProcessRunManager.processCollection.insert(this.serializeToMongoObject(false))
    }

    // TODO check if everything went fine
    true
  }


  def run(parentEnv:RunEnv,ns:String,parentPort:Option[String],detached:Boolean):UUID = {
    this.parentPort = parentPort
    status match {
      case Running() => throw new Exception("Process already running")
      case Waiting() => status = Running()
      case Exited(errorcode) => throw new Exception("Process already run and exited with status code "+errorcode)
    }
    logger.info("Executing "+moduleval.moduledef.name)

    // save process to db
    //this.saveStateToDB()

    resultnamespace = ns
    // init runenv from parent env
    try{
      initRunEnv(parentEnv)
      postInit()
    }catch{
      case e:Throwable => logger.error(e.getMessage); exitRoutine("error when initiation execution environment : "+e.getMessage); return id

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
      executorService.shutdown();
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

    var error = "0"
    try{
      while (!endCondition()) {

        step() // step run

        val rawmessage = socket.recvStr()
        val message: ProcessMessage = rawmessage

        update(message) // update after step

      }



    }catch{
      case e:Throwable => error = "error when running : "+e.getMessage; logger.error(e.getMessage)
    }finally {
      socket.close();
      exitRoutine(error)
    }
  }

  protected def initRunEnv(parentRunEnv:RunEnv) = {
    logger.debug("Initializing environement for "+moduleval.moduledef.name)
    logger.debug("Parent env contains : ")
    parentRunEnv.debugPrint()

    // set parent env reference
    parentEnv = parentRunEnv

    // new env vars container
    var newargs = Map[String,AbstractParameterVal]()

    // create new subdirectory run dir and set path to env vars
    val runresultdir = DIR(None,None)
    runresultdir.fromYaml(parentRunEnv.getRawVar("_RUN_DIR").get.asString()+"/"+moduleval.namespace)
    val newdir = new java.io.File(runresultdir.asString())
    newdir.mkdirs()
    newargs += ("_RUN_DIR" -> runresultdir)

    // set module defintion directory info
    // builtin modules haven't any real definition directory, use parent's
    val defdir = if(ModuleDef.builtinmodules.contains(moduleval.moduledef.name)){
      parentRunEnv.getRawVar("_DEF_DIR").get
    }else{
      val x = DIR(None,None)
      x.fromYaml(moduleval.moduledef.defdir)
      x
    }
    newargs += ("_DEF_DIR" -> defdir)

    // set information about current running module name and caller context module name
    // for built in module, use name of first parent custom module name
    val (mod_context,cur_mod) = if(ModuleDef.builtinmodules.contains(moduleval.moduledef.name)){
      val x = VAL(None,None)
      x.fromYaml("_MAIN")
      (parentRunEnv.getRawVar("_CUR_MOD").getOrElse(x),parentRunEnv.getRawVar("_CUR_MOD").getOrElse(x))
    }else{
      val x = VAL(None,None)
      x.fromYaml(moduleval.moduledef.name)
      (parentRunEnv.getRawVar("_CUR_MOD").getOrElse(x),x)
    }
    newargs += ("_MOD_CONTEXT" -> mod_context)
    newargs += ("_CUR_MOD" -> cur_mod)

    // get usefull (look into module input needs) vars from parent env and copy them into the new env
    val donotoverride = List("_MOD_CONTEXT","_CUR_MOD","_DEF_DIR","_RUN_DIR")
    // for anonymous module, copy every parent env vars except previously set
    if(moduleval.moduledef.name == "_ANONYMOUS"){
      parentEnv.getVars().filter(arg => {
        !donotoverride.contains(arg._1)
      }).foreach(arg => {
        newargs += (arg._1 -> arg._2)
      })

    }else{
      moduleval.inputs.foreach(input=>{
        logger.info("Looking in parent env for "+input._1+" of type "+input._2.getClass.toGenericString+" with value to resolve : "+input._2.asString())
        val variables = input._2.extractVariables()
        var ready = true
        variables.filter(arg => {
          !donotoverride.contains(arg)
        }).foreach(variable => {
          if(parentRunEnv.getRawVar(variable).isEmpty){
            ready = false
          }else{
            val value = if(moduleval.moduledef.inputs.contains(variable)){
              moduleval.moduledef.inputs(variable).createVal()
            }else{
              parentEnv.getRawVar(variable).get.newEmpty()
            }
            value.fromYaml(parentRunEnv.getRawVar(variable).get.asString())
            newargs += (variable -> value)
            //newargs += (variable -> parentRunEnv.getRawVar(variable).get)
          }
        })
        if(ready){
          logger.info("Found")
          val newval = input._2.newEmpty()
          if(moduleval.moduledef.name == "_CMD"){
            newval.fromYaml(parentEnv.resolveValueToString(input._2.toYaml()))
          }else{
            newval.fromYaml(parentEnv.resolveValueToYaml(input._2.toYaml()))
          }
          newargs += (input._1 -> newval)


        }
      });


    }

    //TODO allow previous run result to fill missing inputs if run type allow it

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

    newargs.foreach(el => {
      if(el._2._mytype=="FILE" || el._2._mytype=="DIR"){
        if(!(new java.io.File(el._2.asString())).exists()){
          throw new Exception(el._2.asString() + " does not exist! Aborting run")
        }
      }
    })

    val newenv = new RunEnv(newargs)
    env = newenv
    logger.debug("Child env contains : ")
    env.debugPrint()
  }

  private[this] def exitRoutine(): Unit = exitRoutine("0")

  private[this] def exitRoutine(error:String): Unit = {
    logger.info("Finished processing for module "+moduleval.moduledef.name+", connecting to parent socket")
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


    status = Exited(error)
    ProcessRunManager.list -= id
    //saveStateToDB()


    socket match {
      case Some(sock) => {
        logger.debug("Sending completion signal")
        sock.send(new ValidProcessMessage(moduleval.namespace,"FINISHED",error))
        sock.close()
      }
      case None => {
        logger.info("Finished executing "+moduleval.moduledef.name)
      }
    }

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
          if(exitval!="0"){
            throw new Exception(sender+" failed with exit value "+exitval)
          }
          completedModules += (sender -> runningModules(sender))
        }
        case s : String => logger.warn("WTF? : "+s)
      }
      case _ => logger.warn("unrecognized message")
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
      //this.saveStateToDB()
      process.run(env,moduleval.namespace,Some(processPort),true) // not top level modules (called by cpm cli) always run demonized
    })
  }

  override def updateParentEnv() = {
    logger.debug("Process env contains : ")
    env.debugPrint()
    moduleval.moduledef.outputs.foreach(output=>{
      logger.debug("Looking to resolve : "+output._2.value.get.asString())
      val x = output._2.createVal()
      logger.debug("Found :"+env.resolveValueToYaml(output._2.value.get.asString()))
      x.fromYaml(env.resolveValueToYaml(output._2.value.get.toYaml()))
      val namespace = moduleval.namespace match {
        case "" => ""
        case _ => moduleval.namespace+"."
      }
      parentEnv.setVar(namespace+output._1,x)
    });
    logger.debug("New parent env contains : ")
    parentEnv.debugPrint()
  }

  override protected[this] def attrserialize(): (Map[String, String], Map[String, String]) = {
    (Map[String, String](),Map[String, String]())
  }

  override protected[this] def attrdeserialize(mixedattrs: Map[String, String]): Unit = {
  }
}

class AnonymousModuleProcess(override val moduleval:ModuleVal,override val parentProcess:Option[AbstractProcess],override val id:UUID)  extends ModuleProcess(moduleval,parentProcess,id){
  def this(moduleval:ModuleVal,parentProcess:Option[AbstractProcess]) = this(moduleval,parentProcess,UUID.randomUUID())

  override def updateParentEnv() = {
    logger.debug("Process env contains : ")
    env.debugPrint()
    moduleval.inputs.foreach(elt => {
      logger.debug(elt._1+" with value "+elt._2.asString())
    })
    parentEnv.setVars(env.getVars().filter(elt => {

      moduleval.moduledef.exec.foldLeft(false)((agg,modval) => {
        agg || elt._1.startsWith(modval.namespace)
      })

    }).foldLeft(Map[String,AbstractParameterVal]())((map,elt)=>{map + (moduleval.namespace+"."+elt._1->elt._2)}))
    logger.debug("New parent env contains : ")
    parentEnv.debugPrint()
  }
}


class CMDProcess(override val moduleval:CMDVal,override val parentProcess:Option[AbstractProcess],override val id:UUID) extends AbstractProcess(parentProcess,id){
  def this(moduleval:CMDVal,parentProcess:Option[AbstractProcess]) = this(moduleval,parentProcess,UUID.randomUUID())
  var stdoutval : VAL = VAL(None,None)
  var stderrval : VAL = VAL(None,None)
  var run = ""

  override def step(): Unit = {
    logger.debug("Launching CMD "+env.resolveValueToString(moduleval.inputs("CMD").asString()))
    var stderr = ""
    var stdout = ""
    val wd = env.getRawVar("_DEF_DIR").get.asString()
    val folder = new java.io.File(wd)



    val dockerimagename = {
      env.resolveValueToString(moduleval.inputs("DOCKERFILE").toYaml()) match {
        case x :String => {
          if(x=="true"){
            val name = env.getRawVar("_MOD_CONTEXT").get.asString()+"-"+moduleval.namespace // _MOD_CONTEXT should always be the module defintion that holds this command
            if(!DockerManager.exist(name)){
              DockerManager.build(name,wd+"/Dockerfile")
            }
            name
          }else{
            ""
          }
        }
        case _ =>  ""
      }
    }

    val containername = if(env.resolveValueToString(moduleval.inputs("CONTAINED").toYaml()) == "true") {
      UUID.randomUUID().toString // to ensure a unique docker container is created for this process that should be containerized from other instances of the same process
    } else {
      dockerimagename
    }



    if(dockerimagename!=""){
      DockerManager.serviceRun(containername,dockerimagename,folder)
      run = DockerManager.serviceExec(this.id,moduleval.namespace,"localhost",processPort,env.resolveValueToString(moduleval.inputs("CMD").asString()),folder,containername)
    }else{
      val cmd = env.resolveValueToString(moduleval.inputs("CMD").asString())
      val absolutecmd = cmd.replace("\n"," ").replace("\"","\\\"").replaceAll("^./",folder.getCanonicalPath+"/")
      val cmdtolaunch = "python "+ConfManager.get("cpm_home_dir")+"/"+ConfManager.processshell+" "+this.id.toString+" "+moduleval.namespace+" "+processPort+" "+absolutecmd+""
      logger.info("Launchin cmd : "+cmdtolaunch)
      val exitval = Process(cmdtolaunch,new java.io.File(wd)) ! ProcessLogger(line => stdout+="\n"+line,line=>stderr+="\n"+line)
      stdoutval.rawValue = stdout
      stderrval.rawValue = stderr
      if(exitval!=0){
        throw new Exception(stderr)
      }
    }

    // tag to prevent running more than once the process
    run = "true"

  }

  override protected[this] def updateParentEnv(): Unit = {
    //stdoutval.rawValue = Source.fromFile("/tmp/out"+this.id.toString).getLines.mkString
    stdoutval.resolvedValue = stdoutval.rawValue

    //stderrval.rawValue = Source.fromFile("/tmp/err"+this.id.toString).getLines.mkString
    stderrval.resolvedValue = stderrval.rawValue


    parentEnv.logs += (moduleval.namespace -> stderrval)
    parentEnv.setVar(moduleval.namespace+".STDOUT", stdoutval)


  }

  override protected[this] def update(message: ProcessMessage): Unit = {
    message match {
      case ValidProcessMessage(sender,status,exitval) => status match {
        case "FINISHED" => {
          logger.debug(sender + " just finished")
          // TODO message should contain new env data?
          // anyway update env here could be good since there is no need to lock...

          if(exitval!="0"){
            throw new Exception(sender+" failed with exit value "+exitval)
          }
        }
        case s : String => logger.warn("WTF? : "+s)
      }
      case _ => logger.warn("unknown message type")
    }
  }

  override protected[this] def endCondition(): Boolean = {
    run != ""
  }

  override protected[this] def attrserialize(): (Map[String, String], Map[String, String]) = {
    (Map[String,String](),Map[String,String]("cmdprocrun"->run))
  }

  override protected[this] def attrdeserialize(mixedattrs: Map[String, String]): Unit = {
    run = mixedattrs("cmdprocrun")
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
    //values += ("tmpenv"->env.copy())
    val filterregex = moduleval.getInput("REGEX",env).asString();
    values += ("filter"->new FilenameFilter {
      override def accept(dir: io.File, name: JSFunction): Boolean = {
        filterregex.r.findFirstIn(name) match {
          case None => false
          case Some(x:String) => true
        }
      }
    })
  }



  override protected[this] def updateParentEnv(): Unit = {
    logger.debug("Process env contains : ")
    env.debugPrint()

    val namespace = resultnamespace match {
      case "" => ""
      case _ => resultnamespace+"."
    }

    val prefix = namespace+"_MAP."
    val prefixlength = prefix.length
    parentEnv.setVars(env.getVars().filter(elt => {
      elt._1.startsWith(prefix)
    }).groupBy[String](el=>{
      val modnamestartindex = el._1.substring(prefixlength).indexOf(".")
      val modnameendindex = el._1.substring(prefixlength+modnamestartindex+1).indexOf(".")
      prefix+el._1.substring(5+modnamestartindex+1)//.substring(0,modnameendindex)
    }).transform((key,content) => {
      // TODO know whichever the fuck is the type and create the proper list type
      /**
       * +"*"
       */
      val newel = AbstractModuleParameter.createVal(content.head._2._mytype+"*",content.head._2.format,content.head._2.schema).asInstanceOf[LIST[AbstractParameterVal]]
      content.foldLeft(newel)((agg,elt) => {
        agg.list ::= elt._2
        agg
      })
    }))

    logger.debug("New parent env contains : ")
    parentEnv.debugPrint()
  }

  override protected [this] def update(message:ProcessMessage)={
    values += ("chunksize" -> 1)
    val n : Int= values("completed").asInstanceOf[Int]
    values += ("completed" -> (n+1))
  }

  override protected[this] def endCondition():Boolean = {
    offset>=values("dir").asInstanceOf[java.io.File].listFiles(values("filter").asInstanceOf[FilenameFilter]).length &&
      values("completed").asInstanceOf[Int] == (values("process").asInstanceOf[List[AbstractProcess]]).length
  }

  def getResult(varname:String) = {
    val module = new AnonymousDef(values("modules").asInstanceOf[List[AbstractModuleVal]],context,parentInputsDef)

    val moduleval = new ModuleVal(resultnamespace+"_MAP."+(offset).toString,module,Some(Utils.scalaMap2JavaMap(env.getVars().mapValues(paramval => {
      paramval.toYaml()
    }))))

    module.exec.find(m=>{
      m.namespace == varname
    }).get.moduledef.outputs.find(_ == varname).get._2.value
  }


  override protected[this] def step()={
    val to = if(offset+values("chunksize").asInstanceOf[Int]>=values("dir").asInstanceOf[java.io.File].listFiles(values("filter").asInstanceOf[FilenameFilter]).length){
      values("dir").asInstanceOf[java.io.File].listFiles(values("filter").asInstanceOf[FilenameFilter]).length
    }else{
      offset + values("chunksize").asInstanceOf[Int]
    }

    val toProcessFiles = values("dir").asInstanceOf[java.io.File].listFiles(values("filter").asInstanceOf[FilenameFilter]).slice(offset,to)

    var i = 0;
    //val newenv = values("tmpenv").asInstanceOf[RunEnv]
    toProcessFiles.foreach(file => {
      val newenv = env
      val dirinfo = this.moduleval.getInput("IN",env)
      val x = FILE(dirinfo.format,dirinfo.schema)
      x.fromYaml(file.getCanonicalPath)
      newenv.setVar("_", x)

      val module = new AnonymousDef(values("modules").asInstanceOf[List[AbstractModuleVal]],context,parentInputsDef)

      val moduleval = new ModuleVal(resultnamespace+"_MAP."+(offset+i).toString,module,Some(Utils.scalaMap2JavaMap(newenv.getVars().mapValues(paramval => {
        paramval.toYaml()
      }))))
      i+=1
      val process = new AnonymousModuleProcess(moduleval,Some(this))
      childrenProcess ::= process.id
      //this.saveStateToDB()
      var list = values("process").asInstanceOf[List[AbstractProcess]]
      list ::= process
      values += ("process" -> list)
      process.run(newenv,moduleval.namespace,Some(processPort),true)
    })

    offset = to
  }

  override protected[this] def attrserialize(): (Map[String, String], Map[String, String]) = {
    (Map[String,String](),Map[String,String]())
  }

  override protected[this] def attrdeserialize(mixedattrs: Map[String, String]): Unit = {

  }

  def swapEnv(): Unit ={
    val swapcol = DB.get("envswap")
    val query = MongoDBObject("ruid"->this.id.toString)



  }

}



class FILTERProcess(){

}


