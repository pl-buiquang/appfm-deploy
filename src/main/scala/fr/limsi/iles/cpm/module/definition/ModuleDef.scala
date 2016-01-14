package fr.limsi.iles.cpm.module.definition


import java.io.FileInputStream
import java.util.function.Consumer

import com.mongodb.DBObject
import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.module.definition.ModuleManager._
import fr.limsi.iles.cpm.module.parameter._
import fr.limsi.iles.cpm.module.process._
import fr.limsi.iles.cpm.module.value._
import fr.limsi.iles.cpm.utils._
import org.yaml.snakeyaml.Yaml
import org.json._

/**
 * Created by buiquang on 9/16/15.
 * TODO refactor this package : clearer instanciation of inputs/outputs/exec for default module def and special modules
 */

/**
 * Module definition.
 * <p> Reflects the configuration file for a module in yaml format
 *
 * @param confFilePath the filepath of the module configuration path
 * @param name the unique name of the module [a-zA-Z][a-zA-Z0-9\-_]+(@[a-zA-Z0-9\-_]+)?
 * @param desc the description of the module
 * @param inputs the inputs of the module
 * @param outputs the expected produced outputs of the module
 * @param log
 * @param exec the execution process of the module, consisting of a sequence of instanciated module
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

  /**
   * Returns the last modification date of the configuration file for the module
   * @return
   */
  def getLastModificationDate(): Long ={
    val file = new java.io.File(confFilePath)
    file.lastModified()
  }

  /**
   * Returns the defintion directory of this module
   * @return
   */
  def getDefDir():String = {
    val file = new java.io.File(confFilePath)
    file.getParent
  }

  /**
   * Create a process object from this module definition
   * @param parentProcess an optional parent process of the newly created process
   * @return
   */
  def toProcess(parentProcess:Option[AbstractProcess]) = {
    val modval = new ModuleVal("",this,None)
    modval.toProcess(parentProcess)
  }

  /**
   * The last modification date of this module definition
   */
  val lastModified = getLastModificationDate()

  /**
   * The definition directory of this module
   */
  val defdir = getDefDir()

  /**
   * Returns a pretty string representation of this module (for human reading)
   * @return
   */
  override def toString:String={
    "Name : "+name + "\nDesc : " + desc+"\nLast Modified : "+Utils.getHumanReadableDate(lastModified)+"\n"+
      inputs.foldLeft("Inputs : ")((agg,input)=>{
        agg + "\n\t" + input._1 + ": " + input._2.toString()
      }) + "\n" + outputs.foldLeft("Outputs : ")((agg,output)=>{
      agg + "\n\t" + output._1 + ": " + output._2.toString()
    }) + "\n\n"
  }

  def serialize()(implicit tojson:Boolean=false) : String= {
    val yamloffset = "  "
    val yamlstring = "name : "+name + "\n" +
      (if(desc!="")"desc : >\n"+yamloffset+desc + "\n" else "")+
    inputs.foldLeft("input : ")((agg,input)=>{
      agg + "\n"+yamloffset+input._1+" : "+"\n"+
        (input._2.desc match {case Some(thing)=>yamloffset+yamloffset+"desc : "+thing+"\n"; case None => ""})+
        yamloffset+yamloffset+"type : "+input._2.paramType+"\n"+
        (input._2.format match {case Some(thing)=>yamloffset+yamloffset+"format : "+thing+"\n";case None=>""})+
        (input._2.schema match {case Some(thing)=>yamloffset+yamloffset+"schema : "+thing+"\n";case None=>""})+
        (input._2.value match {case Some(thing)=>yamloffset+yamloffset+"value : "+Utils.addOffset(yamloffset+yamloffset,thing.toYaml())+"\n";case None=>""})
    }) + "\n" +
    outputs.foldLeft("output : ")((agg,output)=>{
      agg + "\n"+yamloffset+output._1+" : "+"\n"+
        (output._2.desc match {case Some(thing)=>yamloffset+yamloffset+"desc : "+thing+"\n"; case None => ""})+
        yamloffset+yamloffset+"type : "+output._2.paramType+"\n"+
        (output._2.format match {case Some(thing)=>yamloffset+yamloffset+"format : "+thing+"\n";case None=>""})+
        (output._2.schema match {case Some(thing)=>yamloffset+yamloffset+"schema : "+thing+"\n";case None=>""})+
        (output._2.value match {case Some(thing)=>yamloffset+yamloffset+"value : "+Utils.addOffset(yamloffset+yamloffset,thing.toYaml())+"\n";case None=>""})
    }) + "\n" +
    exec.foldLeft("exec : ")((agg,modval)=>{
      agg + "\n"+yamloffset+"- "+modval.namespace+" : \n"+yamloffset+yamloffset+yamloffset+"input : "+
        modval.inputs.foldLeft("")((agg2,inputval)=>{
          agg2 + "\n" + yamloffset+yamloffset+yamloffset+yamloffset+inputval._1+" : "+Utils.addOffset(yamloffset+yamloffset+yamloffset+yamloffset,inputval._2.toYaml())
        })
    })
    if(tojson){
      val yaml= new Yaml();
      val obj = yaml.load(yamlstring);
      return YamlElt.fromJava(obj).toJSONObject().toString
    }else{
      return yamlstring
    }
  }

}



object ModuleDef extends LazyLogging{
  val nameRegex = """[a-zA-Z][a-zA-Z0-9\-_]+(@[a-zA-Z0-9\-_]+)?"""
  val extendedNameRegex = "(_?"+nameRegex+")"

