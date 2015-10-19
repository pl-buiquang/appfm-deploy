package fr.limsi.iles.cpm.module.process

import java.io.PrintWriter

import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.utils.ConfManager
import scala.sys.process._

/**
 * Created by buiquang on 10/6/15.
 */
object DockerManager extends LazyLogging{

  def initCheckDefault():Boolean = {
    exist(ConfManager.defaultDockerBaseImage) match {
      case true => true
      case false => {
        build(ConfManager.defaultDockerBaseImage,ConfManager.get("cpm_home_dir") + "/cpm-process-shell")
      }
    }
  }


  def baseRun(name:String,host:String,port:String,cmd:String,foldersync:java.io.File) = {
    run(name,host,port,cmd,foldersync,ConfManager.defaultDockerBaseImage)
  }

  def run(name:String,host:String,port:String,cmd:String,foldersync:java.io.File,dockerimage:String) = {
    val mount = "-v "+foldersync.getCanonicalPath+":"+foldersync.getCanonicalPath
    val mount2 = " -v /tmp:/tmp -v "+ConfManager.get("default_result_dir")+":"+ConfManager.get("default_result_dir")+" -v "+ConfManager.get("default_corpus_dir")+":"+ConfManager.get("default_corpus_dir")+" "
    val absolutecmd = cmd.replace("\n"," ").replace("\"","\\\"").replaceAll("^./",foldersync.getCanonicalPath+"/")
    val dockercmd = "docker run "+mount+mount2+" -td "+dockerimage+" "+name+" "+port+" "+absolutecmd+""
    logger.debug(dockercmd)
    dockercmd.!!
  }

  def exist(name:String):Boolean={
    try {
      val images = "docker images".!!
      val imagelst = images.split("\n")
      if(!imagelst.exists(image => {
        val imageprops = image.split("\\s+")
        imageprops(0) == name
      })) {
        false

      }else{
        true
      }
    }catch {
      case e:Throwable =>  logger.error(e.getMessage); fr.limsi.iles.cpm.utils.Log.error(e); false
    }
  }


  def build(name:String,path:String) : Boolean= {
    try {
      val filepath = new java.io.File(path)
      val dir = if(filepath.isDirectory){
        filepath.getCanonicalPath
      }else{
        filepath.getParent
      }
      val output = ("docker build -t " + name + " " + dir).!!
      logger.info(output)
      true
    } catch {
      case e:Throwable => logger.error(e.getMessage); fr.limsi.iles.cpm.utils.Log.error(e); false
    }
  }

}
