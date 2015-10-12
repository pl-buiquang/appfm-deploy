package fr.limsi.iles.cpm.process

import java.io.PrintWriter

import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.utils.ConfManager
import scala.sys.process._

/**
 * Created by buiquang on 10/6/15.
 */
object DockerManager extends LazyLogging{

  def initCheckDefault():Boolean = {
    try {
      val images = "docker images".!!
      val imagelst = images.split("\n")
      if(!imagelst.exists(image => {
        val imageprops = image.split("\t")
        imageprops(0) == ConfManager.defaultDockerBaseImage
      })) {
        try {
          ("docker build -t " + ConfManager.defaultDockerBaseImage + " " + ConfManager.get("cpm_home_dir") + "/cpm-process-shell").!!
        } catch {
          case e:Throwable => logger.error(e.getMessage); fr.limsi.iles.cpm.utils.Log.error(e)
        }
      }
    }catch {
      case e:Throwable =>  logger.error(e.getMessage); fr.limsi.iles.cpm.utils.Log.error(e)
    }

    true
  }


  def baseRun(name:String,host:String,port:String,cmd:String,foldersync:java.io.File) = {
    val mount = "-v "+foldersync.getCanonicalPath+":"+foldersync.getCanonicalPath
    val mount2 = " -v /tmp:/tmp -v "+ConfManager.get("default_result_dir")+":"+ConfManager.get("default_result_dir")+" -v "+ConfManager.get("default_corpus_dir")+":"+ConfManager.get("default_corpus_dir")+" "
    val mount3 = " -v /home/paul/lib:/home/paul/lib "
    val absolutecmd = cmd.replace("\n"," ").replace("\"","\\\"").replaceAll("^./",foldersync.getCanonicalPath+"/")
    val dockercmd = "docker run "+mount+mount2+mount3+" -td "+ConfManager.defaultDockerBaseImage+" "+name+" "+port+" "+absolutecmd+""
    logger.debug(dockercmd)
    dockercmd.!!
  }

}