  def fromYaml(modulename:String,conf:String,conffile:String):ModuleDef={

    val yaml = new Yaml()
    val confMap = yaml.load(conf).asInstanceOf[java.util.Map[String,Any]]

    val module = new ModuleDef(conffile,
      modulename,
      ModuleDef.initDesc(confMap),
      ModuleDef.initInputs(confMap),
      ModuleDef.initOutputs(confMap),
      ModuleDef.initLogs(confMap),
      Nil
    );

    module.exec = ModuleDef.initRun(confMap,module.defdir,module.inputs)
    module
  }

  def initName(providedName:String,confMap:java.util.Map[String,Any]) = YamlElt.fromJava(confMap.get("name")) match {
    case YamlString(s) => {
      val regex = "^"+ModuleDef.nameRegex+"$"
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

  def initRun(confMap:java.util.Map[String,Any],wd:String,inputs:Map[String,AbstractModuleParameter]) = {
    var listmodules = Array[String]()
    var run = List[AbstractModuleVal]()
    YamlElt.fromJava(confMap.get("exec"))  match{
      case YamlList(modulevals) => {
        modulevals.forEach(new Consumer[Any] {
          override def accept(t: Any): Unit = {
            run = AbstractModuleVal.fromConf(t,run,inputs) :: run
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
    val forcecontainerized = VAL(None,None)
    forcecontainerized.fromYaml("false")
    x += ("CONTAINED"->new ModuleParameter[VAL]("VAL",None,None,None,Some(forcecontainerized)))
    val defaultdockerfile = VAL(None,None)
    defaultdockerfile.fromYaml("false")
    x += ("DOCKERFILE"->new ModuleParameter[VAL]("VAL",None,None,None,Some(defaultdockerfile)))
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
    x += ("RUN"->new ModuleParameter[LIST[MODVAL]]("MODVAL+",None,None,None))

    val regex = VAL(None,None)
    regex.fromYaml(".*")
    x += ("REGEX"->new ModuleParameter[VAL]("VAL",None,None,None,Some(regex)))

    val chunk_size = VAL(None,None)
    chunk_size.fromYaml("10")
    x += ("CHUNK_SIZE"->new ModuleParameter[VAL]("VAL",Some("Number of files to be processed in parallel"),None,None,Some(chunk_size)))
    x
  }

  def initMAPOutputs()={
    var x = Map[String,AbstractModuleParameter]()
    x += ("OUT"->new ModuleParameter[LIST[DIR]]("DIR+",None,None,None))
    x
  }


  val builtinmodules :List[String] = List("_CMD","_MAP","_FILTER","_ANONYMOUS")

}

class AnonymousDef(modulelist:List[AbstractModuleVal],context:List[AbstractModuleVal],env:Map[String,AbstractModuleParameter]) extends ModuleDef("/no/path","_ANONYMOUS","",AnonymousDef.initInputs(modulelist,context,env),AnonymousDef.initOutputs(modulelist),Map[String,String](),modulelist){

}

object AnonymousDef extends LazyLogging{
  def initOutputs(modulelist:List[AbstractModuleVal]):Map[String,AbstractModuleParameter]={
    val implicitvars = List("_","_RUN_DIR","_DEF_DIR","_CUR_MOD","_MOD_CONTEXT")
    var x = Map[String,AbstractModuleParameter]()
    modulelist.foreach(moduleval => {
      moduleval.moduledef.outputs.foreach(output => {
        if(!output._2.value.isEmpty) {
          x += (moduleval.namespace + "." + output._1 -> output._2)
        }
      })
    })
    x
  }

  def initInputs(modulelist:List[AbstractModuleVal],context:List[AbstractModuleVal],env:Map[String,AbstractModuleParameter]):Map[String,AbstractModuleParameter]={
    val implicitvars = List("_","_RUN_DIR","_DEF_DIR","_CUR_MOD","_MOD_CONTEXT")
    var outervariables = Map[String,AbstractModuleParameter]()
    var innervariables = Map[String,AbstractModuleParameter]()
    modulelist.foreach(moduleval => {
      moduleval.inputs.foreach(input => {
        val variables = input._2.extractVariables()
        variables.foreach(variable => {
          if(!innervariables.contains(variable) && !implicitvars.contains(variable)){
            val value = if(variables.length == 1 && !input._2.isExpression()){
              moduleval.moduledef.inputs(variable)
            }else{
              context.find(contextualmoduleval => {
                contextualmoduleval.moduledef.outputs.contains(variable)
              }) match {
                case Some(contextualmoduleval) => contextualmoduleval.moduledef.outputs(variable)
                case None => {
                  if(env.contains(variable)){
                    env(variable)
                  }else{
                    new ModuleParameter[VAL](variable,None,None,None,None)
                  }
                }
              }
            }
            outervariables += (variable -> value)
          }
        })
      })
      moduleval.moduledef.outputs.foreach(output => {
        innervariables += (moduleval.namespace+"."+output._1 -> output._2)
        logger.debug("added "+moduleval.namespace+"."+output._1)
      })
    })
    outervariables
  }
}

object CMDDef extends ModuleDef("/no/path","_CMD","Built-in module that run a UNIX commad",ModuleDef.initCMDInputs(),ModuleDef.initCMDOutputs(),Map[String,String](),List[AbstractModuleVal]()){

}

object MAPDef extends ModuleDef("/no/path","_MAP","Built-in module that map a LIST of FILE in Modules that process a single file",ModuleDef.initMAPInputs(),ModuleDef.initMAPOutputs(),Map[String,String](),List[AbstractModuleVal]()) {
}



