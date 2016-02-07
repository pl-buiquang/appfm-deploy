package fr.limsi.iles.cpm.module.process

import java.io.FileInputStream
import java.util.function.{BiConsumer, Consumer}


import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.module.parameter.AbstractModuleParameter
import fr.limsi.iles.cpm.module.value.{AbstractParameterVal, VAL, AbstractParameterVal$}
import fr.limsi.iles.cpm.utils.{YamlMap, Log, YamlElt}
import org.yaml.snakeyaml.Yaml

/**
 * Created by buiquang on 9/24/15.
 */
class RunEnv(private var args:Map[String,AbstractParameterVal]) extends LazyLogging{
  var logs = Map[String,AbstractParameterVal]()

  def serialize():String={
    args.foldLeft("")((agg,el)=>{
      el._2.toYaml()

      val yamlel = if(el._2._mytype.endsWith("*")){
        "\n    "+el._2.toYaml().replace("\n","\n    ")
      }else{
        //">\n    "+"""""""+el._2.toYaml().trim.replace("\n","\n    ").replace("""\""","""\\""").replace(""""""","""\"""")+"""""""
        """""""+el._2.toYaml().trim.replace("""\""","""\\""").replace(""""""","""\"""").replace("\n","""\n""")+"""""""
      }
      agg+"\n\""+el._1+"\" : \n  type : "+el._2._mytype+"\n  "+ (if(el._2.format.nonEmpty){
        "format : "+el._2.format.get+"\n  "
      }else{
        ""
      })+ (if(el._2.schema.nonEmpty){
        "schema : "+el._2.schema.get+"\n  "
      }else{
        ""
      }) +"value : "+yamlel
    })
  }



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

  def resolveValueToString(value:String)(implicit skip:Boolean=false) : String= {
    RunEnv.resolveValueToString(this.args,value)(skip)
  }

  def resolveValueToYaml(value:String)(implicit skip:Boolean=false) : String= {
    RunEnv.resolveValueToYaml(this.args,value)(skip)
  }


  def getRawVar(key:String) : Option[AbstractParameterVal]= {
    if(this.args.contains(key)){
      Some(this.args(key))
    }else{
      None
    }
  }

  def getVars() : Map[String,AbstractParameterVal] = {
    val envargs = args
    envargs
  }

  def setVar(key:String,value:AbstractParameterVal) = {
    args += (key -> value)
  }

  def setVars(vars:Map[String,AbstractParameterVal]) = {
    args ++= vars
  }

  def debugPrint() : Unit = {
    args.foreach(elt => {
      logger.debug(elt._1+" of type "+elt._2.getClass.toGenericString+" with value "+elt._2.asString())
    })
  }

}

object RunEnv {

  def deserialize(serialized:String):RunEnv={
    val env = new RunEnv(Map[String,AbstractParameterVal]())
    YamlElt.fromJava(serialized) match {
      case YamlMap(map) => {
        map.forEach(new BiConsumer[String,Any] {
          override def accept(t: String, u: Any): Unit = {
            val valuebundle = u.asInstanceOf[java.util.Map[String,Any]]
            val format = if(valuebundle.containsKey("format")){
              Some(valuebundle.get("format").toString)
            }else{
              None
            }
            val schema = if(valuebundle.containsKey("schema")){
              Some(valuebundle.get("schema").toString)
            }else{
              None
            }
            val vartype = if(valuebundle.containsKey("type")){
              valuebundle.get("type").toString
            }else{
              "VAL"
            }
            val variable = AbstractModuleParameter.createVal(vartype,format,schema)
            val value = if(valuebundle.containsKey("value")){
              valuebundle.get("value")
            }else{
              ""
            }
            variable.fromYaml(value)(true)
            env.args += (t -> variable)
          }
        })
      }
      case _ => {
        if(serialized==""){
          Log("warning : empty environment!")
        }else{
          throw new Exception("can't parse serialized environnment ("+serialized+")")
        }
      }
    }
    env
  }

  def forcePathToBeRelativeTo(basedir:String,path:String) : String = {
    if(!path.startsWith(basedir)){
      basedir+path
    }
    path
  }

  def resolveValue(env:Map[String,AbstractParameterVal],value:AbstractParameterVal)(implicit skip:Boolean=false) : AbstractParameterVal = {
    val resolved = value.newEmpty()
    val resolvedstring = RunEnv.resolveValueToString(env,value.toYaml())
    resolved.fromYaml(resolvedstring)
    resolved
  }

  def resolveValueToYaml(env:Map[String,AbstractParameterVal],value:String)(implicit skip:Boolean=false) : String ={
    var resolved = """(?<!\\)\$\{(.*?)\}""".r.replaceAllIn(value,m => {
      val splitted = m.group(1).split(":")
      val complexvariable = if(splitted.length>1){
        (splitted.slice(0,splitted.length-1).mkString("."),splitted(splitted.length-1))
      }else{
        (m.group(1),"")
      }
      val replacement = if(env.contains(complexvariable._1)){
        env(complexvariable._1) match{
          case o:AbstractParameterVal => complexvariable._2 match {
            case "" => o.toYaml()
            case _ => o.getAttr(complexvariable._2).toYaml()
          }
          case _ => throw new Exception("undefined value");
        }
      }else{
        m.group(0)
      }
      replacement.replace("$","\\$") // escape "$" to prevent group reference
    })
    resolved = """(?<!\\)\$([a-zA-Z_\-]+)""".r.replaceAllIn(resolved,m => {
      val replacement = if(env.contains(m.group(1))){
        env(m.group(1)) match{
          case o:AbstractParameterVal => val s = o.toYaml(); Log(s);s
          case _ => throw new Exception("undefined value");
        }
      }else{
        m.group(0)
      }
      replacement.replace("$","\\$") // escape "$" to prevent group reference
    })
    resolved
  }

  def resolveValueToString(env:Map[String,AbstractParameterVal],value:String)(implicit skip:Boolean=false) :String={
    var resolved = """(?<!\\)\$\{(.*?)\}""".r.replaceAllIn(value,m => {
      val splitted = m.group(1).split(":")
      val complexvariable = if(splitted.length>1){
        (splitted.slice(0,splitted.length-1).mkString("."),splitted(splitted.length-1))
      }else{
        (m.group(1),"")
      }
      val replacement = if(env.contains(complexvariable._1)){
        env(complexvariable._1) match{
          case o:AbstractParameterVal => complexvariable._2 match {
            case "" => o.toString()
            case _ => o.getAttr(complexvariable._2).toString()
          }
          case _ => throw new Exception("undefined value");
        }
      }else{
        m.group(0)
      }
      replacement.replace("$","\\$") // escape "$" to prevent group reference
    })
    resolved = """(?<!\\)\$([a-zA-Z_\-]+)""".r.replaceAllIn(resolved,m => {
      val replacement = if(env.contains(m.group(1))){
        env(m.group(1)) match{
          case o:AbstractParameterVal => val s = o.toString(); Log(s);s
          case _ => throw new Exception("undefined value");
        }
      }else{
        m.group(0)
      }
      replacement.replace("$","\\$") // escape "$" to prevent group reference
    })
    resolved
  }

  def initFromConf(confMap:java.util.Map[String,Any]) = {
    var args = Map[String,AbstractParameterVal]()
    YamlElt.readAs[java.util.HashMap[String,String]](confMap) match {
      case Some(map) => {
        map.forEach(new BiConsumer[String,String] {
          override def accept(t: String, u: String): Unit = {
            val x = VAL(None,None)
            x.fromYaml(u)
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