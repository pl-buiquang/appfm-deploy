package fr.limsi.iles.cpm.service


import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.module.parameter.AbstractModuleParameter
import fr.limsi.iles.cpm.process.{CMDProcess, DockerManager, RunEnv}
import fr.limsi.iles.cpm.module.value._
import fr.limsi.iles.cpm.process.DockerManager._
import fr.limsi.iles.cpm.utils.{ConfManager, YamlElt, YamlMap}
import org.json.JSONObject

import scala.sys.process._

/**
  * Created by paul on 3/30/16.
  */
class Service(val definitionPath:String,
              val name:String,
              val desc:String,
              val outputs:Map[String,AbstractModuleParameter],
              var startcmd:CMDVal,
              var stopcmd:Option[CMDVal]
             ) extends LazyLogging{

  private var _isRunning : Boolean = false
  private var runningContainer : Option[String] = None

  def getDefDir = {
    (new java.io.File(definitionPath)).getParent
  }

  private def initEnv : RunEnv = {
    val env = new RunEnv(Map[String,AbstractParameterVal]())
    val resdir = DIR(None,None)
    resdir.fromYaml(ConfManager.get("default_result_dir"))
    env.setVar("_RESULT_DIR",resdir)
    val corpusdir = DIR(None,None)
    corpusdir.fromYaml(ConfManager.get("default_corpus_dir"))
    env.setVar("_CORPUS_DIR",corpusdir)
    val defdir = DIR(None,None)
    defdir.fromYaml(getDefDir)
    env.setVar("_DEF_DIR",defdir)
    val maxthreads = VAL(None,None)
    maxthreads.fromYaml(ConfManager.get("maxproc"))
    env.setVar("_MAX_THREADS",maxthreads)
    env
  }

  def start():Boolean={
    val env = initEnv
    if(startcmd.needsDocker()){
      val defdir = getDefDir
      val imagename = startcmd.inputs("DOCKERFILE").toYaml() match {
        case x :String => {
          if(x!="false"){
            // replace @ by "_at_" (docker doesn't accept @ char)
            val dockerfile = new java.io.File(defdir+"/"+x)
            val dockerfilename = if (dockerfile.exists()){
              dockerfile.getName
            }else{
              "Dockerfile"
            }
            val name = DockerManager.nameToDockerName("service-"+this.name+"-"+dockerfilename) // _MOD_CONTEXT should always be the module defintion that holds this command
            if(!DockerManager.exist(name)){
              DockerManager.build(name,defdir+"/"+dockerfilename)
            }
            name
          }else{
            ""
          }
        }
        case _ =>  ""
      }
      val mount = "-v " + defdir + ":" + defdir
      val dockercmd = "docker run "+ env.resolveValueToString(startcmd.inputs("DOCKER_OPTS").asString()) +" "+ mount  + " -td " + imagename
      Thread.sleep(2000)
      logger.info("sleeping for 2secondes. TODO !! Fix delay needed for possible dockerized server initialization time... :(")
      //logger.debug(dockerimage)
      logger.info(dockercmd)
      val containername = dockercmd.!!.trim()
      runningContainer = Some(containername)
    }else{
      runNonDocker(env.resolveValueToString(stopcmd.get.inputs("CMD").asString()))
    }
    _isRunning = true
    true
  }

  def stop():Boolean={
    val env = initEnv
    if(startcmd.needsDocker()){
      if(runningContainer.isDefined){
        DockerManager.removeService(runningContainer.get)
      }
    }
    if(stopcmd.isDefined){
      runNonDocker(env.resolveValueToString(stopcmd.get.inputs("CMD").asString()))
    }
    _isRunning = false
    true
  }

  private def runNonDocker(cmd:String) = {
    val env = initEnv
    val absolutecmd = cmd.replace("\n"," ").replaceAll("^\\./",getDefDir+"/")
    val cmdtolaunch = "python "+ConfManager.get("cpm_home_dir")+"/"+ConfManager.get("shell_exec_bin")+" "+absolutecmd

    Process(cmdtolaunch,new java.io.File(getDefDir)) !
  }

  def isRunning():Boolean={
    _isRunning
  }

  def toJson : JSONObject = {
    val json = new JSONObject()
    json.put("name",name)
    json.put("desc",desc)
    json.put("status",_isRunning)
    val outputsjson = new JSONObject()
    outputs.foreach(output=>{
      outputsjson.put(output._1,output._2.toJson)
    })
    json.put("outputs",outputsjson)
    json
  }

}


object Service{
  def initCMD(conf:Any,name:String):CMDVal={
    val inputs : java.util.Map[String,Any] = conf match {
      case YamlMap(map) => map
      case _ => throw new Exception("malformed module value")
    }
    CMDVal(name,Some(inputs))
  }
}
