/**
 * Created by buiquang on 8/27/15.
 */
package fr.limsi.iles.cpm


import java.io.File

import com.typesafe.scalalogging.{Logger, LazyLogging}
import fr.limsi.iles.cpm.module.definition.ModuleManager
import fr.limsi.iles.cpm.module.process.DockerManager
import fr.limsi.iles.cpm.module.value.MODVAL
import fr.limsi.iles.cpm.server.Server
import fr.limsi.iles.cpm.utils.{Log, ConfManager}
import org.slf4j.LoggerFactory

import scala.sys.process._

/**
 * Entry point of CPM server
 */
object CPM extends App{

  override def main(args:Array[String]): Unit ={

    // the first argument provided override default configuration file
    var confile : Option[String] = None
    if(args.length>0){
      if (new File(args(0)).exists()){
        confile = Some(args(0))
      }
    }
    Log("CPM Server Started!")

    // shutdown hook for clean exit
    sys.addShutdownHook({
      println("ShutdownHook called")
      DockerManager.cleanup() // clean up docker exited containers
      //Server.context.term()
    })

    // init configuration manager
    confile match {
      case Some(f) => ConfManager.init(f)
      case _ => ConfManager.init()
    }

    // init module manager
    // check for modules definition consistency
    ModuleManager.init()


    // check for docker proper initialization
    DockerManager.initCheckDefault()

    // start the main loop server
    val port = ConfManager.get("cmd_listen_port").toString
    Log("Listening on port : "+port)
    Server.run(port)
  }

}
