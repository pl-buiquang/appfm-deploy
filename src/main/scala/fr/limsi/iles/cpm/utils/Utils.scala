package fr.limsi.iles.cpm.utils

import java.io.{FilenameFilter, File, FileFilter}
import java.nio.file.DirectoryStream.Filter
import java.nio.file.{Path, FileSystems, Files}
import java.text.SimpleDateFormat
import java.util
import java.util.function.{BiConsumer, Consumer}

import com.typesafe.scalalogging.LazyLogging
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml

import org.json._

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

object Utils extends LazyLogging{

  def lsDir(curFilepath:String,from:Int) : Object = {
    var jsonserial = new JSONArray();
    val curfilepathnormalized = if(!curFilepath.endsWith("/")){
      curFilepath + "/"
    }else{
      curFilepath
    }
    val curFile = new java.io.File(curfilepathnormalized)
    val files = curFile.listFiles()
    files.sortWith((fileA,fileB)=>{
      fileA.isDirectory && fileB.isFile || fileA.isDirectory && fileB.isDirectory && fileA.getName < fileB.getName ||  fileB.isFile && fileA.isFile && fileA.getName < fileB.getName
    }).toStream.drop(from).take(20).foreach(file=>{
      var fileobj : Object = null;
      if(file.isDirectory){
        var dir = new JSONObject()
        var emptyls = new JSONArray()
        var more = new JSONObject()
        more.put("...",0)
        emptyls.put(more)
        dir.put(file.getName,emptyls)
        fileobj = dir
      }else{
        fileobj = file.getName
      }
      jsonserial.put(fileobj)
    })

    if(files.length>20+from){
      var more = new JSONObject()
      more.put("...",from+20)
      jsonserial.put(more)
    }
    jsonserial
  }

  def deleteDirectory(dir:java.io.File) : Boolean = {
    if(dir.isDirectory){
      var deleted = true
      dir.listFiles().foreach(child => {
        deleted = deleteDirectory(child) && deleted
      })
      if(deleted){
        deleted = dir.delete() && deleted
      }
      deleted
    }else{
      dir.delete()
    }
  }

  def deleteFile(filepath:String) : Boolean = {
    try{
      val file = new java.io.File(filepath)
      file.delete()
    }catch{
      case e:Throwable => logger.warn("couldn't delete "+filepath); false
    }
  }

  def scalaMap2JavaMap(map:Map[String,Any]):java.util.Map[String,Any]={
    var javamap = new java.util.HashMap[String,Any]()
    map.foreach(elt => {
      javamap.put(elt._1,elt._2)
    })
    javamap
  }

  def getHumanReadableDate(datelong:Long) :String = {
    val date = new java.util.Date(datelong)
    val formatter = new SimpleDateFormat()
    formatter.format(date)
  }

  def addOffset(offset:String,content:String)={
    val splitted = content.split("\n")
    var retstring = splitted(0)
    var i = 1;
    while(i<splitted.length){
      retstring += "\n"+offset+splitted(i)
      i+=1
    }
    retstring
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

  def ensureTrailingSlash(dirname:String):String={
    val removedDoubleSlash = if(dirname.startsWith("//")){
      dirname.substring(1)
    }else{
      dirname
    }
    if(removedDoubleSlash.endsWith("/")){
      removedDoubleSlash
    }else{
      removedDoubleSlash+"/"
    }
  }


}


abstract class YamlElt{
  def toJSONObject() : AnyRef= {
    this match {
      case YamlList(list) => {
        var x = new JSONArray()
        var index = 0
        list.forEach(new Consumer[Any] {
          override def accept(t: Any): Unit = {
            x.put(index,YamlElt.fromJava(t).toJSONObject());
            index += 1
          }
        })
        x
      }
      case YamlMap(map)=>{
        var x = new JSONObject()
        map.forEach(new BiConsumer[String,Any] {
          override def accept(t: String, u: Any): Unit = {
            x.put(t,YamlElt.fromJava(u).toJSONObject())
          }
        })
        x
      }
      case YamlString(string)=>{
        string
      }
      case YamlUnknown(thing) => {
        null
      }
      case YamlNull() => {
        null
      }
    }
  }

  def fromJSONObject(json:JSONObject):YamlElt={
    val keys = json.keys()
    var map = new java.util.HashMap[String,Any]()
    while(keys.hasNext){
      val key = keys.next()
      map.put(key,json.get(key))
    }
    YamlElt.fromJava(map)
  }
}
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
        val content = thing.asInstanceOf[String]
        if(content.contains('\n')){
          val yaml = new Yaml()
          var retEl : YamlElt = null
          try{
            val confMap = yaml.load(content)
            retEl = YamlElt.fromJava(confMap)
          }catch {
            case e:Throwable => retEl = YamlString(content)
          }
          retEl
        }else{
          YamlString(thing.asInstanceOf[String])
        }
      }else if(thing.isInstanceOf[Boolean]){
        YamlString(thing.asInstanceOf[Boolean].toString)
      }else if(thing.isInstanceOf[Integer]){
        YamlString(thing.asInstanceOf[Integer].toString)
      }else{
        YamlUnknown(thing.toString)
      }
    }else{
      YamlNull()
    }
  }

  def readAs[T](thing:Any) :Option[T]= {
    val bugfix = if(thing!=null && thing.isInstanceOf[Boolean]){
      thing.asInstanceOf[Boolean].toString
    }else{
      thing
    }

    if(bugfix!=null && bugfix.isInstanceOf[T]){
      Some(bugfix.asInstanceOf[T])
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

