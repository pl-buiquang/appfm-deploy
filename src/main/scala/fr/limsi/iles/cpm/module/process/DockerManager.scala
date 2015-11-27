package fr.limsi.iles.cpm.module.process

import java.io.{File, PrintWriter}
import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.utils.ConfManager
import scala.sys.process._

/**
 * Created by buiquang on 10/6/15.
 */
object DockerManager extends LazyLogging{

  var servicesAvailable = List[String]()

  /**
   * Construct base default image (useless now since using docker is not the default behavior for command run)
   * @return
   */
  def initCheckDefault():Boolean = {
    exist(ConfManager.defaultDockerBaseImage) match {
      case true => true
      case false => {
        build(ConfManager.defaultDockerBaseImage,ConfManager.get("cpm_home_dir") + "/cpm-process-shell")
      }
    }
  }

  /**
   * Run a command in the default base image (useless now since using docker is not the default behavior for command run)
   * @param pid
   * @param name
   * @param host
   * @param port
   * @param cmd
   * @param foldersync
   * @return
   */
  def baseRun(pid:UUID,name:String,host:String,port:String,cmd:String,foldersync:java.io.File) = {
    run(pid,name,host,port,cmd,foldersync,ConfManager.defaultDockerBaseImage)
  }


  def serviceRun(name:String,dockerimage:String,foldersync:java.io.File) = {
    if(!servicesAvailable.exists(_==dockerimage)) {
      try {
        val mount = "-v " + foldersync.getCanonicalPath + ":" + foldersync.getCanonicalPath
        val mount2 = " -v /tmp:/tmp -v " + ConfManager.get("default_result_dir") + ":" + ConfManager.get("default_result_dir") + " -v " + ConfManager.get("default_corpus_dir") + ":" + ConfManager.get("default_corpus_dir") + " "
        val list : java.util.ArrayList[String] = ConfManager.get("modules_dir").asInstanceOf[java.util.ArrayList[String]]
        var mount3 = ""
        val iterator = list.iterator()
        while(iterator.hasNext) {
          val path = iterator.next()
          val file = new File(path)
          if(file.exists()){
            mount3 += " -v "+file.getCanonicalPath+":"+file.getCanonicalPath
          }
        }
        val dockercmd = "docker run " + mount + mount2 + mount3 + " -td --name " + name + " " + dockerimage
        logger.debug(dockerimage)
        dockercmd.!!
        servicesAvailable = name :: servicesAvailable
      } catch {
        case e: Throwable => e.printStackTrace()
      }
    }
  }

  def serviceExec(pid:UUID,name:String,host:String,port:String,cmd:String,foldersync:java.io.File,dockercontainername:String) = {
    val absolutecmd = cmd.replace("\n"," ").replaceAll("^./",foldersync.getCanonicalPath+"/")
    val dockercmd = "docker exec -td "+dockercontainername+" /home/app/bin/cpm-process-shell.py "+pid.toString+" "+name+" "+port+" "+absolutecmd+""
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

  /**
   * Check if a docker image name already exists
   * @param name
   * @return
   */
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

  /**
   * Build a docker image provided a name and a Dockerfile path only if the name is not already taken (image already built)
   * @param name
   * @param path
   * @return
   */
  def build(name:String,path:String) : Boolean= {
    if(!DockerManager.exist(name)) {
      logger.info("Building docker image " + name)
      try {
        val filepath = new java.io.File(path)
        val dir = if (filepath.isDirectory) {
          filepath.getCanonicalPath
        } else {
          filepath.getParent
        }
        val output = ("docker build -t " + name + " " + dir).!
        output == 0
      } catch {
        case e: Throwable => logger.error(e.getMessage); fr.limsi.iles.cpm.utils.Log.error(e); false
      }
    }else{
      true // if the image exists we suppose everything is fine :)
    }
  }

  /**
   * cleanup routing
   * //TODO run this some times
   * @return
   */
  def cleanup():Boolean={
    "docker ps -a -q -f status=exited | xargs docker rm -v".!
    true
  }

}
