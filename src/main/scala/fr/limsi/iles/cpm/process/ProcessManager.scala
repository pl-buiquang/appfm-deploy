package fr.limsi.iles.cpm.process

import java.io.FileInputStream
import java.util.UUID
import java.util.function.BiConsumer

import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.utils.YamlElt
import org.yaml.snakeyaml.Yaml

/**
 * Created by buiquang on 10/6/15.
 */
object ProcessManager extends LazyLogging{

  var list : Set[UUID] = Set[UUID]()

  def newRun(modulename:String,conffile:String) :String = {
    var uuid = UUID.randomUUID()
    while(list.contains(uuid)){
      uuid = UUID.randomUUID()
    }

    var args = Map[String,ModuleParameterVal]()
    val yaml = new Yaml()
    val ios = new FileInputStream(conffile)
    val confMap = yaml.load(ios).asInstanceOf[java.util.Map[String,Any]]
    val resultdirpath = YamlElt.readAs[java.util.HashMap[String,String]](confMap) match {
      case Some(map) => {
        map.get("RESULT_DIR")
      }
      case None => {
        throw new Exception("malformed configuration file")
      }
    }

    val runresultdir = createRunResultDir(resultdirpath,uuid)

    try{
      val module = ModuleManager.modules(modulename)
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
      process.env.args.aggregate("")((toprint,elt) => {elt._1+" = "+elt._2.asString()},_+"\n"+_)
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
