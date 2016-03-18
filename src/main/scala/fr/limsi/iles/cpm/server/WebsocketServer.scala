package fr.limsi.iles.cpm.server

import java.net.InetSocketAddress
import java.util
;

import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

/**
  * Created by paul on 3/17/16.
  */
class WebsocketServer(port:Int) extends WebSocketServer(new InetSocketAddress( port )){

  override def onOpen(conn: WebSocket, handshake: ClientHandshake): Unit = {
    this.sendToAll( "{\"message\":\"" +"new connection: " + handshake.getResourceDescriptor()+"\"}" );
  }

  override def onError(conn: WebSocket, ex: Exception): Unit = {
    this.sendToAll( "{\"message\":\"" + conn + " disconnected\"}" );
  }

  override def onMessage(conn: WebSocket, message: String): Unit = {
    //this.sendToAll(message,conn);
  }

  override def onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean): Unit = {

  }

  def sendToAll( text:String  ):Unit = {
    sendToAll(text,null);
  }

  /**
    * Sends <var>text</var> to all currently connected WebSocket clients.
    *
    * @param text
   *            The String to send across the network.
    * @throws InterruptedException
   *             When socket related I/O errors occur.
    */
  def sendToAll( text :String  , except :WebSocket ) :Unit = {
    val con  : util.Collection[WebSocket] = connections();
    con.synchronized {
      val it = con.iterator()
      while(  it.hasNext) {
        val c = it.next()
        if(except != c){
          c.send( text );
        }
      }
    }
  }
}
