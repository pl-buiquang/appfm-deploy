package fr.limsi.iles.cpm.server

import java.io.{PrintWriter, FileInputStream}
import java.net.URLEncoder
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.function.Consumer

import com.mongodb.{BasicDBObject, DBObject}
import com.mongodb.casbah.commons.MongoDBObject
import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.CPM
import fr.limsi.iles.cpm.corpus.CorpusManager
import fr.limsi.iles.cpm.module.definition.{ModuleDef, ModuleManager}
import fr.limsi.iles.cpm.process.{Exited, ProcessRunManager}
import fr.limsi.iles.cpm.module.value.AbstractModuleVal
import fr.limsi.iles.cpm.service.ServiceManager
import fr.limsi.iles.cpm.utils.{YamlElt, Log, Utils, ConfManager}
import org.json.{JSONObject, JSONArray}
import org.yaml.snakeyaml.Yaml

import scala.io.Source
import scala.sys.process._

/**
 * Created by buiquang on 9/15/15.
 */
object CLInterpreter extends LazyLogging{

  /**
   * Interpret command line arguments
   * @param cmdarg
   * @param data
   * @return
   */
  def interpret(cmdarg:String,data:Option[String],user:String) :String = {
    val args = cmdarg.split("\\s")
    try{
      args(0) match {
        case "corpus" =>
          interpretCorpusCommands(args.slice(1,args.length))
        case "process" =>
          interpretProcessCommands(args.slice(1,args.length),data,user)
        case "module" =>
          interpretModuleCommands(args.slice(1,args.length),data,user)
        case "service" =>
          interpretServiceCommands(args.slice(1,args.length),data)
        case "exec" =>
          interpretExecCommands(args.slice(1,args.length))
        case "reload" => {
          ModuleManager.reload()
          "ok"
        }
        case "status"=>{
          "Active (more details about configuration and state to come)"
        }
        case "log"=> interpretLogCommand(args.slice(1,args.length))
        case "settings"=>{
          var moreargs = Array[String]()
          if(args.length>=1){
            moreargs = args.slice(1,args.length)
          }
          interpretSettingsCommands(moreargs,data)
        }
        case "fs"=>{
          var moreargs = Array[String]()
          if(args.length>=1){
            moreargs = args.slice(1,args.length)
          }
          interpretFileSystemCommands(moreargs,data)
        }
        case "restart" =>
          "Restart cpm"
        case "test" => {
          ModuleManager.modules.foldLeft("")((output,module)=>{
            output + "\n"+module._1+" : "+module._2.needsDocker().toString
          })
        }
        case _ => "No such method!"
      }
    }catch{
      case e:Throwable => logger.error(e.getMessage()) ; "Command error. More details in Core Log"
    }
  }

