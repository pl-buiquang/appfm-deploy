package fr.limsi.iles.cpm.core

/**
 * Created by buiquang on 9/7/15.
 */
import org.zeromq.{ZMQException, ZMQ}
import java.io.{BufferedWriter, FileWriter, PrintWriter, File}
import java.util.concurrent.{Executors, ExecutorService}

object Server {
  val context = ZMQ.context(1)

  def run(port : String) {
    //  Prepare our context and socket

    val socket = context.socket(ZMQ.REP)
    println ("starting")
    socket.bind ("tcp://*:"+port)

    val pool: ExecutorService = Executors.newSingleThreadExecutor()


    while (!Thread.currentThread().isInterrupted) {
      //  Wait for next request from client
      //  We will wait for a 0-terminated string (C string) from the client,
      //  so that this server also works with The Guide's C and C++ "Hello World" clients
      val request = socket.recv (0)
      // If provided by scala or c apps for instance (not needed with python...)
      // In order to display the 0-terminated string as a String,
      //  we omit the last byte from request
      val stringinput = new String(request,0,request.length)
      println ("Received request: ["
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
    context.term()
  }
}
