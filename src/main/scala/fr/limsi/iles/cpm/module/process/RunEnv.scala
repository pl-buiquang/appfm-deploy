package fr.limsi.iles.cpm.module.process

import java.io.FileInputStream
import java.util.function.{BiConsumer, Consumer}


import fr.limsi.iles.cpm.module.value.{VAL, ModuleParameterVal}
import fr.limsi.iles.cpm.utils.{Log, YamlElt}
import org.yaml.snakeyaml.Yaml

/**
 * Created by buiquang on 9/24/15.
 */
class RunEnv(var args:Map[String,ModuleParameterVal]){
  var logs = Map[String,ModuleParameterVal]()

  def resolveValue(value:String) : ModuleParameterVal = {

  }

  def resolveValueToString(value:String) : String= {
    var resolved = """\$\{(.*?)\}""".r.replaceAllIn(value,m => {
      val splitted = m.group(1).split(":")
      val complexvariable = if(splitted.length>1){
        (splitted.slice(0,splitted.length-1).mkString("."),splitted(splitted.length-1))
      }else{
        (m.group(1),"")
      }
      if(this.args.contains(complexvariable._1)){
        this.args(complexvariable._1) match{
          case o:ModuleParameterVal => complexvariable._2 match {
            case "" => o.asString()
            case _ => o.getAttr(complexvariable._2).toString
          }
          case _ => throw new Exception("undefined value");
        }
      }else{
        m.group(0).replace("$","\\$") // escape "$" to prevent group reference
      }
    })
    resolved = """\$([a-zA-Z_\-]+)""".r.replaceAllIn(resolved,m => {
      if(this.args.contains(m.group(1))){
        this.args(m.group(1)) match{
          case o:ModuleParameterVal => val s = o.asString(); Log(s);s
          case _ => throw new Exception("undefined value");
        }
      }else{
        m.group(0).replace("$","\\$") // escape "$" to prevent group reference
      }
    })
    resolved
  }

}

object RunEnv {
  def initFromConf(content:String) = {
    var args = Map[String,ModuleParameterVal]()
    val yaml = new Yaml()
    val ios = new FileInputStream(content)
    val confMap = yaml.load(ios).asInstanceOf[java.util.Map[String,Any]]
    YamlElt.readAs[java.util.HashMap[String,String]](confMap) match {
      case Some(map) => {
        map.forEach(new BiConsumer[String,String] {
          override def accept(t: String, u: String): Unit = {
            val x = VAL()
            x.parseFromJavaYaml(u)
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