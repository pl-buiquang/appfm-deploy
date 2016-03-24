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

  /**
    * This map store the dockerized service availables
    * Multiple named instances can be available indicating if they are busy or not
    */
  var servicesAvailable = Map[String,scala.collection.mutable.Map[String,Boolean]]()
  val maxProcess : Int = Integer.valueOf(ConfManager.get("maxproc").toString)
  var nonDockerProcRunning = 0
  var runOnlyOneProcessAtATime : Object = new Object()

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


  def serviceRun(dockerimage:String,foldersync:java.io.File,docker_opts:String,unique:Boolean) : String= {
    val nbcontainers = getNbContainers(dockerimage)
    val availableContainers = findAvailableContainers(dockerimage)
    if (nbcontainers < maxProcess || unique){
      createContainer(dockerimage,foldersync,docker_opts)
    }else if(availableContainers.size>0){
      availableContainers(0)
    }else{
      if(nbcontainers >= maxProcess){
        cleanUnusedContainer()
      }
      createContainer(dockerimage,foldersync,docker_opts)
    }
  }

  /**
    * @todo reattach to exited container..
    * @param dockerimage
    * @param foldersync
    * @param docker_opts
    * @return
    */
  def createContainer(dockerimage:String,foldersync:java.io.File,docker_opts:String) : String = {
    try {
      val mount = "-v " + foldersync.getCanonicalPath + ":" + foldersync.getCanonicalPath
      val mount2 = " -v /tmp:/tmp -v " + ConfManager.get("default_result_dir") + ":" + ConfManager.get("default_result_dir") + " -v " + ConfManager.get("default_corpus_dir") + ":" + ConfManager.get("default_corpus_dir") + " "
      var mount3 = ""
      /*val list : java.util.ArrayList[String] = ConfManager.get("modules_dir").asInstanceOf[java.util.ArrayList[String]]
      val iterator = list.iterator()
      while(iterator.hasNext) {
        val path = iterator.next()
        val file = new File(path)
        if(file.exists()){
          mount3 += " -v "+file.getCanonicalPath+":"+file.getCanonicalPath
        }
      }*/
      val dockercmd = "docker run "+docker_opts +" "+ mount + mount2 + mount3 + " -td " + dockerimage
      Thread.sleep(2000)
      logger.info("sleeping for 2secondes. TODO !! Fix delay needed for possible dockerized server initialization time... :(")
      logger.debug(dockerimage)
      logger.info(dockercmd)
      val containername = dockercmd.!!.trim()
      updateServiceStatus(Some(containername),Some(dockerimage),true)
      containername
    } catch {
      case e: Throwable => logger.error(e.getMessage); throw new Exception("error when trying to run service "+dockerimage+" with name . Error Message : "+e.getMessage)
    }
  }


  def serviceExec(pid:UUID,name:String,host:String,port:String,cmd:String,foldersync:java.io.File,imagename:String,workingdir:java.io.File,dockeropts:String,unique:Boolean) : String= {
    while (getNbProcessRunning() >= maxProcess){
      Thread.sleep(1000)
    }

    runOnlyOneProcessAtATime.synchronized{
      val containerName = DockerManager.serviceRun(imagename,foldersync,dockeropts,unique)
      val absolutecmd = cmd.replace("\n"," ").replaceAll("^\\./",foldersync.getCanonicalPath+"/")
      val dockercmd = "docker exec -td "+containerName+" /home/pshell/bin/cpm-process-shell.py true "+pid.toString+" "+name+" "+port+" "+workingdir.getCanonicalPath+" "+absolutecmd
      logger.info(dockercmd)
      dockercmd.!!
      containerName
    }
  }

  /**
   * Get a Map of docker containers
   * @param onlyRunning
   * @return a map keyed by docker container names into a tuple consisting of container id and status
   */
  def getContainers(onlyRunning:Boolean=false):Map[String,(String,String)]={
    val containerslstcmd  = ("docker ps "+ {if(onlyRunning){""}else{"-a "}}+""" --format "{{.Names}}\t{{.ID}}\t{{.Status}}"""")
    try{
      val containerslst = containerslstcmd.!!
      containerslst.split("\n").map(item => {
        val info = item.stripMargin('"').split("\t")
        (info(0).trim(),(info(1).trim(),info(2).trim()))
      }).toMap
    }catch{
      case e:Throwable => logger.error(containerslstcmd+" : "+e.getMessage); Map[String,(String,String)]()
    }
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
            dockerfile = filepath.getCanonicalPath+"/Dockerfile"
            filepath.getCanonicalPath
          } else {
            filepath.getParent
          }
          val buildcmd = ("docker build -t " + name + " -f " + dockerfile + " " + dir)
          logger.info("Docker : "+buildcmd)
          val output = buildcmd.!
          output == 0
        } catch {
          case e: Throwable => logger.error(e.getMessage); fr.limsi.iles.cpm.utils.Log.error(e); false
        }
      }else{
        true // if the image exists we suppose everything is fine :)
      }
    }
  }

  def updateServiceStatus(containerName:Option[String],imageName:Option[String],status:Boolean) = {
    if (containerName.isDefined && imageName.isDefined){
      this.servicesAvailable.synchronized{
        if(!this.servicesAvailable.exists(_._1 == imageName.get)){
          var containers = scala.collection.mutable.Map[String,Boolean]()
          containers += (containerName.get -> status)
          this.servicesAvailable += (imageName.get -> containers)
        }else if(!(this.servicesAvailable(imageName.get)).exists(_._1 == containerName.get)){
          this.servicesAvailable(imageName.get) += (containerName.get -> status)
        }else{
          (this.servicesAvailable(imageName.get))(containerName.get) = status
        }
      }
    }
  }

  def findAvailableContainers(imagename:String) : List[String]={
    this.servicesAvailable.synchronized{
      if(this.servicesAvailable.exists(_._1 == imagename)){
        this.servicesAvailable(imagename).foldLeft(List[String]())((list,container)=>{
          if(!container._2){
            container._1 :: list
          }else{
            list
          }
        })
      }else{
        List[String]()
      }
    }
  }

  def getNbContainers(imageName:String):Int = {
    this.servicesAvailable.synchronized{
      this.servicesAvailable.filter(image=>image._1==imageName).foldLeft(0)((count,image)=>{
        image._2.foldLeft(0)((subcount,container)=>{
          subcount + 1
        }) + count
      })
    }
  }

  def getNbProcessRunning() : Int = {
    this.servicesAvailable.synchronized{
      this.servicesAvailable.foldLeft(0)((count,image)=>{
        image._2.foldLeft(0)((subcount,container)=>{
          if (container._2){
            subcount + 1
          }else{
            subcount
          }
        }) + count
      }) + this.nonDockerProcRunning
    }
  }

  def cleanUnusedContainer()={
    this.servicesAvailable.synchronized{
      var imageToRemove = List[String]()
      this.servicesAvailable.foreach(image=>{

        var removed = List[String]()
        image._2.foreach(container=>{
          if (!container._2){
            removeService(container._1)
            removed ::= container._1
          }
        })
        if (removed.size == image._2.size){
          imageToRemove ::= image._1
        }else{
          removed.foreach(item => {
            image._2 -= item
          })
        }
      })
      imageToRemove.foreach(item=>{
        this.servicesAvailable -= item
      })
    }
  }


  def removeService(name:String)={
    val kill = "docker kill "+name
    val rm = "docker rm "+name
    kill.!!
    rm.!!
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
      servicename._2.foreach(containername => {
        ("docker kill "+containername._1)!
      })

    })
    "docker ps -a -q -f status=exited" #| "xargs docker rm -v" ! ;
    true
  }

}
