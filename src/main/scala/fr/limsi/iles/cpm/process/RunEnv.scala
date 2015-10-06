package fr.limsi.iles.cpm.process

import java.io.FileInputStream
import java.util.function.{BiConsumer, Consumer}

import fr.limsi.iles.cpm.utils.{Log, YamlElt}
import org.yaml.snakeyaml.Yaml

/**
 * Created by buiquang on 9/24/15.
 */
class RunEnv(var args:Map[String,ModuleParameterVal]){
  var logs = Map[String,ModuleParameterVal]()

  def resolveVars(value:String) : String= {
    var resolved = """\$\{(.*?)\}""".r.replaceAllIn(value,m => {
      this.args(m.group(1)) match{
        case o:ModuleParameterVal => val s = o.asString(); Log(s);s
        case _ => "not found"
      }
    })
    """\$([a-zA-Z_\-]+)""".r.replaceAllIn(resolved,m => {
      this.args(m.group(1)) match{
        case o:ModuleParameterVal => o.asString()
        case _ => "non"
      }
    })
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