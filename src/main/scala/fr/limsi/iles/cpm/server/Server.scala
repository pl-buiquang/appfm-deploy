package fr.limsi.iles.cpm.server

/**
 * Created by buiquang on 9/7/15.
 */

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.utils.ConfManager
import org.zeromq.{ZMQException, ZMQ}
import java.io.{BufferedWriter, FileWriter, PrintWriter, File}
import java.util.concurrent.{Executors, ExecutorService}


object Server extends LazyLogging{
  // zmq context initialization (global to whole application instance)
  val context = ZMQ.context(1)

  // run the core server
  def run(port : String) {
    // TODO  Replace all this with proper load balancer
    val nthreads = 20
    val pool: ExecutorService = Executors.newFixedThreadPool(nthreads)



    val frontend = context.socket(ZMQ.ROUTER)

    frontend.bind ("tcp://*:"+port)

    val backend = context.socket(ZMQ.DEALER)
    backend.bind("inproc://backendcmdhandler")
    //backend.bind("tcp://*:"+backendport)

    // init request handlers workers threads
    for(i <- (1 to nthreads)){
      pool.execute(new RequestHandler())
    }

    EventManager.emit(new EventMessage("kernel-started","","with "+nthreads+" client threads at port "+port))


    ZMQ.proxy(frontend,backend,null)


    frontend.close()
    backend.close()
    context.term()
  }


}

class RequestHandler extends Runnable with LazyLogging{

  /**
   * This is the core request handler to manage user interaction with cpm
   */
  def run()={
    val socket = Server.context.socket(ZMQ.REP)
    socket.connect ("inproc://backendcmdhandler")
    //socket.connect("tcp://localhost:"+Server.backendport)

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

      val answer :String= CPMCommand.execute(stringinput)


      //  Send reply back to client
      //  We will send a 0-terminated string (C string) back to the client,
      //  so that this server also works with The Guide's C and C++ "Hello World" clients
      val reply = (answer+" ").getBytes
      reply(reply.length-1)=0 //Sets the last byte of the reply to 0
      socket.send(answer, 0)

    }

    socket.close()
  }

}
