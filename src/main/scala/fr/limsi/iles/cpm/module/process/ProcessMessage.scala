package fr.limsi.iles.cpm.module.process

/**
 * Created by buiquang on 10/6/15.
 */

object ProcessMessage{

  implicit def parse(message:String) : ProcessMessage = {
    val components = message.split("\n")
    if(components.size==2){
      new ValidProcessMessage(components(0),components(1))
    }else{
      new InvalidProcessMessage()
    }
  }

  implicit def toString(message:ProcessMessage) : String = {
    message.toString()
  }
}

class ProcessMessage

case class ValidProcessMessage(val sender:String,val status:String) extends ProcessMessage{

  override def toString(): String = {
    sender+"\n"+status
  }
}

case class InvalidProcessMessage() extends ProcessMessage

