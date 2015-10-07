package fr.limsi.iles.cpm.core

/**
 * Created by buiquang on 9/7/15.
 */

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import org.zeromq.{ZMQException, ZMQ}
import java.io.{BufferedWriter, FileWriter, PrintWriter, File}
import java.util.concurrent.{Executors, ExecutorService}

object Server extends LazyLogging{
  val context = ZMQ.context(1)

  val backendport = 5005

  def run(port : String) {
    //  Replace all this with proper load balancer
    val pool: ExecutorService = Executors.newFixedThreadPool(10)

    for(i <- (1 to 10)){
      pool.execute(new RequestHandler())
    }

    val frontend = context.socket(ZMQ.ROUTER)
    println ("starting")
    frontend.bind ("tcp://*:"+port)

    val backend = context.socket(ZMQ.DEALER)
    backend.bind("tcp://*:"+backendport)

    ZMQ.proxy(frontend,backend,null)

    frontend.close()
    backend.close()
    context.term()
  }
}

class RequestHandler extends Runnable with LazyLogging{

  def run()={
    val socket = Server.context.socket(ZMQ.REP)
    socket.connect ("tcp://localhost:"+Server.backendport)

    while (!Thread.currentThread().isInterrupted) {
      //  Wait for next request from client
      //  We will wait for a 0-terminated string (C string) from the client,
      //  so that this server also works with The Guide's C and C++ "Hello World" clients
      val stringinput = socket.recvStr()
      // If provided by scala or c apps for instance (not needed with python...)
      // In order to display the 0-terminated string as a String,
      //  we omit the last byte from request
      //val stringinput = new String(request,0,request.length)
      logger.info("Received request: ["
        + stringinput  //  Creates a String from request, minus the last byte
        + "]")

      val answer :String= CLInterpreter.interpret(stringinput)

      val writer = new PrintWriter(new BufferedWriter(new FileWriter("log.txt",true )))

      writer.write(stringinput+"\n")
      writer.write(answer+"\n")
      writer.close()



      //  Send reply back to client
      //  We will send a 0-terminated string (C string) back to the client,
      //  so that this server also works with The Guide's C and C++ "Hello World" clients
      val reply = (answer+" ").getBytes
      reply(reply.length-1)=0 //Sets the last byte of the reply to 0
      socket.send(reply, 0)

    }

    socket.close()
  }

}
