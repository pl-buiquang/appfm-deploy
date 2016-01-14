package fr.limsi.iles.cpm.server

import java.util.concurrent.{Executors, ExecutorService}

import fr.limsi.iles.cpm.utils.ConfManager
import org.zeromq.ZMQ

/**
 * Created by buiquang on 12/22/15.
 */
object EventManager extends Runnable{

  val pubport = ConfManager.get("event_pub_port")

  def start()={
    val service: ExecutorService = Executors.newSingleThreadExecutor()
    service.execute(this)
    service.shutdown()
  }

  def run()={
    val pubsocket = Server.context.socket(ZMQ.PUB)
    pubsocket.bind("tcp://*:" + pubport)

    val eventsocket = Server.context.socket(ZMQ.PULL)
    eventsocket.bind("inproc://eventloop")

    while(!Thread.interrupted()){

      val message = eventsocket.recvStr()
      pubsocket.send(message)

    }

  }




}
