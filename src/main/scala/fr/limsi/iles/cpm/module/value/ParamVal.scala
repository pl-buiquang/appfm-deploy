package fr.limsi.iles.cpm.module.value

import java.util.function.Consumer

import fr.limsi.iles.cpm.module.ModuleManager
import fr.limsi.iles.cpm.module.definition.{MAPDef, CMDDef}
import fr.limsi.iles.cpm.module.parameter.AbstractModuleParameter
import fr.limsi.iles.cpm.utils.{YamlMap, YamlString, YamlList, YamlElt}

import scala.collection

/**
 * Abstract base class for param typed values
 * Primitive types are :
 * VAL
 * FILE
 * DIR
 * CORPUS
 * Container types are :
 * MAP
 * LIST
 */
sealed abstract class AbstractParameterVal{
  type MyType <: AbstractParameterVal
  var yamlVal : Any = _
  val _mytype : String

  def fromYaml(yaml:Any):Unit = {
    yamlVal = yaml
    parseYaml(yaml)
  }
  def parseYaml(yaml:Any):Unit
  def asString():String
  override def toString()=asString()

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

  def getAttr(attrName:String):AbstractParameterVal

  def newEmpty():AbstractParameterVal={
    AbstractModuleParameter.createVal(_mytype)
  }
}

object AbstractParameterVal{
  /*implicit def stringToVAL(stringval:String):ModuleParameterVal={
    val x = VAL()
    x.parseFromJavaYaml(stringval)
    x
  }*/

}

/**
 * VAL are string value, they may be internally stored to save the result of a run but are passed as a full string value
 */
case class VAL() extends AbstractParameterVal {
  var rawValue : String = _
  var resolvedValue : String = _
  override val _mytype = "VAL"


  override def parseYaml(yaml: Any): Unit = {
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
    this.rawValue
  }

  override def getAttr(attrName: String): AbstractParameterVal = {
    throw new Exception("VAL doesn't have any attributes")
  }
}


/**
 * FILE are representated by their file path
 * they display the following properties :
 * - basename : the name of the file without last extension
 * - dirname : the name of its directory
 */
case class FILE() extends AbstractParameterVal {
  var rawValue : String = _
  override val _mytype = "FILE"

  override def parseYaml(yaml: Any): Unit = {
    val value = YamlElt.readAs[String](yaml)
    rawValue = value match {
      case Some(theval) => theval
      case None => "Error reading val value (should be a string)"
    }
  }

  override def asString()={
    rawValue
  }

  override def getAttr(attrName: String): AbstractParameterVal = {
    attrName match {
      case "basename" => {
        val file = new java.io.File(rawValue)
        val extensionIndex = file.getName.lastIndexOf(".")
        if(extensionIndex != -1){
          val x = new VAL()
          x.parseYaml(file.getName.substring(0,extensionIndex))
          x
        }else{
          val x = new VAL()
          x.parseYaml(file.getName)
          x
        }
      }
      case "filename" => {
        val x = new VAL()
        val file = new java.io.File(rawValue)
        x.parseYaml(file.getName)
        x
      }
      case "basedir" => {
        val x = new DIR()
        val file = new java.io.File(rawValue)
        x.parseYaml(file.getParent)
        x
      }
    }
  }
}



case class DIR() extends AbstractParameterVal {
  var rawValue : String = _
  override val _mytype = "DIR"

  override def parseYaml(yaml: Any): Unit = {
    val value = YamlElt.readAs[String](yaml)
    rawValue = value match {
      case Some(theval) => theval
      case None => "Error reading val value (should be a string)"
    }
  }

  override def asString()={
    rawValue
  }

  override def getAttr(attrName: String): AbstractParameterVal = {
    attrName match {
      case "ls" => {
        val x = new LIST[FILE]()
        val file = new java.io.File(rawValue)
        x.parseYaml(file.listFiles().foldLeft("")((str,thefile)=>{
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
        x.parseYaml(recListFiles(file))
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
case class CORPUS() extends AbstractParameterVal {
  override val _mytype = "CORPUS"
  override def parseYaml(yaml: Any): Unit = {

  }

  override def asString()={
    "the corpus root dir path"
  }

  override def getAttr(attrName: String): AbstractParameterVal = {
    throw new Exception("VAL doesn't have any attributes")
  }
}

/**
 * MODULE is a ModuleVal represented by its yaml instanciation
 */
case class MODVAL() extends AbstractParameterVal{
  override val _mytype = "MODVAL"
  var moduleval : AbstractModuleVal = _

  override def parseYaml(yaml: Any): Unit = {
    moduleval = AbstractModuleVal.fromConf(yaml)
  }

  override def asString()={
    "yaml string definition"
  }


  override def getAttr(attrName: String): AbstractParameterVal = {
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
case class LIST[P <: AbstractParameterVal](implicit manifest: Manifest[P]) extends AbstractParameterVal {
  override val _mytype = {
    manifest.runtimeClass.getCanonicalName+"*"
  }
  var list : List[P] = _

  def make: P = manifest.runtimeClass.newInstance.asInstanceOf[P]

  override def parseYaml(yaml: Any): Unit = {

    list = YamlElt.fromJava(yaml) match {
      case YamlList(list) => {
        var thelist :List[P]= List[P]()
        list.forEach(new Consumer[Any]() {

          override def accept(t: Any): Unit = {
            val el : P = make
            el.parseYaml(t)
            thelist = thelist :+ el
          }
        })
        thelist
      }
      case YamlString(implicitlist) => {
        var thelist :List[P]= List[P]()
        implicitlist.split("\\s+").foreach(t => {
          val el: P = make
          el.parseYaml(t)
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

  override def getAttr(attrName: String): AbstractParameterVal = {
    throw new Exception("VAL doesn't have any attributes")
  }
}

case class MAP() extends AbstractParameterVal{
  override val _mytype = "MAP"
  var map : Map[String,AbstractParameterVal] = Map[String,AbstractParameterVal]()

  override def parseYaml(yaml: Any): Unit = {

  }

  override def asString(): String = {
    "???"
  }

  override def getAttr(attrName: String): AbstractParameterVal = ???
}


