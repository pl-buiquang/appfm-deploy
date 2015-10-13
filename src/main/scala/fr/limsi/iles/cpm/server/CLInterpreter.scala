package fr.limsi.iles.cpm.server

import fr.limsi.iles.cpm.module.ModuleManager
import fr.limsi.iles.cpm.module.process.ProcessRunManager

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
        case "pipeline" =>
          interpretPipelineCommands(args.slice(1,args.length))
        case "reload" =>
          "Reload cpm with configuration"
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

  def interpretProcessCommands(args:Seq[String]) = {
    try{
      args(0) match{
        case "ls" => try {Seq("docker","ps","-a") !!} catch {case e:Throwable => "Error :"+e.getMessage}
        case "run" => try{
          Seq("bash",
            "/vagrant/modules/addons/bonsai_parser/parse_all_embed.sh",
            "/vagrant/data/corpus/munshitest",
            "/vagrant/data/results/munshitest") !!
        }catch {case e:Throwable => "Error :"+e.getMessage}
        case _ => "Invalid argument"
      }
    }catch{
      case e:Throwable => "Missing argument"
    }
  }

  def interpretModuleCommands(args:Seq[String]) = {
    try{
      args(0) match{
        case "ls" => ModuleManager.ls()
        case "run" => {
          ProcessRunManager.newRun(args(1),args(2))
        }
        case _ => "Invalid argument"
      }
    }catch{
      case e:Throwable => e.getMessage+ " (Missing argument)"
    }
  }

  def interpretPipelineCommands(args:Seq[String]) = {
    try{
      args(0) match{
        case _ => "Invalid argument"
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
