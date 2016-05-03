package fr.limsi.iles.cpm.service


import fr.limsi.iles.cpm.module.parameter.AbstractModuleParameter
import fr.limsi.iles.cpm.process.{DockerManager, RunEnv}
import fr.limsi.iles.cpm.module.value.{FILE, AbstractModuleVal, AbstractParameterVal}
import fr.limsi.iles.cpm.utils.ConfManager
import org.json.JSONObject
import scala.sys.process._

/**
  * Created by paul on 3/30/16.
  */
class Service(val definitionPath:String,
              val name:String,
              val desc:String,
              val outputs:Map[String,AbstractModuleParameter],
              var startcmd:java.util.Map[String,String],
              var stopcmd:java.util.Map[String,String]
             ) {

  private var _isRunning : Boolean = false

  def start():Boolean={
    /*val env = new RunEnv(Map[String,AbstractParameterVal]())
    val datadir = FILE(None,None)
    datadir.fromYaml(ConfManager.get("default_result_dir").toString)
    env.setVar("_DATA_DIR",datadir)

    val dockerfileinput = env.resolveValueToString(startcmd.get("DOCKERFILE"))

    val defdir = (new java.io.File(this.definitionPath)).getParent
    val dockerfile = new java.io.File(defdir+"/"+dockerfileinput)
    val dockerfilename = if (dockerfile.exists()){
      dockerfile.getName
    }else{
      "Dockerfile"
    }
    val name = DockerManager.nameToDockerName("appfm-service-"+this.name) // _MOD_CONTEXT should always be the module defintion that holds this command
    if(!DockerManager.exist(name)){
      DockerManager.build(name,defdir+"/"+dockerfilename)
    }

    val dockeropts = env.resolveValueToString(startcmd.get("DOCKER_OPTS"))
    val dockercmd = "docker run "+dockeropts + " -td " + name
    dockercmd.!!*/
    _isRunning = true
    true
  }

  def stop():Boolean={
    _isRunning = false
    true
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

}
