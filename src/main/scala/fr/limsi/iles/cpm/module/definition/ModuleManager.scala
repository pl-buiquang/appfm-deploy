package fr.limsi.iles.cpm.module.definition

import java.io.{File, FileInputStream}

import com.typesafe.scalalogging.LazyLogging
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
    if(!ConfManager.confMap.containsKey("modules_dir")){
      throw new Exception("no module directories set in configuration!")
    }
    val list : java.util.ArrayList[String] = ConfManager.get("modules_dir").asInstanceOf[java.util.ArrayList[String]]
    val iterator = list.iterator()
    while(iterator.hasNext){
      val path = iterator.next()
      val file = new File(path)
      findModuleConf(file,ModuleManager.initModule)
    }

    var firstRun = true
    var discarded = ""
    while(discarded != "" || firstRun){
      if(discarded!=""){
        modules -= discarded
      }
      discarded = ""
      firstRun = false
      var curmod = ""
      try{
        modules.values.foreach(m => {
          curmod = m.name
          val yaml = new Yaml()
          val wd = (new File(m.confFilePath)).getParent
          val ios = new FileInputStream(m.confFilePath)
          val confMap = yaml.load(ios).asInstanceOf[java.util.Map[String,Any]]
            m.exec = ModuleDef.initRun(confMap,wd,m.inputs)
        })
      }catch{
        case e:Throwable => discarded = curmod; e.printStackTrace(); logger.error("error when initiation exec configuration for module "+curmod+". This module will therefore be discarded");
      }

    }

    logger.info("Finished initializing modules")
  }

  /**
   * Reload module definition
   */
  def reload()={
    modules = Map[String,ModuleDef]()
    init()
  }

  /**
   * Export module defs to json
   * @param onlyname
   * @return
   */
  def jsonExport(onlyname:Boolean):String ={
    var json ="["
    modules.foreach(el=>{
      if(onlyname){
        json += el._1 +","
      }else{
        json += el._2.serialize()(true)+","
      }
    })
    json.substring(0,json.length-1)+"]"
  }

  /**
   * Returns printable string of module defs
   * @param onlyname
   * @return
   */
  def ls(onlyname:Boolean) : String= {
    modules.foldRight("")((el,agg) => {
      {if(onlyname){
         el._2.name
      }else{
        el._2.toString
      }} + "\n" + agg
    })
  }

  /**
   * Apply a function on file that match module extension : .module
   * @param curFile
   * @param f
   */
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

  /**
   * Init module definition conf from definition file
   * @param moduleConfFile
   */
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
      case e: Throwable => e.printStackTrace(); logger.error("Wrong module defintion in "+moduleConfFile.getCanonicalPath+"\n"+e.getMessage+"\n This module will not be registered.")
    }
  }



}
