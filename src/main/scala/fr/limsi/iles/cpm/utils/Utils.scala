package fr.limsi.iles.cpm.utils

import java.text.SimpleDateFormat
import java.util.function.Consumer

import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml


/**
 * Created by buiquang on 9/22/15.
 */
object Log {
  val log = com.typesafe.scalalogging.Logger(LoggerFactory.getLogger(""))

  def apply(message:String) = {

    log.info(message)
  }

  def error(e:Throwable)={
    var error = ""
    e.getStackTrace.foreach(el => {
      error += el.toString + "\n"
    })
    log.error("Stack trace : \n"+error)
  }

}

object Utils{
  def getHumanReadableDate(datelong:Long) :String = {
    val date = new java.util.Date(datelong)
    val formatter = new SimpleDateFormat()
    formatter.format(date)
  }

  /**
   * Retrieve a list of space separated arguments in a string (arguments delimited with " or ' are preserved as one)
   * @param line
   * @return
   */
  def getArgumentsFromString(line:String):List[String]={
    """('|")(.*?)\1""".r.replaceAllIn(line,matched => {
      matched.group(2).replaceAll("\\s","_!_SPACE_!_")
    }).split("\\s+").map(item => {
      item.replaceAll("_!_SPACE_!_"," ")
    }).toList
  }


}


abstract class YamlElt
case class YamlList(list:java.util.ArrayList[Any]) extends YamlElt{
  def apply(index:Int)={
    val yel = list.get(index)
    YamlElt.fromJava(yel)
  }
  def toList()= {
    var slist = List[Any]()
    val it = list.iterator()
    while(it.hasNext){
      val el : Any = it.next()
      slist = el :: slist
    }
    slist
  }
}
case class YamlMap(map:java.util.HashMap[String,Any]) extends YamlElt{
  def apply(key:String)={
    val yel = map.get(key)
    YamlElt.fromJava(yel)
  }
}
case class YamlString(value:String) extends YamlElt
case class YamlNull() extends YamlElt
case class YamlUnknown(obj:Any) extends YamlElt

object YamlElt{
  def fromJava(thing:Any) : YamlElt= {
    if(thing!=null){
      if(thing.isInstanceOf[java.util.Map[String,Any]]){
        YamlMap(thing.asInstanceOf[java.util.HashMap[String,Any]])
      }else if(thing.isInstanceOf[java.util.ArrayList[Any]]){
        YamlList(thing.asInstanceOf[java.util.ArrayList[Any]])
      }else if(thing.isInstanceOf[String]){
        YamlString(thing.asInstanceOf[String])
      }else if(thing.isInstanceOf[Boolean]){
        YamlString(thing.asInstanceOf[Boolean].toString)
      }else{
        YamlUnknown(thing)
      }
    }else{
      YamlNull()
    }
  }

  def readAs[T](thing:Any) = {
    if(thing!=null && thing.isInstanceOf[T]){
      Some(thing.asInstanceOf[T])
    }else{
      None
    }
  }

  def testRead(elt:YamlElt,paramName:String) :Unit = {
    elt match {
      case YamlUnknown(el) => Log("Unknown element "+paramName+" : "+el.getClass.getCanonicalName)
      case YamlNull() => Log("Null element found")
      case YamlMap(map) => {
        val keys = map.keySet()
        val it = keys.iterator()
        while(it.hasNext){
          val el = it.next()
          testRead(YamlElt.fromJava(map.get(el)),paramName+"."+el)
        }
      }
      case YamlList(array) => {
        val it = array.iterator()
        var index = 0
        while(it.hasNext){
          val el = it.next()
          testRead(YamlElt.fromJava(el),paramName+"["+index+"]")
          index+=1
        }
      }
      case YamlString(value) => Log(paramName+" = "+value)
    }
  }
}

