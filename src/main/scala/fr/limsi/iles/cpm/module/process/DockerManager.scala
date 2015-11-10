package fr.limsi.iles.cpm.module.process

import java.io.PrintWriter
import java.util.UUID

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


  def baseRun(pid:UUID,name:String,host:String,port:String,cmd:String,foldersync:java.io.File) = {
    run(pid,name,host,port,cmd,foldersync,ConfManager.defaultDockerBaseImage)
  }

  def serviceRun(name:String,dockerimage:String,foldersync:java.io.File) = {
    try{
      val mount = "-v "+foldersync.getCanonicalPath+":"+foldersync.getCanonicalPath
      val mount2 = " -v /tmp:/tmp -v "+ConfManager.get("default_result_dir")+":"+ConfManager.get("default_result_dir")+" -v "+ConfManager.get("default_corpus_dir")+":"+ConfManager.get("default_corpus_dir")+" "
      val dockercmd = "docker run "+mount+mount2+" -td --name "+name+" "+dockerimage
      logger.debug(dockercmd)
      dockercmd.!!
    }catch {
      case e:Throwable => e.printStackTrace()
    }
  }

  def serviceExec(pid:UUID,name:String,host:String,port:String,cmd:String,foldersync:java.io.File,dockerimage:String) = {
    val absolutecmd = cmd.replace("\n"," ").replaceAll("^./",foldersync.getCanonicalPath+"/")
    val dockercmd = "docker exec -td "+dockerimage+" /home/app/bin/cpm-process-shell.py "+pid.toString+" "+name+" "+port+" "+absolutecmd+""
    logger.debug(dockercmd)
    dockercmd.!!
    "true"
  }

  def run(pid:UUID,name:String,host:String,port:String,cmd:String,foldersync:java.io.File,dockerimage:String) = {
    val mount = "-v "+foldersync.getCanonicalPath+":"+foldersync.getCanonicalPath
    val mount2 = " -v /tmp:/tmp -v "+ConfManager.get("default_result_dir")+":"+ConfManager.get("default_result_dir")+" -v "+ConfManager.get("default_corpus_dir")+":"+ConfManager.get("default_corpus_dir")+" "
    val absolutecmd = cmd.replace("\n"," ").replace("\"","\\\"").replaceAll("^./",foldersync.getCanonicalPath+"/")
    val dockercmd = "docker run "+mount+mount2+" -td "+dockerimage+" "+pid.toString+" "+name+" "+port+" "+absolutecmd+""
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
    logger.info("Building docker image "+name)
    try {
      val filepath = new java.io.File(path)
      val dir = if(filepath.isDirectory){
        filepath.getCanonicalPath
      }else{
        filepath.getParent
      }
      val output = ("docker build -t " + name + " " + dir).!
      output==0
    } catch {
      case e:Throwable => logger.error(e.getMessage); fr.limsi.iles.cpm.utils.Log.error(e); false
    }
  }

  def cleanup():Boolean={
    "docker rm -v $(docker ps -a -q -f status=exited)".!
    true
  }

}
