package fr.limsi.iles.cpm.module.process

import java.io.FileInputStream
import java.util.UUID
import java.util.function.BiConsumer

import com.mongodb.BasicDBObject
import com.mongodb.casbah.commons.MongoDBObject
import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.module.definition.ModuleManager
import fr.limsi.iles.cpm.module.value._
import fr.limsi.iles.cpm.utils.{YamlElt, ConfManager, DB}
import org.yaml.snakeyaml.Yaml

/**
 * Created by buiquang on 10/6/15.
 */
object ProcessRunManager extends LazyLogging{

  // the mongodb process collection
  val processCollection = DB.get("process")

  var list : Map[UUID,AbstractProcess] = Map[UUID,AbstractProcess]()



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


  def newRun(modulename:String,conffile:String,async:Boolean) :String = {
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
    val ios = new FileInputStream(conffile)
    val confMap = yaml.load(ios).asInstanceOf[java.util.Map[String,Any]]
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
    val env = RunEnv.initFromConf(conffile)
    val resultdirval = DIR(None,None)
    resultdirval.fromYaml(runresultdir)
    env.args += ("_RUN_DIR" -> resultdirval)
    val defdirval = DIR(None,None)
    defdirval.fromYaml(module.defdir)
    env.args += ("_DEF_DIR" -> defdirval)





    // finally launch the process and return the id of it
    process.run(env,"",None,async).toString
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


trait ProcessDBObject {
  def getID():String
  def getModuleName():String
  def getSubprocess():List[String]
  def getCom():Int // communication port

}

