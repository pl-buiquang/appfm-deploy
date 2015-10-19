package fr.limsi.iles.cpm.module

import java.io.{File, FileInputStream}

import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.module.definition.ModuleDef
import fr.limsi.iles.cpm.utils._
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
      val wd = (new File(m.confFilePath)).getParent
      val ios = new FileInputStream(m.confFilePath)
      val confMap = yaml.load(ios).asInstanceOf[java.util.Map[String,Any]]
      m.exec = ModuleDef.initRun(confMap,wd)
    })

    modules.values.foreach(m => {
      println(m.name)
      m.exec.foreach(println _)

    })
  }

  def ls() : String= {
    var output = ""
    modules.foreach(el => {
      output += "Name : "+el._1 + "\nDesc : " + el._2.desc+"\nLast Modified : "+Utils.getHumanReadableDate(el._2.lastModified)+"\n\n"
    })
    output
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
      logger.warn("File at path "+curFile.getPath+" is neither file nor directory!")
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
      case e: Throwable => logger.error("Wrong module defintion in "+moduleConfFile.getCanonicalPath+"\n"+e.getMessage+"\n This module will not be registered.")
    }
  }



}
