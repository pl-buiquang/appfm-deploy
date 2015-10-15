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


abstract class AbstractModuleVal(val moduledef:ModuleDef,conf:Option[java.util.Map[String,Any]]) extends LazyLogging{
  val namespace:String
  val inputs:Map[String,AbstractParameterVal] = AbstractModuleVal.initInputs(moduledef,conf)

  def toProcess():AbstractProcess
}


object AbstractModuleVal{

  def fromConf(yaml:Any) : AbstractModuleVal= {
    YamlElt.fromJava(yaml) match {
      case YamlMap(moduleval) => {
        if(moduleval.keySet().size()!=1){
          throw new Exception("Module run item error")
        }
        val runitemname = moduleval.keySet().iterator().next()
        val cmdmatch = """((?:\w|-)+:)?((?:\w|-)+)""".r.findFirstMatchIn(runitemname)
        val modulename = cmdmatch match {
          case Some(result) => {
            result.group(2)
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
              Some(inputs)
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
              CMDVal(runitemname,Some(inputs))
            }
            case "_MAP" => {
              MAPVal(runitemname,Some(inputs))
            }
            case _ => throw new Exception("unknown run module item")
          }
        }
      }
      case _ => throw new Exception("Malformed run definition")
    }
  }

  def initInputs(definition:ModuleDef,conf:Option[java.util.Map[String,Any]]) :Map[String,AbstractParameterVal] ={
    conf match {
      case Some(yaml) => initInputs(definition,yaml)
      case None => initInputs(definition)
    }
  }

  def initInputs(definition:ModuleDef,conf:java.util.Map[String,Any]) :Map[String,AbstractParameterVal]={
    var inputs = Map[String,AbstractParameterVal]()
    val paramnames = conf.keySet()
    val it = paramnames.iterator()
    while(it.hasNext){
      val paramname = it.next()
      val value = definition.inputs(paramname).createVal()
      value.parseYaml(conf.get(paramname))
      inputs += (paramname -> value)
    }
    inputs
  }


  def initInputs(definition:ModuleDef):Map[String,AbstractParameterVal]={
    var x = Map[String,AbstractParameterVal]()
    definition.inputs.map(in => {
      val value = in._2.paramType match {
        case "VAL" => VAL()
        case "DIR" => DIR()
        case "FILE" => FILE()
        case "CORPUS" => CORPUS()
        case "LIST" => LIST()
      }
      value.parseYaml("$"+in._1)
      x += (in._1 -> value)
    })
    x
  }

}



case class ModuleVal(override val namespace:String,override val moduledef:ModuleDef,conf:Option[java.util.Map[String,Any]]) extends AbstractModuleVal(moduledef,conf){

  override def toProcess(): AbstractProcess = {
    new ModuleProcess(this)
  }
}


case class CMDVal(override val namespace:String,conf:Option[java.util.Map[String,Any]]) extends AbstractModuleVal(CMDDef,conf){

  override def toProcess(): AbstractProcess = {
    new CMDProcess(this)
  }

}

case class MAPVal(override val namespace:String,conf:Option[java.util.Map[String,Any]]) extends AbstractModuleVal(MAPDef,conf){

  override def toProcess(): AbstractProcess = {
    new MAPProcess(this)
  }

}

