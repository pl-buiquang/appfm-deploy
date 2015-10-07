/**
 * Created by buiquang on 8/27/15.
 */
package fr.limsi.iles.cpm


import java.io.File

import com.typesafe.scalalogging.{Logger, LazyLogging}
import fr.limsi.iles.cpm.server.Server
import fr.limsi.iles.cpm.process.{ModuleManager, ModuleDef}
import fr.limsi.iles.cpm.utils.{ConfManager}
import org.slf4j.LoggerFactory

import scala.sys.process._

/**
 * Entry point of CPM server
 */
object CPM extends App{

  override def main(args:Array[String]): Unit ={
    args.map(s => println(s))
    var confile : Option[String] = None
    if(args.length>0){
      if (new File(args(0)).exists()){
        confile = Some(args(0))
      }
    }
    fr.limsi.iles.cpm.utils.Log("CPM Server Started!")

    sys.addShutdownHook({
      println("ShutdownHook called")
      //println(Seq("docker","ps","-a") !!)
      //Server.context.term()
    })

    /* // save config
    val fos = new OutputStreamWriter(new FileOutputStream(f), "UTF-8")
    props.store(fos, "")
    fos.close()
     */

    /*val module = new Module("Stanford Parser")
    module.exec()
*/
    confile match {
      case Some(f) => ConfManager.init(f)
      case _ => ConfManager.init()
    }
    /*
    load config
    init modules : list modules
    init pipelines : list pipelines
    init corpus : list corpus

    start master server
    start webserver
    start cli server


     */

    ModuleManager.init()


    val port = ConfManager.get("cmd_listen_port").toString
    println("Listening on port : "+port)
    Server.run(port)
  }

  def test(): Unit = {
    println("test")
  }

}
