package fr.limsi.iles.cpm.process

import fr.limsi.iles.cpm.corpus.Corpus
import org.yaml.snakeyaml.Yaml
import scala.sys.process._
import scala.util.matching.Regex

/**
 * Created by buiquang on 9/7/15.
 */


class RunEnv(var args:Map[String,PARAMETER]){

}

class Module(moduleName:String,definitionFilePath:String) {
  var mrootpath : String = null
  var runcmds :Seq[ModuleUnit] = Seq[ModuleUnit]()
  var inputs = Map[String,PARAMETER]()
  var outputs = Map[String,PARAMETER]()

  def init():Unit={
    val yaml = new Yaml()
    val confMap = yaml.load(definitionFilePath).asInstanceOf[java.util.Map[String, Any]]
    var run = confMap.get("run").asInstanceOf[java.util.ArrayList[java.util.Map[String,Any]]]
    val it = run.iterator()
    while(it.hasNext){
      val j = it.next()
      val key = (j.keySet()).toArray()(0).asInstanceOf[String]
      var mod : ModuleUnit =  key match {
        case "CMD" => val m = CMD(null); m.init(j); m
        case "MAP" => val m = MAP(null,null) ; m.init(j) ; m
        case "FILTER" => val m = FILTER(null,null); m.init(j); m
        case module => val m = ModuleManager.modules(key); MODULE(m)
      }
      runcmds = runcmds.+:(mod)
    }

    val output = confMap.get("output").asInstanceOf[java.util.Map[String,Any]]
    val itout = output.keySet().iterator()
    while(itout.hasNext){
      val outputname = itout.next()


      val outputparams = output.get(outputname).asInstanceOf[java.util.Map[String,Any]]
      val param = outputparams.get("type").asInstanceOf[String] match {
        case "FILE" => FILE(outputparams.get("val").asInstanceOf[String])
        case "VAL" => VAL(outputparams.get("val").asInstanceOf[String])
      }

      outputs = outputs + (outputname -> param)
    }

    val input = confMap.get("input").asInstanceOf[java.util.Map[String,Any]]
    val itin = input.keySet().iterator()
    while(itin.hasNext){
      val inputname = itin.next()


      val inputparams = input.get(inputname).asInstanceOf[java.util.Map[String,Any]]
      val param = inputparams.get("type").asInstanceOf[String] match {
        case "FILE" => FILE(inputparams.get("val").asInstanceOf[String])
        case "VAL" => VAL(inputparams.get("val").asInstanceOf[String])
      }

      inputs = inputs + (inputname -> param)
    }
  }

  def initRunEnv(parentRunEnv:RunEnv) = {
    val key = "foo"
    inputs + (key -> parentRunEnv.args(key))
  }

  def run(runenv:RunEnv,cmds:Seq[ModuleUnit],ns:String):Unit = {
    println("Executing "+moduleName)

    cmds.foreach(
      _ match {
        case c : CMD => c.run(runenv,ns+"."+moduleName)
        case m : MAP => m.run(runenv,ns+"."+moduleName)
        case f : FILTER => f.run(runenv,ns+"."+moduleName)
        case MODULE(m) => m.run(runenv,m.runcmds,ns+"."+moduleName)
      }
    )

  }
}

abstract class ModuleUnit{
  def init(conf:java.util.Map[String,Any])
  def run(env:RunEnv,ns:String)
}

case class CMD(var cmd:String) extends  ModuleUnit{
  override def init(conf:java.util.Map[String,Any]) = {

  }
  override def run(env:RunEnv,ns:String) = {
    env.args + (ns -> (cmd.replaceAll("$.*?",env.args("\0") match{
      case VAL(value) => value
      case LIST(items) => items.reduce((a,b) => a+(b match{
        case VAL(value) => value
      }))
      case FILE(path)=> path
      case _ => "non"
    }) !!))
  }
}

case class MAP(in:LIST,pipeline:Seq[ModuleUnit]) extends ModuleUnit{
  override def init(conf:java.util.Map[String,Any]) = {

  }
  override def run(env:RunEnv,ns:String)={
    in.items.map(_ match{
      case VAL(value) => value
    })//run(LIST(Seq(input)),pipeline))
  }
}
case class FILTER(input:PARAMETER,regex:VAL) extends ModuleUnit{
  override def init(conf:java.util.Map[String,Any]) = {
    conf.get("IN")
  }
  override def run(env:RunEnv,ns:String)={
    input match {
      case LIST(items) => items.filter(
        _ match {
          case VAL(value) => !(new Regex(regex.value)).findFirstMatchIn(value).isEmpty
          case FILE(path) => !(new Regex(regex.value)).findFirstMatchIn(path).isEmpty
          case _ => false
        }
      )
      case _ => input
    }
  }
}


trait PARAMETER{
  def foo = {}
}

case class VAL(value:String) extends PARAMETER
case class FILE(path:String) extends PARAMETER
case class CORPUS(id:String) extends Corpus(id) with PARAMETER
case class LIST[A <: PARAMETER](items:Seq[A]) extends PARAMETER{
}
case class MODULE(module:Module) extends ModuleUnit with PARAMETER{
  override def init(conf:java.util.Map[String,Any]) = {

  }
  override def run(env:RunEnv,ns:String)={
    module.run(env,module.runcmds,ns)
  }
}
