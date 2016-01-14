package fr.limsi.iles.cpm.corpus

import java.io.{File, FileFilter}
import java.nio.file.DirectoryStream.Filter
import java.nio.file._
import java.util.function.Consumer

import fr.limsi.iles.cpm.utils.ConfManager
import org.json.{JSONArray, JSONObject}

/**
 * Created by buiquang on 9/7/15.
 */
class CorpusManager {

}

object CorpusManager{

  var cachelist : JSONObject = null

  def jsonExport(reload:Boolean)(implicit onlyInputs : Boolean = true) = {
    if(cachelist == null || reload){
      val corpusrootfolder = new java.io.File(ConfManager.get("default_corpus_dir").toString)
      val corpustree = buildJSONTree(corpusrootfolder)

      val mastertree = new JSONObject();

      if(!onlyInputs){
        val resultsrootfolder = new java.io.File(ConfManager.get("default_result_dir").toString)
        val resulttree = buildJSONTree(resultsrootfolder)
        mastertree.put("results",resulttree.asInstanceOf[JSONObject].get(resulttree.asInstanceOf[JSONObject].keys().next()))
      }

      mastertree.put("corpus",corpustree.asInstanceOf[JSONObject].get(corpustree.asInstanceOf[JSONObject].keys().next()))
      cachelist = mastertree
    }
    cachelist
  }



  def buildJSONTree(curFile:java.io.File) :Object={
    var maxFolder = 20
    var maxFile = 20

    if(curFile.isDirectory){
      var folder = new JSONObject()
      var subfiles = new JSONArray()

      val dirStream = Files.newDirectoryStream(FileSystems.getDefault.getPath(curFile.getCanonicalPath),new Filter[Path] {
        override def accept(entry: Path): Boolean = {
          entry.toFile.isDirectory
        }
      })
      val dirit = dirStream.iterator()
      while(dirit.hasNext && maxFolder > 0){
        subfiles.put(buildJSONTree(dirit.next().toFile))
        maxFolder-=1
      }
      if(dirit.hasNext){
        var more = new JSONObject()
        more.put("...","dir")
        subfiles.put(more)
      }

      dirStream.close()

      val fileStream = Files.newDirectoryStream(FileSystems.getDefault.getPath(curFile.getCanonicalPath),new Filter[Path] {
        override def accept(entry: Path): Boolean = {
          entry.toFile.isFile
        }
      })
      val fileit = fileStream.iterator()
      while(fileit.hasNext && maxFile > 0){
        subfiles.put(buildJSONTree(fileit.next().toFile))
        maxFile-=1
      }
      if(fileit.hasNext){
        var more = new JSONObject()
        more.put("...","file")
        subfiles.put(more)
      }

      fileStream.close()

      /*
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
      */
      folder.put(curFile.getName,subfiles)
      folder
    }else {
      curFile.getName
    }
  }

  def ls() = {
    val jsontree = jsonExport(false)
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
