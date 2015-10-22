package fr.limsi.iles.cpm.module.process

import java.io.FileInputStream
import java.util.UUID
import java.util.function.BiConsumer

import com.mongodb.casbah.commons.MongoDBObject
import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.module.ModuleManager
import fr.limsi.iles.cpm.module.value._
import fr.limsi.iles.cpm.utils.{YamlElt, ConfManager, DB}
import org.yaml.snakeyaml.Yaml

/**
 * Created by buiquang on 10/6/15.
 */
object ProcessRunManager extends LazyLogging{

  // the mongodb process collection
  val processCollection = DB.get("runids")

  var list : Set[UUID] = Set[UUID]()

  /**
   * Get the status of a process id
   * @param uuid
   * @return
   */
  def getStatus(uuid:String)={
    val query = MongoDBObject("ruid"->uuid)
    val tmp = processCollection.findOne(query) match {
      case Some(thing) => thing.toString// retrieve process, retrieve status
      case None => "no process found with that uuid"
    }
    tmp
  }

  /**
   * Create a new run
   * @param modulename
   * @param conffile
   * @return
   */
  def newRun(modulename:String,conffile:String) :String = {
    // first check if module exist in registered modules
    if(!ModuleManager.modules.contains(modulename)){
      return "No module named '"+modulename+"' found!"
    }

    var uuid = UUID.randomUUID()
    while(list.contains(uuid)){
      uuid = UUID.randomUUID()
    }

    val it = processCollection.find()
    while(it.hasNext){
      val el = it.next()
      logger.info(el.get("ruid").toString)

    }

    var args = Map[String,AbstractParameterVal]()
    val yaml = new Yaml()
    val ios = new FileInputStream(conffile)
    val confMap = yaml.load(ios).asInstanceOf[java.util.Map[String,Any]]

    // creation of the result dir
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
    logger.debug("attempting to create result dir in "+resultdirpath)
    val runresultdir = createRunResultDir(resultdirpath,uuid)

    val module = ModuleManager.modules(modulename)

    try{

      val query = MongoDBObject("def"->module.confFilePath)
      processCollection.findOne(query) match {
        case Some(thing) => logger.debug(thing.get("ruid").toString)
        case None => logger.debug("creating new base result dir")
      }

      val obj = MongoDBObject("ruid" -> uuid.toString,"def" -> module.confFilePath)
      processCollection.insert(obj)

      val env = RunEnv.initFromConf(conffile)
      val resultdirval = DIR()
      resultdirval.parseYaml(runresultdir)
      env.args += ("_RUN_DIR" -> resultdirval)
      val defdirval = DIR()
      defdirval.parseYaml(module.wd)
      env.args += ("_DEF_DIR" -> defdirval)
      val process = module.toProcess()
      process.run(env,"",None,false)
      env.args.foldLeft("")((toprint,elt) => {toprint+"\n"+elt._1+" = "+elt._2.asString()})
    }catch{
      case e:Throwable => e.getMessage
    }
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

