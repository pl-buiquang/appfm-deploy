package fr.limsi.iles.cpm.server

import java.util.concurrent.{Executors, ExecutorService}

import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.server.EventManager._
import fr.limsi.iles.cpm.utils.ConfManager
import org.json.JSONObject
import org.zeromq.ZMQ

/**
 * Created by buiquang on 12/22/15.
 */
object EventManager {

  val pubport = ConfManager.get("event_pub_port")


  def start()={
    val service: ExecutorService = Executors.newSingleThreadExecutor()
    service.execute(new EventManagerServer(pubport.toString))
    service.shutdown()
  }



  def emit(message:EventMessage)={
    val eventpushsocket = Server.context.socket(ZMQ.PUSH)
    eventpushsocket.connect("inproc://eventloop")
    eventpushsocket.send(message);
  }


}

class EventManagerServer (pubport:String) extends Runnable with LazyLogging{
  def run()={
    try{
      logger.info("starting event loop")
      val pubsocket = Server.context.socket(ZMQ.PUB)
      pubsocket.bind("tcp://*:" + pubport)

      val eventsocket = Server.context.socket(ZMQ.PULL)
      eventsocket.bind("inproc://eventloop")

      val wsServer = new WebsocketServer(Integer.valueOf(ConfManager.get("websocket_port").toString))
      wsServer.start()
      logger.info("event loop started")
      while(!Thread.interrupted()){

        val message = eventsocket.recvStr()
        logger.info(message)
        pubsocket.send(message)

        wsServer.sendToAll(message)
      }

    }catch{
      case e:Throwable=>e.printStackTrace(); logger.error(e.getMessage)
    }

  }
}

object EventMessage extends LazyLogging{
  implicit def parse(message:String) : EventMessage = {
    try{
      val json = new JSONObject(message)
      val eventtype = json.getString("type")
      val target = if(json.has("target")){
        json.getString("target")
      }else{
        ""
      }
      val more = if(json.has("more")){
        json.getString("more")
      }else{
        ""
      }
      new EventMessage(eventtype,target,more)
    }catch{
      case e:Throwable => {
        logger.error("invalid event message")
        new EventMessage("invalid-event","","")
      }
    }
  }

  implicit def toString(message:EventMessage) : String = {
    val json = new JSONObject()
    json.put("type",message.msgtype)
    json.put("target",message.target)
    json.put("more",message.more)
    json.toString(2)
  }
}

class EventMessage(val msgtype:String,val target:String,val more:String){
  override def toString()={
    EventMessage.toString(this)
  }
}