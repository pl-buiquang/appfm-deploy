package fr.limsi.iles.cpm.module.value

import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.module.ModuleManager
import fr.limsi.iles.cpm.module.definition.{CMDDef, MAPDef, ModuleDef}
import fr.limsi.iles.cpm.module.parameter._
import fr.limsi.iles.cpm.module.process._
import fr.limsi.iles.cpm.utils.{YamlMap, YamlElt}

/**
 * Created by buiquang on 9/7/15.
 */


abstract class AbstractModuleVal(val inputs:Map[String,ModuleParameterVal],val outputs:Map[String,String]) extends LazyLogging{
  val namespace:String
  val moduledef : ModuleDef

  def toProcess():AProcess
}


object AbstractModuleVal{

  def fromConf(yaml:Any) : AbstractModuleVal= {
    YamlElt.fromJava(yaml) match {
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
            ModuleVal(
              runitemname,
              modulevaldef,
              AbstractModuleVal.initInputs(modulevaldef, inputs),
              AbstractModuleVal.initOutputs(modulevaldef,outputs)
            )
          }else{
            throw new Exception("required module inputs are not all set for module "+modulename)
          }
        }else{
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
          modulename match {
            case "_CMD" => {
              CMDVal(runitemname,AbstractModuleVal.initInputs(CMDDef,inputs),AbstractModuleVal.initOutputs(CMDDef,outputs))
            }
            case "_MAP" => {
              MAPVal(runitemname,AbstractModuleVal.initInputs(MAPDef,inputs),AbstractModuleVal.initOutputs(MAPDef,outputs))
            }
            case _ => throw new Exception("unknown run module item")
          }
        }
      }
      case _ => throw new Exception("Malformed run definition")
    }
  }


  def initInputs(definition:ModuleDef,conf:java.util.Map[String,Any])={
    var inputs = Map[String,ModuleParameterVal]()
    val paramnames = conf.keySet()
    val it = paramnames.iterator()
    while(it.hasNext){
      val paramname = it.next()
      val value = definition.inputs(paramname).paramType match {
        case "VAL" => VAL()
        case "DIR" => DIR()
        case "FILE" => FILE()
        case "CORPUS" => CORPUS()
        case "LIST" => LIST[VAL]()
        case "MODULE+" => LIST[MODULE]()
      }
      value.parseFromJavaYaml(conf.get(paramname))
      inputs += (paramname -> value)
    }
    inputs
  }

  def initOutputs(definition:ModuleDef,conf:java.util.Map[String,Any])={
    Map[String,String]()
  }

  def initInputs(definition:ModuleDef)={
    var x = Map[String,ModuleParameterVal]()
    definition.inputs.map(in => {
      val value = in._2.paramType match {
        case "VAL" => VAL()
        case "DIR" => DIR()
        case "FILE" => FILE()
        case "CORPUS" => CORPUS()
        case "LIST" => LIST()
      }
      value.parseFromJavaYaml("$"+in._1)
      x += (in._1 -> value)
    })
    x
  }

  def initOutputs(definition:ModuleDef)={
    var x = Map[String,String]()
    definition.outputs.map(out => {
      x += (out._1 -> out._1)
    })
    x
  }

}


case class ModuleVal(override val namespace:String,override val moduledef:ModuleDef,ainputs:Map[String,ModuleParameterVal],aoutputs:Map[String,String]) extends AbstractModuleVal(ainputs,aoutputs){
  override def toProcess(): AProcess = {
    new ModuleProcess(this)
  }
}


case class CMDVal(override val namespace:String ,ainputs:Map[String,ModuleParameterVal],aoutputs:Map[String,String]) extends AbstractModuleVal(ainputs,aoutputs) {
  override val moduledef = CMDDef

  override def toProcess(): AProcess = {
    new CMDProcess(this)
  }

}

case class MAPVal(override val namespace:String ,ainputs:Map[String,ModuleParameterVal],aoutputs:Map[String,String]) extends AbstractModuleVal(ainputs,aoutputs) {
  override val moduledef = MAPDef

  override def toProcess(): AProcess = {
    new MAPProcess(this)
  }

}

