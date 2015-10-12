package fr.limsi.iles.cpm.process

import fr.limsi.iles.cpm.utils.YamlElt



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

  def getAttr(attrName:String):String

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

  override def getAttr(attrName: String): String = {
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

  override def getAttr(attrName: String): String = {
    attrName match {
      case "basename" => {
        val file = new java.io.File(rawValue)
        val extensionIndex = file.getName.lastIndexOf(".")
        if(extensionIndex != -1){
          file.getName.substring(0,extensionIndex)
        }else{
          file.getName
        }
      }
      case "filename" => {
        val file = new java.io.File(rawValue)
        file.getName;
      }
      case "basedir" => {
        val file = new java.io.File(rawValue)
        file.getParent
      }
    }
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

  override def getAttr(attrName: String): String = {
    attrName match {
      case "ls" => {
        val file = new java.io.File(rawValue)
        file.getName;
      }
      case "rls" => {
        val file = new java.io.File(rawValue)
        file.getParent
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

  override def getAttr(attrName: String): String = {
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
case class LIST[L]() extends ModuleParameterVal {
  type L <: AModuleParameter
  override def parseFromJavaYaml(yaml: Any): Unit = {

  }

  override def asString()={
    "comma separated or yaml string definition"
  }

  override def getAttr(attrName: String): String = {
    throw new Exception("VAL doesn't have any attributes")
  }
}

/**
 * MODULE is a ModuleVal represented by its yaml instanciation
 */
case class MODULE() extends ModuleParameterVal{
  override def parseFromJavaYaml(yaml: Any): Unit = {

  }

  override def asString()={
    "yaml string definition"
  }


  override def getAttr(attrName: String): String = {
    throw new Exception("VAL doesn't have any attributes")
  }
}

abstract class AModuleParameter{
  type T <: ModuleParameterVal

  var value : Option[T] = None
  var paramType : String

  def setVal(yaml:Any,theval:T)={
    value = Some(theval)
    value.get.parseFromJavaYaml(yaml)
  }
}

class ModuleParameter[U <: ModuleParameterVal](theparamType:String,val desc:Option[String],  val format:Option[String], val schema:Option[String], defaultval : Option[U]) extends AModuleParameter{
  override type T = U
  override var paramType: String = theparamType
  value = defaultval

  def this(theparamType:String,desc:Option[String],format:Option[String],schema:Option[String]) = this(theparamType,desc,format,schema,None)
}


