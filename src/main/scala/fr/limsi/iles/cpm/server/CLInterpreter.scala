package fr.limsi.iles.cpm.server

import java.io.FileInputStream
import java.net.URLEncoder
import java.util.UUID
import java.util.function.Consumer

import com.mongodb.DBObject
import com.mongodb.casbah.commons.MongoDBObject
import fr.limsi.iles.cpm.corpus.CorpusManager
import fr.limsi.iles.cpm.module.definition.ModuleManager
import fr.limsi.iles.cpm.module.process.ProcessRunManager
import fr.limsi.iles.cpm.module.value.AbstractModuleVal
import fr.limsi.iles.cpm.utils.{YamlElt, Log, Utils, ConfManager}
import org.json.{JSONObject, JSONArray}
import org.yaml.snakeyaml.Yaml

import scala.io.Source
import scala.sys.process._

/**
 * Created by buiquang on 9/15/15.
 */
object CLInterpreter {

  /**
   * Interpret command line arguments
   * @param cmdarg
   * @param data
   * @return
   */
  def interpret(cmdarg:String,data:Option[String]) :String = {
    val args = cmdarg.split("\\s")
    try{
      args(0) match {
        case "corpus" =>
          interpretCorpusCommands(args.slice(1,args.length))
        case "process" =>
          interpretProcessCommands(args.slice(1,args.length))
        case "module" =>
          interpretModuleCommands(args.slice(1,args.length),data)
        case "exec" =>
          interpretExecCommands(args.slice(1,args.length))
        case "reload" => {
          ModuleManager.reload()
          "ok"
        }
        case "settings"=>{
          var moreargs = Array[String]()
          if(args.length>=1){
            moreargs = args.slice(1,args.length)
          }
          interpretSettingsCommands(moreargs,data)
        }
        case "restart" =>
          "Restart cpm"
        case "test" => Thread.sleep(10000); "ok"
        case _ => "No such method!"
      }
    }catch{
      case e:Throwable => "Missing argument"
    }
  }

  /**
   * Interpret corpus related commands
   * @param args
   */
  def interpretCorpusCommands(args:Seq[String]) = {
    try{
      args(0) match{
        case "create" => "Create corpus"
        case "copy" => "Copy corpus"
        case "resolve" => "Assign new path to corpus for user moved directories"
        case "locate" => "Returns the location of the corpus"
        case "ls" => val jsonoutput = args.exists(_=="--json")
          if(jsonoutput){
            CorpusManager.jsonExport().toString
          }else{
            CorpusManager.ls().toString
          }
        case "add" => "Add a file to corpus"
        case "commit" => "Version of a corpus"
        case "log" => "Log of operations over corpus"
        case "export" => "export corpus"
        case "rm" => "remove file from corpus"
        case "delete" => "delete corpus"
        case _ => ""
      }
    }catch{
      case e:Throwable => "Missing argument"
    }
  }

  def processChildrenRecPrint(process:DBObject,offset:String):String={
    var out = ""
    val children = process.get("children").toString.split(",")

    if(children.length>0 && process.get("children").toString.trim!="") {
      children.reverse.foreach(pid => {
        val q = MongoDBObject("ruid"->pid)
        val childproc = ProcessRunManager.processCollection.findOne(q)
        childproc match {
          case Some(stuff) => out += offset+(stuff.get("name") + "\t" + stuff.get("ruid").toString+"\n") + processChildrenRecPrint(stuff,"  |"+offset)
          case None => ""
        }
      })
    }else {
      out = ""
    }
    out
  }

