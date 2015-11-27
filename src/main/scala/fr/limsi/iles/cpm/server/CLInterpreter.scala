package fr.limsi.iles.cpm.server

import java.net.URLEncoder
import java.util.UUID

import com.mongodb.DBObject
import com.mongodb.casbah.commons.MongoDBObject
import fr.limsi.iles.cpm.module.definition.ModuleManager
import fr.limsi.iles.cpm.module.process.ProcessRunManager
import fr.limsi.iles.cpm.module.value.AbstractModuleVal
import fr.limsi.iles.cpm.utils.{Log, Utils, ConfManager}

import scala.io.Source
import scala.sys.process._

/**
 * Created by buiquang on 9/15/15.
 */
object CLInterpreter {

  /**
   * Interpret command line arguments
   * @param arg
   * @return
   */
  def interpret(arg:String) :String = {
    val args = arg.split("\\s")
    try{
      args(0) match {
        case "corpus" =>
          interpretCorpusCommands(args.slice(1,args.length))
        case "process" =>
          interpretProcessCommands(args.slice(1,args.length))
        case "module" =>
          interpretModuleCommands(args.slice(1,args.length))
        case "exec" =>
          interpretExecCommands(args.slice(1,args.length))
        case "reload" => {
          ModuleManager.reload()
          "ok"
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
        case "ls" => "List recursively all added files of the corpus"
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
          val master = MongoDBObject("master"->true)
          val it = ProcessRunManager.processCollection.find(master)
          var toprint = ""
          while(it.hasNext){
            val pobj = it.next()
            if(opt._1 || pobj.get("status")=="Running"){
              toprint += pobj.get("name") + "\t" + pobj.get("ruid").toString
              if(opt._2){
                toprint+="\n"+processChildrenRecPrint(pobj,"  |")+"\n"
              }else{
                toprint+="\n"
              }
            }
          }
          toprint
        } catch {case e:Throwable => "Error :"+e.getMessage}
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
            val result = if(process.parentEnv.args.contains(args(2))){
              process.parentEnv.args(args(2)).asString()
            }else if(args(2)=="__ALL__"){
              val r = process.parentEnv.args.keys.foldLeft("")(_ +","+ _)
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

  def interpretModuleCommands(args:Seq[String]) = {
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
          ProcessRunManager.newRun(args(1),args(2),true)
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
