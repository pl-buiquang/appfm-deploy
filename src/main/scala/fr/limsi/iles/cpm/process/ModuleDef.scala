package fr.limsi.iles.cpm.process

import java.io.{File, FileInputStream}
import java.util.function.Consumer
import java.util.regex.Pattern

import fr.limsi.iles.cpm.corpus.Corpus
import org.yaml.snakeyaml.Yaml
import fr.limsi.iles.cpm.utils._
import com.typesafe.scalalogging.LazyLogging

import scala.util.matching.Regex

/**
 * Created by buiquang on 9/16/15.
 */
class ModuleDef(
   val confFilePath:String,
   val name:String,
   val desc:String,
   val inputs:Map[String,AModuleParameter],
   val outputs:Map[String,AModuleParameter],
   val log:Map[String,String],
   var run:List[AbstractModuleVal]
 ){

  def getLastModificationDate(): Long ={
    val file = new java.io.File(confFilePath)
    file.lastModified()
  }

  def getWd():String = {
    val file = new java.io.File(confFilePath)
    file.getParent
  }

  val lastModified = getLastModificationDate()

  val wd = getWd()

}



object ModuleDef extends LazyLogging{

  def initName(providedName:String,confMap:java.util.Map[String,Any]) = YamlElt.fromJava(confMap.get("name")) match {
    case YamlString(s) => {
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
    var moduleinputs = Map[String,AModuleParameter]()
    val parsed = YamlElt.fromJava(confMap.get("input"))
    parsed match {
      case YamlMap(inputs) => {
        val inputnames = inputs.keySet();
        val it = inputnames.iterator()
        while(it.hasNext){
          val name = it.next()
          val parsedinputdef = inputs.get(name)
          YamlElt.fromJava(parsedinputdef) match {
            case YamlMap(inputdef) => {
              val intype = YamlElt.readAs[String](inputdef.get("type"))
              val informat = YamlElt.readAs[String](inputdef.get("format"))
              val inschema = YamlElt.readAs[String](inputdef.get("schema"))
              val indesc = YamlElt.readAs[String](inputdef.get("desc"))
              val defaultval = YamlElt.fromJava(inputdef.get("val")) match {
                case YamlString(value) => value
                case YamlList(list) => list
                case _ => None
              }
              intype.getOrElse("unknown type") match {
                case "VAL" => moduleinputs += (name -> new ModuleParameter[VAL](intype.get,indesc,informat,inschema,YamlElt.fromJava(inputdef.get("val")) match {
                  case YamlString(value) => val x = new VAL(); x.parseFromJavaYaml(value); Some(x)
                  case _ => None
                }))
                case "FILE" => moduleinputs += (name -> new ModuleParameter[FILE](intype.get,indesc,informat,inschema))
                case "LIST" => moduleinputs += (name -> new ModuleParameter[LIST](intype.get,indesc,informat,inschema))
                case "CORPUS" => moduleinputs += (name -> new ModuleParameter[CORPUS](intype.get,indesc,informat,inschema))
                case "MODULE" => moduleinputs += (name -> new ModuleParameter[MODULE](intype.get,indesc,informat,inschema))
                case _ => throw new Exception("unknown type for input \""+name+"\"")
              }
            }
            case _ => throw new Exception("wrong input definition")
          }
        }
      }
      case _ => throw new Exception("wrong input definition")
    }
    moduleinputs
  }

  def initOutputs(confMap:java.util.Map[String,Any]) = {
    var moduleoutputs = Map[String,AModuleParameter]()
    val parsed = YamlElt.fromJava(confMap.get("output"))
    parsed match {
      case YamlMap(inputs) => {
        val outputnames = inputs.keySet();
        val it = outputnames.iterator()
        while(it.hasNext){
          val name = it.next()
          val parsedoutputdef = inputs.get(name)
          YamlElt.fromJava(parsedoutputdef) match {
            case YamlMap(outputdef) => {
              val outtype = YamlElt.readAs[String](outputdef.get("type"))
              val outformat = YamlElt.readAs[String](outputdef.get("format"))
              val outschema = YamlElt.readAs[String](outputdef.get("schema"))
              val outdesc = YamlElt.readAs[String](outputdef.get("desc"))
              val outval = YamlElt.fromJava(outputdef.get("val")) match {
                case YamlString(value) => value
                case YamlList(list) => list
                case _ => throw new Exception("Missing or malformed value for output \""+name+"\"")
              }
              outtype.getOrElse("unknown type") match {
                case "VAL" => {
                  val x = new ModuleParameter[VAL](outtype.get,outdesc,outformat,outschema)
                  x.setVal(outputdef.get("val"),new VAL());
                  moduleoutputs += (name -> x)
                }
                case "FILE" => {
                  val x = new ModuleParameter[FILE](outtype.get,outdesc,outformat,outschema)
                  x.setVal(outputdef.get("val"),new FILE());
                  moduleoutputs += (name -> x)
                }
                case "LIST" => {
                  val x = new ModuleParameter[LIST](outtype.get,outdesc,outformat,outschema)
                  x.setVal(outputdef.get("val"),new LIST());
                  moduleoutputs += (name -> x)
                }
                case "CORPUS" => {
                  val x = new ModuleParameter[CORPUS](outtype.get,outdesc,outformat,outschema)
                  x.setVal(outputdef.get("val"),new CORPUS());
                  moduleoutputs += (name -> x)
                }
                case "MODULE" => {
                  val x = new ModuleParameter[MODULE](outtype.get,outdesc,outformat,outschema)
                  x.setVal(outputdef.get("val"),new MODULE());
                  moduleoutputs += (name -> x)
                }
                case _ => throw new Exception("unknown type for input \""+name+"\"")
              }
            }
            case _ => throw new Exception("wrong input definition")
          }
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
    YamlElt.fromJava(confMap.get("run"))  match{
      case YamlList(modulevals) => {
        modulevals.forEach(new Consumer[Any] {
          override def accept(t: Any): Unit = {
            YamlElt.fromJava(t) match {
              case YamlMap(moduleval) => {
                if(moduleval.keySet().size()!=1){
                  throw new Exception("Module run item error")
                }
                val runitemname = moduleval.keySet().iterator().next()
                val cmdmatch = """((?:\w|-)+)(\.(\w+))?""".r.findFirstMatchIn(runitemname)
                val modulename = cmdmatch match {
                  case Some(result) => {
                    result.group(1)
                  }
                  case None => {
                    throw new Exception("Error parsing module value name")
                  }
                }
                if(ModuleManager.modules.keys.exists(_ == modulename)){
                  val runitemconf : java.util.Map[String,Any] = YamlElt.readAs[java.util.HashMap[String,Any]](moduleval.get(runitemname)) match {
                    case Some(map) => map
                    case None => throw new Exception("malformed module value")
                  }
                  val inputs = YamlElt.readAs[java.util.HashMap[String,Any]](runitemconf.get("input")) match {
                    case Some(map) => map
                    case None => new java.util.HashMap[String,Any]()
                  }
                  val outputs = YamlElt.readAs[java.util.HashMap[String,Any]](runitemconf.get("output")) match {
                    case Some(map) => map
                    case None => new java.util.HashMap[String,Any]()
                  }
                  // TODO check for outputs consistency with module def and multiple variable def
                  val modulevaldef = ModuleManager.modules(modulename)
                  if(modulevaldef.inputs.filter(input => {
                    !inputs.containsKey(input._1) && input._2.value.isEmpty
                  }).size==0){
                    run = ModuleVal(
                      runitemname,
                      modulevaldef,
                      AbstractModuleVal.initInputs(modulevaldef, inputs),
                      AbstractModuleVal.initOutputs(modulevaldef,outputs)
                    ) :: run
                  }else{
                    throw new Exception("required module inputs are not all set for module "+modulename)
                  }
                }else{
                  modulename match {
                    case "_CMD" => {
                      val runitemconf : java.util.Map[String,Any] = YamlElt.readAs[java.util.Map[String,Any]](moduleval.get(runitemname)) match {
                        case Some(map) => map
                        case None => throw new Exception("malformed module value")
                      }
                      val inputs = YamlElt.readAs[java.util.Map[String,Any]](runitemconf.get("input")) match {
                        case Some(map) => map
                        case None => new java.util.HashMap[String,Any]()
                      }
                      val outputs = YamlElt.readAs[java.util.Map[String,Any]](runitemconf.get("output")) match {
                        case Some(map) => map
                        case None => new java.util.HashMap[String,Any]()
                      }
                      run = CMDVal(wd,runitemname,AbstractModuleVal.initInputs(CMDDef,inputs),AbstractModuleVal.initOutputs(CMDDef,outputs)) :: run
                    }
                    case _ => throw new Exception("unknown run module item")
                  }
                }
              }
              case _ => throw new Exception("Malformed run definition")
            }
          }
        })
      }
      case _ => throw new Exception("Module does not provide run information!")
    }
    run
  }

  def initCMDInputs()={
    var x = Map[String,AModuleParameter]()
    x += ("CMD"->new ModuleParameter[VAL]("VAL",None,None,None))
    x
  }

  def initCMDOutputs()={
    var x = Map[String,AModuleParameter]()
    x += ("STDOUT"->new ModuleParameter[VAL]("VAL",None,None,None))
    x
  }

  def initMAPInputs()={
    var x = Map[String,AModuleParameter]()
    x += ("IN"->new ModuleParameter[LIST]("LIST",None,None,None))
    x += ("RUN"->new ModuleParameter[LIST]("LIST",None,None,None))
    x
  }

  def initMAPOutputs()={
    var x = Map[String,AModuleParameter]()
    x += ("OUT"->new ModuleParameter[LIST]("LIST",None,None,None))
    x
  }

  def initFILTERMAPInputs()={
    var x = Map[String,AModuleParameter]()
    x += ("IN"->new ModuleParameter[FILE]("LIST",None,None,None))
    x += ("RUN"->new ModuleParameter[LIST]("LIST",None,None,None))
    x
  }

  def initFILTERMAPOutputs()={
    var x = Map[String,AModuleParameter]()
    x += ("OUT"->new ModuleParameter[LIST]("LIST",None,None,None))
    x
  }

}


object CMDDef extends ModuleDef("/no/path","_CMD_","Built-in module that run a UNIX commad",ModuleDef.initCMDInputs(),ModuleDef.initCMDOutputs(),Map[String,String](),List[AbstractModuleVal]()){

}

object MAPDef extends ModuleDef("/no/path","_MAP_","Built-in module that map a LIST of FILE in Modules that process a single file",ModuleDef.initMAPInputs(),ModuleDef.initCMDOutputs(),Map[String,String](),List[AbstractModuleVal]()) {
}

object FILTERMAPDef extends ModuleDef("/no/path","_FILTERMAP_","Built-in module that filter the content of a FILE using a regex and create a new LIST of FILE containing filtered original content which are mapped to Modules that process a single file",ModuleDef.initFILTERMAPInputs(),ModuleDef.initFILTERMAPOutputs(),Map[String,String](),List[AbstractModuleVal]()) {
}


