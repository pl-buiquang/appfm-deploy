package fr.limsi.iles.cpm.module.value

import java.util.function.Consumer

import fr.limsi.iles.cpm.module.ModuleManager
import fr.limsi.iles.cpm.module.definition.{MAPDef, CMDDef}
import fr.limsi.iles.cpm.module.parameter.AbstractModuleParameter
import fr.limsi.iles.cpm.utils.{YamlMap, YamlString, YamlList, YamlElt}

import scala.collection


sealed abstract class ModuleParameterVal{

  def parseFromJavaYaml(yaml:Any):Unit
  def asString():String

  def extractVariables() : Array[String]={
    val value = asString()
    val complexVars = """\$\{(.+?)\}""".r.findAllMatchIn(value)
    val simpleVars = """\$([a-zA-Z_\-]+)""".r.findAllMatchIn(value)
    var vars = Array[String]()
    while(complexVars.hasNext){
      val splitted = complexVars.next().group(1).split(":")
      val complexvariable = if(splitted.length>1){
        (splitted.slice(0,splitted.length-1).mkString("."),splitted(splitted.length-1))
      }else{
        (complexVars.next().group(1),"")
      }
      vars :+= complexvariable._1
    }
    while(simpleVars.hasNext){
      vars :+= simpleVars.next().group(1)
    }
    vars
  }

  def getAttr(attrName:String):ModuleParameterVal

}

object ModuleParameterVal{
  /*implicit def stringToVAL(stringval:String):ModuleParameterVal={
    val x = VAL()
    x.parseFromJavaYaml(stringval)
    x
  }*/

}

/**
 * VAL are string value, they may be internally stored to save the result of a run but are passed as a full string value
 */
case class VAL() extends ModuleParameterVal {
  var rawValue : String = _
  var resolvedValue : String = _

  override def parseFromJavaYaml(yaml: Any): Unit = {
    if(yaml == null){
      return
    }
    val value = YamlElt.readAs[String](yaml)
    rawValue = value match {
      case Some(theval) => theval
      case None => "Error reading val value (should be a string)"
    }
  }

  override def asString()={
    rawValue
  }

  override def getAttr(attrName: String): ModuleParameterVal = {
    throw new Exception("VAL doesn't have any attributes")
  }
}





case class DIR() extends ModuleParameterVal {
  var rawValue : String = _

  override def parseFromJavaYaml(yaml: Any): Unit = {
    val value = YamlElt.readAs[String](yaml)
    rawValue = value match {
      case Some(theval) => theval
      case None => "Error reading val value (should be a string)"
    }
  }

  override def asString()={
    rawValue
  }

  override def getAttr(attrName: String): ModuleParameterVal = {
    attrName match {
      case "ls" => {
        val x = new LIST[FILE]()
        val file = new java.io.File(rawValue)
        x.parseFromJavaYaml(file.listFiles().foldLeft("")((str,thefile)=>{
          if(thefile.isFile)
            str + " " + thefile.getCanonicalPath
          else
            str
        }))
        x
      }
      case "rls" => {
        val x = new LIST[FILE]()
        val file = new java.io.File(rawValue)
        def recListFiles(folder:java.io.File) :String = {
          folder.listFiles().foldLeft("")((str,thefile)=>{
            if(thefile.isFile)
              str + " " + thefile.getCanonicalPath
            else if(thefile.isDirectory){
              str + recListFiles(thefile)
            }else {
              str
            }
          })
        }
        x.parseFromJavaYaml(recListFiles(file))
        x
      }
    }
  }
}

/**
 * CORPUS is for now a kind of directory object
 * it is representated by its path
 * it displays the following properties :
 * - list : the list of file contained in the corpus/directory
 */
case class CORPUS() extends ModuleParameterVal {
  override def parseFromJavaYaml(yaml: Any): Unit = {

  }

  override def asString()={
    "the corpus root dir path"
  }

  override def getAttr(attrName: String): ModuleParameterVal = {
    throw new Exception("VAL doesn't have any attributes")
  }
}

/**
 * LIST is a list of other paramval
 * it is represented as a string depending on the type of its content :
 * - VAR : quoted variables separated by spaces
 * - CORPUS/FILE : path separated by spaces
 * - MODULE : the yaml list of yaml moduleval instanciation
 */
case class LIST[P <: ModuleParameterVal](implicit manifest: Manifest[P]) extends ModuleParameterVal {
  var list : Seq[P] = _

  def make: P = manifest.runtimeClass.newInstance.asInstanceOf[P]

  override def parseFromJavaYaml(yaml: Any): Unit = {

    list = YamlElt.fromJava(yaml) match {
      case YamlList(list) => {
        var thelist :Seq[P]= Seq[P]()
        list.forEach(new Consumer[Any]() {

          override def accept(t: Any): Unit = {
            val el : P = make
            el.parseFromJavaYaml(t)
            thelist = thelist :+ el
          }
        })
        thelist
      }
      case YamlString(implicitlist) => {
        var thelist :Seq[P]= Seq[P]()
        implicitlist.split("\\s+").foreach(t => {
          val el: P = make
          el.parseFromJavaYaml(t)
          thelist = thelist :+ el
        })
        thelist
      }
      case _ => {
        throw new Exception("error when parsing LIST type")
      }
    }
  }

  override def asString()={
    list.foldLeft("")((str,el) => {
      str +" "+ el.asString()
    })
  }

  override def getAttr(attrName: String): ModuleParameterVal = {
    throw new Exception("VAL doesn't have any attributes")
  }
}

case class MAP() extends ModuleParameterVal{
  var map : Map[String,ModuleParameterVal] = Map[String,ModuleParameterVal]()

  override def parseFromJavaYaml(yaml: Any): Unit = {
      
  }

  override def asString(): String = {
    "???"
  }

  override def getAttr(attrName: String): ModuleParameterVal = ???
}


/**
 * MODULE is a ModuleVal represented by its yaml instanciation
 */
case class MODULE() extends ModuleParameterVal{
  var moduleval : AbstractModuleVal = _

  override def parseFromJavaYaml(yaml: Any): Unit = {
    moduleval = AbstractModuleVal.fromConf(yaml)
  }

  override def asString()={
    "yaml string definition"
  }


  override def getAttr(attrName: String): ModuleParameterVal = {
    throw new Exception("VAL doesn't have any attributes")
  }
}

/**
  * FILE are representated by their file path
  * they display the following properties :
  * - basename : the name of the file without last extension
  * - dirname : the name of its directory
  */
case class FILE() extends ModuleParameterVal {
   var rawValue : String = _

   override def parseFromJavaYaml(yaml: Any): Unit = {
     val value = YamlElt.readAs[String](yaml)
     rawValue = value match {
       case Some(theval) => theval
       case None => "Error reading val value (should be a string)"
     }
   }

   override def asString()={
     rawValue
   }

   override def getAttr(attrName: String): ModuleParameterVal = {
     attrName match {
       case "basename" => {
         val file = new java.io.File(rawValue)
         val extensionIndex = file.getName.lastIndexOf(".")
         if(extensionIndex != -1){
           val x = new VAL()
           x.parseFromJavaYaml(file.getName.substring(0,extensionIndex))
           x
         }else{
           val x = new VAL()
           x.parseFromJavaYaml(file.getName)
           x
         }
       }
       case "filename" => {
         val x = new VAL()
         val file = new java.io.File(rawValue)
         x.parseFromJavaYaml(file.getName)
         x
       }
       case "basedir" => {
         val x = new DIR()
         val file = new java.io.File(rawValue)
         x.parseFromJavaYaml(file.getParent)
         x
       }
     }
   }
 }