  def interpretLogCommand(args:Seq[String])={
    try{
      val page : Int = if(args.length != 0){
        args(0).toInt
      }else{
        0
      }
      Log.get(page)
    }catch{
      case e:Throwable => cliError("Unexpected error happened ! ("+e.getMessage+")")
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
        case "lsdir" => {
          Utils.lsDir(args(1),args(2).toInt).toString
        }
        case "ls" => {
          val jsonoutput = args.exists(_=="--json")
          val onlycorpora = !(args.exists(_=="-a") || args.exists(_=="--all"))
          if(jsonoutput){
            CorpusManager.jsonExport(false)(onlycorpora).toString
          }else{
            CorpusManager.ls().toString
          }
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

  def interpretProcessCommands(args:Seq[String],data:Option[String],user:String):String = {
    try{
      args(0) match{
        case "ls" => try {
          val all = args.exists(_=="-a")
          val head = args.exists(_=="-h")
          val owned = args.exists(_=="-u")
          var headsize = 10


          var toprint : String = ""
          var ids = Array[String]()
          // first in memory (running) process
          ProcessRunManager.list.foreach(el=>{
            if(el._2.parentProcess.isEmpty){ // only master process
              if(!head || el._2.owner == user || el._2 == "_DEFAULT"){
                ids = ids :+ el._1.toString
                toprint += el._2.moduleval.moduledef.name+" : "+el._1+"("+el._2.creationDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)+")\n"
              }
            }
          })
          // then those saved in db (past process)
          val sort = new BasicDBObject()
          sort.put("creationdate",-1)
          val it = ProcessRunManager.processCollection.find().sort(sort)
          while(it.hasNext){
            val pobj = it.next()
            val id = pobj.get("ruid").toString
            if((head || all) && !ids.contains(id)/*|| pobj.get("status")=="Running"*/){
              val owner = pobj.get("owner")
              if(!head || (headsize>0 && (owner == user  ||  owner == "" || owner == "_DEFAULT"))){
                headsize -= 1
                toprint += pobj.get("name") + " : " + id+"("+java.time.LocalDateTime.parse(pobj.get("creationdate").toString).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)+")\n"
              }

              /*if(opt._2){
                toprint+="\n"+processChildrenRecPrint(pobj,"  |")+"\n"
              }else{
                toprint+="\n"
              }*/
            }
          }

          toprint
        } catch {case e:Throwable => """{"error" : """"+e.getMessage+"\"}"}
        case "get" => try{
          if(args.size > 1){
            ProcessRunManager.getProcess(UUID.fromString(args(1))).serializeToJSON().toString(2)

          }else{
            "Missing pid"
          }
        }catch {case e:Throwable => """{"error" : """"+e.getMessage+"\"}"}
        case "del" => try{
          if(args.size > 1){
            ProcessRunManager.deleteProcess(UUID.fromString(args(1)))
          }else{
            "Missing pid"
          }
        }catch {case e:Throwable => """{"error" : """"+e.getMessage+"\"}"}
        case "status" => try{
          if(args.size > 1){
            //ProcessRunManager.getProcess(UUID.fromString(args(1))).getStatus(true)
            val process = ProcessRunManager.getProcess(UUID.fromString(args(1)))
            val mp = process.getMasterProcess()
            val detailedstatus = mp.getDetailedStatus().toString()
            if(args.exists(_=="--json")){
              val json = new JSONObject()
              mp.status match{
                case Exited(s)=> json.put("exited",true)
                case _ => {}
              }
              json.put("info",detailedstatus)
              json.toString
            }else{
              detailedstatus
            }
          }else{
            "Missing pid"
          }
        }catch {case e:Throwable => """{"error" : """"+e.getMessage+"\"}"}
        case "log" => {
          if(args.size > 1){
            val process = ProcessRunManager.getProcess(UUID.fromString(args(1)))
            if(args.size > 2 && args(2)=="--gui"){
              // TODO in web allow get to open panels
              process.getLog()
            }else{
              process.getLog();
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
            result
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
      settings.put("docker_enabled",CPM.dockerEnabled)
      val hostname = "hostname".!!.trim()
      settings.put("host",hostname)
      settings.put("port",ConfManager.get("cmd_listen_port").toString)
      settings.put("wshost",hostname+":"+ConfManager.get("websocket_port").toString)
      settings.toString(2)
    }catch{
      case e:Throwable => e.getMessage+ " (Error processing command)"
    }
  }

  def interpretModuleCommands(args:Seq[String],data:Option[String],user:String) = {
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
          val synced = !args.exists(_=="--sync")
          if(data.isDefined){
            ProcessRunManager.newRun(args(1),data.get,synced,user)
          }else{
            val bs = Source.fromFile(args(2))
            val confdata = bs.getLines.foldLeft("")((agg,line)=>agg+"\n"+line)
            bs.close()
            ProcessRunManager.newRun(args(1),confdata,synced,user)
          }
        }
        case "info" => {
          var json = new JSONObject()
          if(ModuleManager.modules.contains(args(1))){
            val module = ModuleManager.modules(args(1))
            json.put("module",new JSONObject(module.serialize()(true)))
            val bs = Source.fromFile(module.confFilePath)
            json.put("source",bs.getLines.foldLeft("")((agg,line)=>agg+"\n"+line))
            bs.close()
          }else{
            val bs = Source.fromFile(args(1))
            json.put("source",bs.getLines.foldLeft("")((agg,line)=>agg+"\n"+line))
            bs.close()
          }
          json.toString(2)
        }
        case "getdesc" => {
          if(ModuleManager.modules.contains(args(1))){
            val module = ModuleManager.modules(args(1))
            module.desc + {
              if (args.exists(_=="--extended")){
                "Inputs : \n"+
                module.inputs.foldLeft("\n")((agg,input)=>{
                  agg + ModuleDef.printInput(input) + "\n\n"
                })
              }else{
                ""
              }
            }
          }else{
            "Unknown module!"
          }
        }
        case "create" => {
          if(args.size > 2 && data.isDefined) {
            ModuleManager.createModule(args(1),args(2),data.get)
          }else{
            "missing arguments"
          }
        }
        case "update" => {
          if(args.size > 1 && data.isDefined) {
            ModuleManager.updateModule(args(1),data.get)
          }else{
            "missing arguments"
          }
        }
        case "updateDisplay" => {
          if(args.size > 1 && data.isDefined){
            ModuleManager.updateModuleDisplay(args(1),data.get)
          }else{
            "missing arguments"
          }
        }

        case _ => "Invalid argument"
      }
    }catch{
      case e:Throwable => e.getMessage+ " (Missing argument)"
    }
  }

  def interpretServiceCommands(args:Array[String],data:Option[String]):String={
    try{
      args(0) match{
        case "ls"=>{
          ServiceManager.toString
        }
        case "start"=>{
          if(args.size > 1){
            ServiceManager.services.get(args(1)) match {
              case Some(service)=>{
                val json = new JSONObject()
                if(service.start()){
                  json.put("success",true)
                  json.toString(2)
                }else{
                  jsonError("couldn't start service")
                }
              }
              case None => jsonError("unknwon service")
            }
          }else{
            jsonError("missing argument")
          }
        }
        case "stop"=>{
          if(args.size > 1){
            ServiceManager.services.get(args(1)) match {
              case Some(service)=>{
                val json = new JSONObject()
                if(service.stop()){
                  json.put("success",true)
                  json.toString(2)
                }else{
                  jsonError("couldn't stop service")
                }
              }
              case None => jsonError("unknwon service")
            }
          }else{
            jsonError("missing argument")
          }
        }
        case "status"=>{
          if(args.size > 1){
            ServiceManager.services.get(args(1)) match {
              case Some(service)=>{
                val json = new JSONObject()
                json.put("status",service.isRunning())
                json.toString()
              }
              case None => jsonError("unknwon service")
            }
          }else{
            jsonError("missing argument")
          }
        }
        case "info"=>{
          if(args.size > 1){
            ServiceManager.services.get(args(1)) match {
              case Some(service)=>{
                service.toJson.toString(2)
              }
              case None => jsonError("unknwon service")
            }
          }else{
            jsonError("missing argument")
          }
        }
        case _ =>{
          jsonError("unknown command")
        }
      }
    }catch {
      case e:Throwable => jsonError(e.getMessage)
    }
  }

  def interpretFileSystemCommands(args:Array[String],data:Option[String]):String = {
    if(args.size==0){
      return cliError("Missing argument")
    }
    try{
      args(0) match{
        case "get" => {
          if(args.size>1){
            val filename = args.tail.mkString(" ")
            if (!(new java.io.File(filename)).exists()){
              return cliError("File doesn't exist!")
            }
            if(!Utils.checkValidPath(filename)){
              return cliError("Not allowed to retrieve this file ! ("+filename+"). Fetchable files must be within corpus or result directories.")
            }
            val bs = Source.fromFile(filename)
            val lines = bs.getLines()
            val output = if (lines.nonEmpty){
              lines.foldLeft("")((agg,line)=>agg+"\n"+line).substring(1)
            }else{
              ""
            }
            bs.close()
            output
          }else{
            cliError("Missing file")
          }
        }
        case "ls" => {
          if(args.size>2){
            if (!(new java.io.File(args(1))).exists()){
              return cliError("File doesn't exist!")
            }
            if(!Utils.checkValidPath(args(1))){
              return cliError("Not allowed to list this directory ! ("+args(1)+"). Directories must be within corpus or result directories.")
            }
            Utils.lsDir(args(1),args(2).toInt).toString
          }else{
            cliError("Missing file or start offset")
          }
        }
        case _ => cliError("Invalid argument")
      }
    }catch {
      case e:Throwable =>  e.printStackTrace(); cliError(e.getMessage)
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
   * Default message error tag for cli
   * @param message
   * @return
   */
  def cliError(message:String):String={
    "__APPFM_ERROR_B__"+message+"__APPFM_ERROR_E__"
  }

  def jsonError(message:String):String={
    val json = new JSONObject()
    json.put("error",message)
    json.toString(2)
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
