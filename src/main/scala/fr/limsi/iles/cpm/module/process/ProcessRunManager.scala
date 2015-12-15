package fr.limsi.iles.cpm.module.process

import java.io.FileInputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.function.BiConsumer

import com.mongodb.BasicDBObject
import com.mongodb.casbah.commons.MongoDBObject
import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.module.definition.ModuleManager
import fr.limsi.iles.cpm.module.value._
import fr.limsi.iles.cpm.server.Server
import fr.limsi.iles.cpm.utils.{YamlElt, ConfManager, DB}
import org.yaml.snakeyaml.Yaml
import org.zeromq.ZMQ

/**
 * Created by buiquang on 10/6/15.
 */
object ProcessRunManager extends LazyLogging{

  // the mongodb process collection
  val processCollection = DB.get("process")

  @volatile var list : Map[UUID,AbstractProcess] = Map[UUID,AbstractProcess]()



  def streamStatus(uuid:String) = {

  }


  def getProcess(uuid:UUID):AbstractProcess={
    if(list.contains(uuid)){
      list(uuid)
    }else{
      val query = MongoDBObject("ruid"->uuid.toString)
      val tmp = processCollection.findOne(query) match {
        case Some(thing) => thing.asInstanceOf[BasicDBObject]// retrieve process, retrieve status
        case None => throw new Exception("no such process exist")
      }
      AbstractProcess.fromMongoDBObject(tmp)
    }
  }


  def newRun(modulename:String,confdata:String,async:Boolean) :String = {
    /*
    val it = processCollection.find()
    while(it.hasNext){
      val el = it.next()
      logger.info(el.get("ruid").toString)

    }*/
    if(!ModuleManager.modules.contains(modulename)){
      return "no module named "+modulename+" found!"
    }
    // fetching module definition
    val module = ModuleManager.modules(modulename)

    // fetching configuration file for current run
    var args = Map[String,AbstractParameterVal]()
    val yaml = new Yaml()

    val confMap = yaml.load(confdata).asInstanceOf[java.util.Map[String,Any]]
    val resultdirpath = YamlElt.readAs[java.util.HashMap[String,String]](confMap) match {
      case Some(map) => {
        map.get("RESULT_DIR") match {
          case x:String => x
          case _ => ConfManager.get("default_result_dir").toString+"/"+modulename
        }
      }
      case None => {
        throw new Exception("malformed configuration file")
      }
    }


    // check if similar run exist (=> same modulename + same configuration settings), ask to overwrite/continue(if exist and paused)/create new result folder
    /*
    val query = MongoDBObject("def"->module.confFilePath)
    processCollection.findOne(query) match {
      case Some(thing) => logger.debug(thing.get("ruid").toString)
      case None => logger.debug("creating new base result dir")
    }*/

    // create process object
    val process = module.toProcess(None)
    val uuid = process.id

    // creating base run result dir
    val runresultdir = createRunResultDir(resultdirpath,uuid)

    // setting run environment from conf and default variables
    val env = RunEnv.initFromConf(confMap)
    val resultdirval = DIR(None,None)
    resultdirval.fromYaml(runresultdir)
    env.setVar("_RUN_DIR" , resultdirval)
    val defdirval = DIR(None,None)
    defdirval.fromYaml(module.defdir)
    env.setVar("_DEF_DIR" , defdirval)





    // finally launch the process and return the id of it
    //val runid = process.run(env,"",None,async)
    process.saveStateToDB()
    val mps = new MasterProcessShell(process,async,"",env)
    mps.run()

    uuid.toString

    //env.args.foldLeft("")((toprint,elt) => {toprint+"\n"+elt._1+" = "+elt._2.asString()})

  }


  def createRunResultDir(resultdirpath:String,uuid:UUID) = {
    val resultdir = new java.io.File(resultdirpath)
    if(!resultdir.exists()){
      logger.debug("result dir does not exist, atempting to create it")
      if(!resultdir.mkdirs()){
        throw new Exception("cannot create result dir")
      }
    }else if(!resultdir.isDirectory){
      throw new Exception("result dir isn't a directory")
    }else if(!resultdir.canWrite){
      throw new Exception("cannot write in the result dir")
    }
    val runresultdirpath = resultdir.getCanonicalPath+"/run-"+uuid
    val runresultdir = new java.io.File(runresultdirpath)
    if(!runresultdir.mkdir()){
      throw new Exception("cannot create run result dir")
    }
    logger.debug("created result dir")
    runresultdirpath
  }

}

class MasterProcessShell(process:AbstractProcess,detached:Boolean,ns:String,env:RunEnv){
  def run() = {

    if(detached){
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



  }

  def runSupervisor()={
    val socket = Server.context.socket(ZMQ.PULL)
    var connected = 10
    var processPort = AbstractProcess.newPort()
    while(connected!=0)
      try {
        socket.bind("tcp://*:" + processPort)
        connected = 0
      }catch {
        case e:Throwable => {
          processPort = AbstractProcess.newPort()
          connected -= 1
        }
      }

    process.run(env,ns,Some(processPort),detached)
    var finished = false
    var error = "0"

    while (!finished) {

      val rawmessage = socket.recvStr()
      val message: ProcessMessage = rawmessage

      message match {
        case ValidProcessMessage(sender,status,exitval) => status match {
          case "FINISHED" => {
            finished = true


          }
          case s : String => finished = true
        }
        case _ => finished = true
      }

    }

    process.saveStateToDB()


    socket.close();
  }
}


trait ProcessDBObject {
  def getID():String
  def getModuleName():String
  def getSubprocess():List[String]
  def getCom():Int // communication port

}

