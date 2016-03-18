package fr.limsi.iles.cpm.server

import scala.util.matching.Regex


/**
 * Created by paul on 10/9/15.
 *
 * Encapsulation/Format is as follow :
 * ===CMD==
 *
 * ===DATA==
 *
 */
object CPMCommand {
  implicit def parse(message:String) : CPMCommand = {
    new CPMCommand(message)
  }

  implicit def toString(message:CPMCommand) : String = {
    message.toString()
  }

  protected def getFrames(rawmessage:String) : Map[String,String] = {
    val matches = """==(?s)([A-Z]+)==(.*?)==END_\1==""".r.findAllMatchIn(rawmessage)
    matches.foldLeft(Map[String,String]())((map,matchelt)=>{
      map + (matchelt.group(1) -> matchelt.group(2))
    })
  }

  def execute(rawmessage:String):String = execute(new CPMCommand(rawmessage))

  def execute(command:CPMCommand):String = {
    CLInterpreter.interpret(command.command,command.data)
  }
}

class CPMCommand(zmqmessage:String){




  val frames = CPMCommand.getFrames(zmqmessage)

  val user = frames.getOrElse("USER","_default")
  val password = frames.getOrElse("PSWD","")
  val command = frames.getOrElse("CMD","unknown cmd")
  val data = frames.get("DATA")

}





