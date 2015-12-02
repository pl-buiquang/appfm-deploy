package fr.limsi.iles.cpm.corpus

import java.io.{File, FileFilter}
import java.util.function.Consumer

import fr.limsi.iles.cpm.utils.ConfManager
import org.json.{JSONArray, JSONObject}

/**
 * Created by buiquang on 9/7/15.
 */
class CorpusManager {

}

object CorpusManager{

  def jsonExport() = {
    val rootfolder = new java.io.File(ConfManager.get("default_corpus_dir").toString)
    buildJSONTree(rootfolder)
  }

  def buildJSONTree(curFile:java.io.File) :Object={
    if(curFile.isDirectory){
      var folder = new JSONObject()
      var subfiles = new JSONArray()
      val dirs = curFile.listFiles(new FileFilter {
        override def accept(pathname: File): Boolean = {
          pathname.isDirectory
        }
      })
      dirs.toStream.take(20).foreach(file=>{
        subfiles.put(buildJSONTree(file))
      })

      if(dirs.length>20){
        var more = new JSONObject()
        more.put("...","dir")
        subfiles.put(more)
      }

      val files = curFile.listFiles(new FileFilter {
        override def accept(pathname: File): Boolean = {
          pathname.isFile
        }
      })
      files.toStream.take(20).foreach(file=>{
        subfiles.put(buildJSONTree(file))
      })

      if(files.length>20){
        var more = new JSONObject()
        more.put("...","file")
        subfiles.put(more)
      }


      folder.put(curFile.getName,subfiles)
      folder
    }else {
      curFile.getName
    }
  }

  def ls() = {
    val jsontree = jsonExport()
    lsPrint(jsontree,"")
  }

  def lsPrint(json:Object,offset:String) : String={
    var toprint = ""
    if(json.isInstanceOf[String]){
      toprint += json
    }else if(json.isInstanceOf[JSONObject]){
      val jsonobject = json.asInstanceOf[JSONObject]
      toprint += "\n"+offset
      jsonobject.keys().forEachRemaining(new Consumer[String](){
        override def accept(t: String): Unit = {
          toprint += t + ":" + lsPrint(jsonobject.get(t),offset+"  ")
        }
      })
    }else if(json.isInstanceOf[JSONArray]){
      val jsonobject = json.asInstanceOf[JSONArray]
      toprint += "\n"+offset
      jsonobject.forEach(new Consumer[AnyRef](){
        override def accept(t: AnyRef): Unit = {
          toprint +=  ":" + lsPrint(t,offset+"  ")
        }
      })

    }
    toprint
  }

}
