package fr.limsi.iles.cpm.process

import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.utils.YamlElt._
import fr.limsi.iles.cpm.utils._
import java.io.{FileInputStream, File}


import org.yaml.snakeyaml.Yaml

/**
 * Created by buiquang on 9/7/15.
 */
object ModuleManager extends LazyLogging{

  var modules = Map[String,ModuleDef]()

  /**
   * Check every modules found in the listed directory supposely containing modules definition/implementation/resources
   * Check for consistency
   */
  def init()={
    // list all modules and init independant fields
    val list : java.util.ArrayList[String] = ConfManager.get("modules_dir").asInstanceOf[java.util.ArrayList[String]]
    val iterator = list.iterator()
    while(iterator.hasNext){
      val path = iterator.next()
      val file = new File(path)
      findModuleConf(file,ModuleManager.initModule)
    }

    modules.values.foreach(m => {
      val yaml = new Yaml()
      val ios = new FileInputStream(m.confFilePath)
      val confMap = yaml.load(ios).asInstanceOf[java.util.Map[String,Any]]
      m.run = ModuleDef.initRun(confMap)
    })

    modules.values.foreach(m => {
      println(m.name)
      m.run.foreach(println _)

    })
  }

  private def findModuleConf(curFile:java.io.File,f:java.io.File => Unit) :Unit={
    if(curFile.isFile){
      if(curFile.getName().endsWith(".module")){
        f(curFile)
      }
    }else if(curFile.isDirectory){
      val iterator = curFile.listFiles().iterator
      while(iterator.hasNext){
        val file = iterator.next()
        findModuleConf(file,f)
      }
    }else{
      logger.warn("File at path %s is neither file nor directory!",curFile.getPath)
    }
  }

  private def initModule(moduleConfFile:File):Unit={
    try{
      val modulename = moduleConfFile.getName.substring(0,moduleConfFile.getName.lastIndexOf('.'))
      logger.debug("Initiating module "+modulename)

      val yaml = new Yaml()
      val ios = new FileInputStream(moduleConfFile)
      val confMap = yaml.load(ios).asInstanceOf[java.util.Map[String,Any]]
      val module = new ModuleDef(moduleConfFile.getCanonicalPath,
        ModuleDef.initName(modulename,confMap),
        ModuleDef.initDesc(confMap),
        ModuleDef.initInputs(confMap),
        ModuleDef.initOutputs(confMap),
        ModuleDef.initLogs(confMap),
        Nil
      );
      // check if module name already exist
      modules.get(modulename) match {
        case Some(m:ModuleDef) => throw new Exception("Module already exist, defined in "+moduleConfFile.getParent)
        case None => modules += (modulename -> module)
      }
    }catch{
      case e: Throwable => logger.error(e.getMessage)
    }
  }

  def listModules():Unit = {
    modules.foreach(println _)
  }

}
