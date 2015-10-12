package fr.limsi.iles.cpm.process

import java.io.FileInputStream
import java.util.UUID
import java.util.function.BiConsumer

import com.mongodb.casbah.commons.MongoDBObject
import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.utils.{ConfManager, DB}
import org.yaml.snakeyaml.Yaml

/**
 * Created by buiquang on 10/6/15.
 */
object ProcessRunManager extends LazyLogging{

  val processCollection = DB.get("runids")

  var list : Set[UUID] = Set[UUID]()

  def newRun(modulename:String,conffile:String) :String = {
    var uuid = UUID.randomUUID()
    while(list.contains(uuid)){
      uuid = UUID.randomUUID()
    }

    val it = processCollection.find()
    while(it.hasNext){
      val el = it.next()
      logger.info(el.get("ruid").toString)

    }

    var args = Map[String,ModuleParameterVal]()
    val yaml = new Yaml()
    val ios = new FileInputStream(conffile)
    val confMap = yaml.load(ios).asInstanceOf[java.util.Map[String,Any]]
    /*val resultdirpath = YamlElt.readAs[java.util.HashMap[String,String]](confMap) match {
      case Some(map) => {
        map.get("RESULT_DIR") match {
          case x:String => x
          case _ => throw new Exception("missing RESULT_DIR value")
        }
      }
      case None => {
        throw new Exception("malformed configuration file")
      }
    }

    logger.debug("attempting to create result dir in "+resultdirpath)*/
    val resultdirpath = ConfManager.get("default_result_dir").toString+"/"+modulename
    val runresultdir = createRunResultDir(resultdirpath,uuid)

    try{
      val module = ModuleManager.modules(modulename)

      val query = MongoDBObject("def"->module.confFilePath)
      processCollection.findOne(query) match {
        case Some(thing) => logger.debug(thing.get("ruid").toString)
        case None => logger.debug("creating new base result dir")
      }

      val obj = MongoDBObject("ruid" -> uuid.toString,"def" -> module.confFilePath)
      processCollection.insert(obj)

      val env = RunEnv.initFromConf(conffile)
      val resultdirval = DIR()
      resultdirval.parseFromJavaYaml(runresultdir)
      env.args += ("_RUN_WD" -> resultdirval)
      val defdirval = DIR()
      defdirval.parseFromJavaYaml(module.wd)
      env.args += ("_DEF_WD" -> defdirval)
      val mod = new ModuleVal("",module,AbstractModuleVal.initInputs(module),AbstractModuleVal.initOutputs(module))
      val process = mod.toProcess()
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
