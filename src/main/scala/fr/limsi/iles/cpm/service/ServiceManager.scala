package fr.limsi.iles.cpm.service

import java.io.FileInputStream

import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.module.definition.ModuleDef
import fr.limsi.iles.cpm.module.parameter.AbstractModuleParameter
import fr.limsi.iles.cpm.module.value.AbstractParameterVal
import org.json.JSONArray
import org.yaml.snakeyaml.Yaml

/**
  * Created by paul on 3/30/16.
  */
object ServiceManager extends LazyLogging{

  var services : Map[String,Service]= Map[String,Service]()

  override def toString : String={
    services.foldLeft(new JSONArray())((agg, service)=>{
      agg.put(service._2.toJson)
    }).toString(2)
  }

  def initService(serviceName:String,confMap:java.util.Map[String,Any],confFile:java.io.File)(implicit checkifexist:Boolean=true):Boolean={
    try{
      val service = new Service(confFile.getCanonicalPath,
        ModuleDef.initName(serviceName,confMap),
        ModuleDef.initDesc(confMap),
        ModuleDef.initOutputs(confMap),
        null,
        null
      );
      // check if module name already exist

      services.get(serviceName) match {
        case Some(m:Service) => {
          if(checkifexist){
            throw new Exception("Service already exist, defined in "+m.definitionPath)
          }else{
            service.startcmd = confMap.get("start").asInstanceOf[java.util.Map[String,String]]
            services = services.updated(serviceName,service)
          }
          true
        }
        case None => service.startcmd = confMap.get("start").asInstanceOf[java.util.Map[String,String]]; services += (serviceName-> service); false
      }

    }catch{
      case e: Throwable => e.printStackTrace(); logger.error("Wrong service defintion in "+confFile.getCanonicalPath+"\n"+e.getMessage+"\n This service will not be registered."); false
    }
  }

  def initService(confFile:java.io.File):Boolean={
    try{
      val servicename = confFile.getName.substring(0,confFile.getName.lastIndexOf('.'))
      //logger.debug("Initiating module "+servicename)



      val yaml = new Yaml()
      val ios = new FileInputStream(confFile)
      val confMap = yaml.load(ios).asInstanceOf[java.util.Map[String,Any]]
      initService(servicename,confMap,confFile)
    }catch{
      case e: Throwable => e.printStackTrace(); logger.error("Wrong service defintion in "+confFile.getCanonicalPath+"\n"+e.getMessage+"\n This service will not be registered."); false
    }
  }

  def exportedVariables():Map[String,AbstractParameterVal]={
    services.foldLeft(Map[String,AbstractParameterVal]())((exported,service)=>{
      exported ++ {
        if(service._2.isRunning()){
          service._2.outputs.foldLeft(Map[String,AbstractParameterVal]())((serviceoutputs,output)=>{
            serviceoutputs + (output._1 -> output._2.toAbstractParameterVal())
          })
        }else{
          Map[String,AbstractParameterVal]()
        }
      }
    })
  }

}
