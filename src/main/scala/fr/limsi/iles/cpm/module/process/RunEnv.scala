package fr.limsi.iles.cpm.module.process

import java.io.FileInputStream
import java.util.function.{BiConsumer, Consumer}


import fr.limsi.iles.cpm.module.value.{AbstractParameterVal, VAL, AbstractParameterVal$}
import fr.limsi.iles.cpm.utils.{Log, YamlElt}
import org.yaml.snakeyaml.Yaml

/**
 * Created by buiquang on 9/24/15.
 */
class RunEnv(var args:Map[String,AbstractParameterVal]){
  var logs = Map[String,AbstractParameterVal]()

  def copy():RunEnv={
    var newargs = Map[String,AbstractParameterVal]()
    args.foreach(paramval => {
      newargs += (paramval._1 -> paramval._2)
    })
    val newenv = new RunEnv(newargs)
    newenv
  }

  def resolveValue(value:AbstractParameterVal) : AbstractParameterVal = {
    RunEnv.resolveValue(this.args,value)
  }

  def resolveValueToString(value:String) : String= {
    RunEnv.resolveValueToString(this.args,value)
  }

}

object RunEnv {

  def forcePathToBeRelativeTo(basedir:String,path:String) : String = {
    if(!path.startsWith(basedir)){
      basedir+path
    }
    path
  }

  def resolveValue(env:Map[String,AbstractParameterVal],value:AbstractParameterVal) : AbstractParameterVal = {
    val resolved = value.newEmpty()
    val resolvedstring = RunEnv.resolveValueToString(env,value.asString())
    resolved.fromYaml(resolvedstring)
    resolved
  }

  def resolveValueToString(env:Map[String,AbstractParameterVal],value:String) :String={
    var resolved = """\$\{(.*?)\}""".r.replaceAllIn(value,m => {
      val splitted = m.group(1).split(":")
      val complexvariable = if(splitted.length>1){
        (splitted.slice(0,splitted.length-1).mkString("."),splitted(splitted.length-1))
      }else{
        (m.group(1),"")
      }
      if(env.contains(complexvariable._1)){
        env(complexvariable._1) match{
          case o:AbstractParameterVal => complexvariable._2 match {
            case "" => o.asString()
            case _ => o.getAttr(complexvariable._2).asString()
          }
          case _ => throw new Exception("undefined value");
        }
      }else{
        m.group(0).replace("$","\\$") // escape "$" to prevent group reference
      }
    })
    resolved = """\$([a-zA-Z_\-]+)""".r.replaceAllIn(resolved,m => {
      if(env.contains(m.group(1))){
        env(m.group(1)) match{
          case o:AbstractParameterVal => val s = o.asString(); Log(s);s
          case _ => throw new Exception("undefined value");
        }
      }else{
        m.group(0).replace("$","\\$") // escape "$" to prevent group reference
      }
    })
    resolved
  }

  def initFromConf(content:String) = {
    var args = Map[String,AbstractParameterVal]()
    val yaml = new Yaml()
    val ios = new FileInputStream(content)
    val confMap = yaml.load(ios).asInstanceOf[java.util.Map[String,Any]]
    YamlElt.readAs[java.util.HashMap[String,String]](confMap) match {
      case Some(map) => {
        map.forEach(new BiConsumer[String,String] {
          override def accept(t: String, u: String): Unit = {
            val x = VAL()
            x.parseYaml(u)
            args += (t -> x)
          }
        })
      }
      case None => {
        throw new Exception("malformed configuration file")
      }
    }
    new RunEnv(args)
  }

}