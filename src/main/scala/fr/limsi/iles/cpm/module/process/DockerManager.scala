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
        build(ConfManager.defaultDockerBaseImage,ConfManager.get("cpm_home_dir") +"/"+ ConfManager.get("process-shell-home").toString)
      }
    }
  }

  /**
   * Run a docker image under a specified name
   * @todo unduplicate checking of available services (servicesAvailable and getContainer docker command result)
   * @param name
   * @param dockerimage
   * @param foldersync
   */
  def serviceRun(name:String,dockerimage:String,foldersync:java.io.File,docker_opts:String) = {
    if(!servicesAvailable.exists(_==name)) {
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
        val dockercmd = "docker run "+docker_opts +" "+ mount + mount2 + mount3 + " -td --name " + name + " " + dockerimage
        logger.debug(dockerimage)
        val existingcontainerstatus = getContainers().getOrElse(name,("",""))
        if(existingcontainerstatus._2 == ""){
          dockercmd.!!
          servicesAvailable = name :: servicesAvailable
        }else if(existingcontainerstatus._2.startsWith("Exited")){
          ("docker rm "+existingcontainerstatus._1).!!
          dockercmd.!!
          if (!servicesAvailable.exists(_ == name)){
            servicesAvailable = name :: servicesAvailable
          }
        }else{
          logger.info("image "+dockerimage+" with name "+name+" already exists with status : "+existingcontainerstatus._2)
        }
      } catch {
        case e: Throwable => logger.error(e.getMessage); throw new Exception("error when trying to run service "+dockerimage+" with name "+name+". Error Message : "+e.getMessage)
      }
    }
  }

  /**
   * Get a Map of docker containers
   * @param onlyRunning
   * @return a map keyed by docker container names into a tuple consisting of container id and status
   */
  def getContainers(onlyRunning:Boolean=false):Map[String,(String,String)]={
    val containerslst  = ("docker ps "+ {if(onlyRunning){""}else{"-a "}}+""" --format "{{.Names}}\t{{.ID}}\t{{.Status}}"""").!!
    containerslst.split("\n").map(item => {
      val info = item.stripMargin('"').split("\t")
      (info(0).trim(),(info(1).trim(),info(2).trim()))
    }).toMap
  }

  def serviceExec(pid:UUID,name:String,host:String,port:String,cmd:String,foldersync:java.io.File,dockercontainername:String,workingdir:java.io.File) = {
    val absolutecmd = cmd.replace("\n"," ").replaceAll("^\\./",foldersync.getCanonicalPath+"/")
    val dockercmd = "docker exec -td "+dockercontainername+" /home/pshell/bin/cpm-process-shell.py true "+pid.toString+" "+name+" "+port+" "+workingdir.getCanonicalPath+" "+absolutecmd
    logger.info(dockercmd)
    dockercmd.!!
    "true"
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
    this.synchronized { // this is to prevent multiple concurrent building of the same image...
      if(!DockerManager.exist(name)) {
        logger.info("Building docker image " + name)
        try {
          val filepath = new java.io.File(path)
          var dockerfile = filepath.getCanonicalPath
          val dir = if (filepath.isDirectory) {
            filepath.getCanonicalPath
            dockerfile = filepath.getCanonicalPath+"/Dockerfile"
          } else {
            filepath.getParent
          }
          val output = ("docker build -t " + name + " -f " + dockerfile + " " + dir).!
          output == 0
        } catch {
          case e: Throwable => logger.error(e.getMessage); fr.limsi.iles.cpm.utils.Log.error(e); false
        }
      }else{
        true // if the image exists we suppose everything is fine :)
      }
    }
  }

  /**
   * Transform a name into correct docker repository regex name
   * @param name
   * @return
   */
  def nameToDockerName(name:String) : String = {
    name.replace("@","-at-").replace("_","--").replace("#","-id-").toLowerCase()
  }

  /**
   * cleanup routing
   * //TODO run this some times
   * @return
   */
  def cleanup():Boolean={
    servicesAvailable.foreach(servicename => {
      ("docker kill "+servicename)!
    })
    "docker ps -a -q -f status=exited" #| "xargs docker rm -v" ! ;
    true
  }

}