  def interpretProcessCommands(args:Seq[String]):String = {
    try{
      args(0) match{
        case "ls" => try {
          val opt = if(args.size > 2){
            if(args(1)=="-a" && args(2)=="-r") {
              (true,true)
            }else {
              (false, false) // should not happen if properly called via cpm-cli
            }
          }else if(args.size > 1){
            if(args(1)=="-a") {
              (true,false)
            }else if(args(1)=="-r") {
              (false,true)
            }else {
              (false,false) // should not happen if properly called via cpm-cli
            }
          }else {
            (false,false)
          }
          var toprint : String = ""
          var ids = Array[String]()
          // first in memory (running) process
          ProcessRunManager.list.foreach(el=>{
            if(el._2.parentProcess.isEmpty){ // only master process
              ids = ids :+ el._1.toString
              toprint += el._2.moduleval.moduledef.name+" : "+el._1+"\n"
            }
          })
          // then those saved in db (past process)
          val it = ProcessRunManager.processCollection.find()
          while(it.hasNext){
            val pobj = it.next()
            val id = pobj.get("ruid").toString
            if(opt._1 && !ids.contains(id)/*|| pobj.get("status")=="Running"*/){
              toprint += pobj.get("name") + " : " + id+"\n"
              /*if(opt._2){
                toprint+="\n"+processChildrenRecPrint(pobj,"  |")+"\n"
              }else{
                toprint+="\n"
              }*/
            }
          }

          toprint
        } catch {case e:Throwable => "Error :"+e.getMessage}
        case "get" => try{
          if(args.size > 1){
            ProcessRunManager.getProcess(UUID.fromString(args(1))).serializeToJSON().toString

          }else{
            "Missing pid"
          }
        }catch {case e:Throwable => "Error :"+e.getMessage}
        case "status" => try{
          if(args.size > 1){
            ProcessRunManager.getProcess(UUID.fromString(args(1))).getStatus(true)
          }else{
            "Missing pid"
          }
        }catch {case e:Throwable => "Error :"+e.getMessage}
        case "log" => {
          if(args.size > 1){
            val process = ProcessRunManager.getProcess(UUID.fromString(args(1)))
            if(args.size > 2 && args(2)=="--gui"){
              if(process.moduleval.moduledef.name=="_CMD")
                "index.php?file="+URLEncoder.encode("/tmp/err"+args(1),"utf-8")
              else
                "mm"
            }else{
              if(process.moduleval.moduledef.name=="_CMD")
                Source.fromFile("/tmp/err"+args(1)).getLines.mkString
              else
                "ll"
            }
          }else{
            "Missing pid"
          }
        }
        case "view" => {
          if(args.size > 2){
            val process = ProcessRunManager.getProcess(UUID.fromString(args(1)))
            val result = if(process.parentEnv != null && process.parentEnv.getVars().contains(args(2))){
              process.parentEnv.getRawVar(args(2)).get.asString()
            }else if(process.env.getRawVar(args(2)).isDefined){
              val yamlstring = process.env.getRawVar(args(2)).get.toYaml()
              if(args.exists(_=="--json")){
                val yaml= new Yaml();
                val obj = yaml.load(yamlstring);
                return YamlElt.fromJava(obj).toJSONObject().toString
              }else{
                yamlstring
              }

            }else if(args(2)=="__ALL__"){
              val r0 = if(process.parentEnv!=null){
                process.parentEnv.getVars().keys.foldLeft("")(_ +","+ _)
              }else{
                ""
              }
              val r = r0+process.env.getVars().keys.foldLeft("")(_ +","+ _)
              if(r.length>0)
                r.substring(1)
              else
                r
            }else{
              "no result"
            }
            if(args.size > 3 && args(3)=="--gui"){
              "index.php?content="+URLEncoder.encode(result,"utf-8")
            }else{
              result
            }
          }else{
            "Missing pid"
          }
        }
        case _ => "Invalid argument"
      }
    }catch{
      case e:Throwable => e.printStackTrace(); "Missing argument"
    }
  }

  def interpretSettingsCommands(args:Seq[String],data:Option[String]) = {
    try {
      var settings = new JSONObject()
      settings.put("modules",          {var json = new JSONArray()
      ConfManager.get("modules_dir").asInstanceOf[java.util.ArrayList[String]].forEach(new Consumer[String] {
        override def accept(t: String): Unit = {
          val file = new java.io.File(t)
          var jsonfile = new JSONObject()
          jsonfile.put("name",file.getCanonicalPath)
          if(file.exists()){
            jsonfile.put("exist",true)
          }else{
            jsonfile.put("exist",false)
          }
          json.put(jsonfile)
        }
      })
        json
      }
      )
      settings.put("result_dir",ConfManager.get("default_result_dir").toString)
      settings.put("corpus_dir",ConfManager.get("default_corpus_dir").toString)
      settings.toString()
    }catch{
      case e:Throwable => e.getMessage+ " (Error processing command)"
    }
  }

  def interpretModuleCommands(args:Seq[String],data:Option[String]) = {
    try{
      args(0) match{
        case "ls" => {
          val onlyname = args.exists(_=="--name")
          val jsonoutput = args.exists(_=="--json")
          if(jsonoutput){
            ModuleManager.jsonExport(onlyname)
          }else{
            ModuleManager.ls(onlyname)
          }
        }
        case "run" => {
          if(data.isDefined){
            ProcessRunManager.newRun(args(1),data.get,true)
          }else{
            val confdata = Source.fromFile(args(2)).getLines.foldLeft("")((agg,line)=>agg+"\n"+line)
            ProcessRunManager.newRun(args(1),confdata,true)
          }
        }
        case "info" => {
          var json = new JSONObject()
          if(ModuleManager.modules.contains(args(1))){
            val module = ModuleManager.modules(args(1))
            json.put("module",new JSONObject(module.serialize()(true)))
            json.put("source",Source.fromFile(module.confFilePath).getLines.foldLeft("")((agg,line)=>agg+"\n"+line))
          }else{
            json.put("source",Source.fromFile(args(1)).getLines.foldLeft("")((agg,line)=>agg+"\n"+line))
          }
          json.toString
        }
        case "getdesc" => {
          if(ModuleManager.modules.contains(args(1))){
            ModuleManager.modules(args(1)).desc
          }else{
            "Unknown module!"
          }
        }

        case _ => "Invalid argument"
      }
    }catch{
      case e:Throwable => e.getMessage+ " (Missing argument)"
    }
  }

  def interpretExecCommands(args:Seq[String]) = {
    try{
      args(0) match{
        case _ => args!!
      }
    }catch{
      case e:Throwable => "Missing argument"
    }
  }

  /**
   * Try guide the user toward valid command
   * @param arg
   */
  def invalidCommandMessage(arg:String) = {
    // depends on previous command / context => change function interface or create foreach sub command interpreter
    // edit distance to valid command
    // propose best command ("did you mean? ...") and print help
    // default print help
  }

  def invalidArgumentMessage(arg:String) = {

  }

}
