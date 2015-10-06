package fr.limsi.iles.cpm.process

import fr.limsi.iles.cpm.utils.YamlElt

import scala.reflect.ClassTag



sealed abstract class ModuleParameterVal{
  def parseFromJavaYaml(yaml:Any):Unit
  def asString():String
  def extractVariables() : Array[String]={
    val value = asString()
    val complexVars = """\$\{(.+?)\}""".r.findAllMatchIn(value)
    val simpleVars = """\$([a-zA-Z_\-]+)""".r.findAllMatchIn(value)
    var vars = Array[String]()
    while(complexVars.hasNext){
      vars :+= complexVars.next().group(1)
    }
    vars
  }

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
}

/**
 * LIST is a list of other paramval
 * it is represented as a string depending on the type of its content :
 * - VAR : quoted variables separated by spaces
 * - CORPUS/FILE : path separated by spaces
 * - MODULE : the yaml list of yaml moduleval instanciation
 */
case class LIST() extends ModuleParameterVal {
  type L <: AModuleParameter
  override def parseFromJavaYaml(yaml: Any): Unit = {

  }

  override def asString()={
    "comma separated or yaml string definition"
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


