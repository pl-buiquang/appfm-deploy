package fr.limsi.iles.cpm.process

import java.util.concurrent.{Executors, ExecutorService}

import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.server.Server
import fr.limsi.iles.cpm.corpus.Corpus
import fr.limsi.iles.cpm.utils.{Log, YamlElt}
import org.yaml.snakeyaml.Yaml
import org.zeromq.ZMQ
import scala.sys.process._
import scala.util.Random
import scala.util.matching.Regex

/**
 * Created by buiquang on 9/7/15.
 */


abstract class AbstractModuleVal(val inputs:Map[String,ModuleParameterVal],val outputs:Map[String,String]) extends LazyLogging{
  val namespace:String
  val moduledef : ModuleDef

  def toProcess():AProcess
}


object AbstractModuleVal{
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
        case "LIST" => LIST()
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


case class CMDVal(val parentWD :String,override val namespace:String ,ainputs:Map[String,ModuleParameterVal],aoutputs:Map[String,String]) extends AbstractModuleVal(ainputs,aoutputs) {
  override val moduledef = CMDDef

  override def toProcess(): AProcess = {
    new CMDProcess(this)
  }

}

case class MAPVal(val parentWD :String,override val namespace:String ,ainputs:Map[String,ModuleParameterVal],aoutputs:Map[String,String]) extends AbstractModuleVal(ainputs,aoutputs) {
  override val moduledef = MAPDef

  override def toProcess(): AProcess = {
    new MAPProcess(this)
  }

}

