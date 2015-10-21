package fr.limsi.iles.cpm.module.definition

import java.util.function.Consumer

import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.module.ModuleManager
import fr.limsi.iles.cpm.module.parameter._
import fr.limsi.iles.cpm.module.process._
import fr.limsi.iles.cpm.module.value._
import fr.limsi.iles.cpm.utils._

/**
 * Created by buiquang on 9/16/15.
 */
class ModuleDef(
   val confFilePath:String,
   val name:String,
   val desc:String,
   val inputs:Map[String,AbstractModuleParameter],
   val outputs:Map[String,AbstractModuleParameter],
   val log:Map[String,String],
   var exec:List[AbstractModuleVal]
 ){

  def getLastModificationDate(): Long ={
    val file = new java.io.File(confFilePath)
    file.lastModified()
  }

  def getWd():String = {
    val file = new java.io.File(confFilePath)
    file.getParent
  }

  def toProcess(parentProcess:Option[AbstractProcess]) = {
    val modval = new ModuleVal("",this,None)
    modval.toProcess(parentProcess)
  }

  val lastModified = getLastModificationDate()

  val wd = getWd()



}



object ModuleDef extends LazyLogging{
  val nameRegex = """^[a-zA-Z][a-zA-Z0-9\-_]+(@[a-zA-Z0-9\-_]+)?$"""

  def initName(providedName:String,confMap:java.util.Map[String,Any]) = YamlElt.fromJava(confMap.get("name")) match {
    case YamlString(s) => {
      val regex = ModuleDef.nameRegex
      if(regex.r.findAllMatchIn(s).isEmpty){
        throw new Exception("name property ("+s+") must match regex '"+regex+"'!")
      }
      if(s!= providedName){
        throw new Exception("name property ("+s+") must match module file name ("+providedName+") !")
      }
      s
    }
    case _ => throw new Exception("Error when trying to get module name")
  }

  def initDesc(confMap:java.util.Map[String,Any]) = YamlElt.fromJava(confMap.get("desc")) match {
    case YamlString(s) => s
    case _ => {
      logger.warn("Couldn't find module description. A little description is really encouraged.");
      ""
    }
  }

  def initInputs(confMap:java.util.Map[String,Any]) = {
    var moduleinputs = Map[String,AbstractModuleParameter]()
    val parsed = YamlElt.fromJava(confMap.get("input"))
    parsed match {
      case YamlMap(inputs) => {
        val inputnames = inputs.keySet();
        val it = inputnames.iterator()
        while(it.hasNext){
          val name = it.next()
          val parsedinputdef = inputs.get(name)
          moduleinputs += (name -> AbstractModuleParameter.fromYamlConf(name,parsedinputdef,false))
        }
      }
      case _ => throw new Exception("wrong input definition")
    }
    moduleinputs
  }

  def initOutputs(confMap:java.util.Map[String,Any]) = {
    var moduleoutputs = Map[String,AbstractModuleParameter]()
    val parsed = YamlElt.fromJava(confMap.get("output"))
    parsed match {
      case YamlMap(inputs) => {
        val outputnames = inputs.keySet();
        val it = outputnames.iterator()
        while(it.hasNext){
          val name = it.next()
          val parsedoutputdef = inputs.get(name)
          moduleoutputs += (name -> AbstractModuleParameter.fromYamlConf(name,parsedoutputdef,true))
        }
      }
      case _ => throw new Exception("wrong input definition")
    }
    moduleoutputs
  }

  def initLogs(confMap:java.util.Map[String,Any]) = {
    YamlElt.fromJava(confMap.get("log"))
    Map[String,String]()
  }

  def initRun(confMap:java.util.Map[String,Any],wd:String) = {
    var listmodules = Array[String]()
    var run = List[AbstractModuleVal]()
    YamlElt.fromJava(confMap.get("exec"))  match{
      case YamlList(modulevals) => {
        modulevals.forEach(new Consumer[Any] {
          override def accept(t: Any): Unit = {
            run = AbstractModuleVal.fromConf(t) :: run
          }
        })
      }
      case _ => throw new Exception("Module does not provide run information!")
    }
    run.reverse
  }

  def initCMDInputs()={
    var x = Map[String,AbstractModuleParameter]()
    x += ("CMD"->new ModuleParameter[VAL]("VAL",None,None,None))
    val dockerfiledefault = FILE()
    dockerfiledefault.parseYaml(ConfManager.defaultDockerBaseImage)
    x += ("DOCKERFILE"->new ModuleParameter[FILE]("FILE",None,None,None,Some(dockerfiledefault)))
    x
  }

  def initCMDOutputs()={
    var x = Map[String,AbstractModuleParameter]()
    x += ("STDOUT"->new ModuleParameter[VAL]("VAL",None,None,None))
    x
  }

  def initMAPInputs()={
    var x = Map[String,AbstractModuleParameter]()
    x += ("IN"->new ModuleParameter[DIR]("DIR",None,None,None))
    x += ("RUN"->new ModuleParameter[LIST[MODVAL]]("MODULE+",None,None,None))

    val chunk_size = VAL()
    chunk_size.parseYaml("20")
    x += ("CHUNK_SIZE"->new ModuleParameter[VAL]("VAL",Some("Number of files to be processed in parallel"),None,None,Some(chunk_size)))
    x
  }

  def initMAPOutputs()={
    var x = Map[String,AbstractModuleParameter]()
    x += ("OUT"->new ModuleParameter[LIST[DIR]]("DIR+",None,None,None))
    x
  }

  def initFILTERMAPInputs()={
    var x = Map[String,AbstractModuleParameter]()
    x += ("IN"->new ModuleParameter[FILE]("LIST",None,None,None))
    x += ("RUN"->new ModuleParameter[LIST[VAL]]("LIST",None,None,None))
    x
  }

  def initFILTERMAPOutputs()={
    var x = Map[String,AbstractModuleParameter]()
    x += ("OUT"->new ModuleParameter[LIST[VAL]]("LIST",None,None,None))
    x
  }

  val builtinmodules :List[String] = List("_CMD","_MAP","_FILTER","_ANONYMOUS")

}

class AnonymousDef(modulelist:List[AbstractModuleVal]) extends ModuleDef("/no/path","_ANONYMOUS","",Map[String,AbstractModuleParameter](),Map[String,AbstractModuleParameter](),Map[String,String](),modulelist){

}

object CMDDef extends ModuleDef("/no/path","_CMD","Built-in module that run a UNIX commad",ModuleDef.initCMDInputs(),ModuleDef.initCMDOutputs(),Map[String,String](),List[AbstractModuleVal]()){

}

object MAPDef extends ModuleDef("/no/path","_MAP","Built-in module that map a LIST of FILE in Modules that process a single file",ModuleDef.initMAPInputs(),ModuleDef.initMAPOutputs(),Map[String,String](),List[AbstractModuleVal]()) {
}

object FILTERMAPDef extends ModuleDef("/no/path","_FILTERMAP","Built-in module that filter the content of a FILE using a regex and create a new LIST of FILE containing filtered original content which are mapped to Modules that process a single file",ModuleDef.initFILTERMAPInputs(),ModuleDef.initFILTERMAPOutputs(),Map[String,String](),List[AbstractModuleVal]()) {
}


