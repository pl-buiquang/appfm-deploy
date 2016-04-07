package fr.limsi.iles.cpm.module.process

import java.io.File
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.Executors

import com.typesafe.scalalogging.LazyLogging

import fr.limsi.iles.cpm.server.Server
import fr.limsi.iles.cpm.utils.ConfManager
import org.zeromq.ZMQ

import scala.collection.mutable
import scala.sys.process.{Process}


object ProcessCMDMessage{
  implicit def fromString(message:String):ProcessCMDMessage={
    val frames = getFrames(message)
    new ProcessCMDMessage(UUID.fromString(frames("ID")),
      frames("NS"),
      frames("PORT"),
      frames("CMD"),
      frames.get("IMAGE"),
      new File(frames("DEF")),
      new File(frames("RUN")),
      frames("OPT"),
      frames("UNIQ").toBoolean
    )
  }

  protected def getFrames(rawmessage:String) : Map[String,String] = {
    val matches = """==(?s)([A-Z]+)==(.*?)==END_\1==""".r.findAllMatchIn(rawmessage)
    matches.foldLeft(Map[String,String]())((map,matchelt)=>{
      map + (matchelt.group(1) -> matchelt.group(2))
    })
  }
}

class ProcessCMDMessage(val id:UUID,val namespace:String,val processPort:String,val cmd:String,val dockerimagename:Option[String],val deffolder:File,val runfolder:File,val dockeropts:String,val unique:Boolean){



  def format():String={
    val dockimg = if(dockerimagename.isDefined){
      "==IMAGE=="+dockerimagename.get+"==END_IMAGE=="
    }else{
      ""
    }
    "==ID=="+id.toString+"==END_ID=="+
    "==NS=="+namespace+"==END_NS=="+
      "==PORT=="+processPort+"==END_PORT=="+
      "==CMD=="+cmd+"==END_CMD=="+
      dockimg+
      "==DEF=="+deffolder.getCanonicalPath+"==END_DEF=="+
      "==RUN=="+runfolder.getCanonicalPath+"==END_RUN=="+
      "==OPT=="+dockeropts+"==END_OPT=="+
      "==UNIQ=="+String.valueOf(unique)+"==END_UNIQ=="
  }

  val message = format()

  def send(): Unit ={
    val socketsend = Server.context.socket(ZMQ.PUSH)
    socketsend.connect("inproc://processmanageradd")
    socketsend.send(message)
    socketsend.close()
  }

  def end():Unit={
    val socketexit = Server.context.socket(ZMQ.PUSH)
    socketexit.connect("inproc://processmanagerremove")
    socketexit.send(message)
    socketexit.close()
  }

}


class ExecutableProcessCMDMessage(processcmdmessage:ProcessCMDMessage) extends LazyLogging{
  def execute()={
    ProcessManager.runningProcess += 1
    // if non docker , create new thread else run docker
    val containername = if(processcmdmessage.dockerimagename.isDefined){
      val containerName = DockerManager.serviceExec(
        processcmdmessage.id,
        processcmdmessage.namespace,
        "localhost",
        processcmdmessage.processPort,
        processcmdmessage.cmd,
        processcmdmessage.deffolder,
        processcmdmessage.dockerimagename.get,
        processcmdmessage.runfolder,
        processcmdmessage.dockeropts,
        processcmdmessage.unique)

      Some(containerName)
    }else{

      ProcessManager.nonDockerExecutorsService.execute(new Runnable {
        override def run(): Unit = {
          val absolutecmd = processcmdmessage.cmd.replace("\n"," ").replaceAll("^\\./",processcmdmessage.deffolder.getCanonicalPath+"/")
          val cmdtolaunch = "python "+ConfManager.get("cpm_home_dir")+"/"+ConfManager.get("process_shell_bin")+" false "+
            processcmdmessage.id.toString+" "+processcmdmessage.namespace+" "+processcmdmessage.processPort+" "+processcmdmessage.runfolder.getCanonicalPath+" "+absolutecmd+""

          Process(cmdtolaunch,processcmdmessage.runfolder) !

        }
      })

      None

    }

    if(containername.isDefined){
      logger.debug("Waiting for lock containerMap")
      ProcessManager.containersmap.synchronized{
        logger.debug("Acquired lock containerMap")
        ProcessManager.containersmap += (processcmdmessage.id.toString -> containername.get)
        logger.debug("Released lock containerMap")
      }
    }

  }

}

/**
 * Created by buiquang on 4/7/16.
 */
object ProcessManager extends Thread with LazyLogging {

  val maxProcess : Int = Integer.valueOf(ConfManager.get("maxproc").toString)
  var processQueue :mutable.Queue[ProcessCMDMessage] = mutable.Queue[ProcessCMDMessage]()
  var runningProcess = 0
  val nonDockerExecutorsService = Executors.newFixedThreadPool(maxProcess)
  var containersmap = mutable.Map[String,String]()
  var abstractProcessQueue:mutable.Queue[AbstractProcess] = mutable.Queue[AbstractProcess]()


  def addToQueue(process:AbstractProcess)={
    logger.debug("Waiting for lock processQueue")
    ProcessManager.processQueue.synchronized{
      logger.debug("Acquired lock processQueue")
      if (runningProcess<=maxProcess){
        process.run()
      }else{
        abstractProcessQueue.enqueue(process)
      }
      logger.debug("aprocess queue lenght is "+abstractProcessQueue.length)
      logger.debug("Released lock processQueue")
    }
  }

  override def run()={

    // listen to new incomming process
    val incomingProcess = new Thread(new Runnable {
      override def run(): Unit = {
        val socket = Server.context.socket(ZMQ.PULL)

        socket.bind("inproc://processmanageradd")

        while (true){
          val processmessage :ProcessCMDMessage= socket.recvStr(Charset.defaultCharset())
          logger.debug("receiving process cmd : "+processmessage.cmd)
          logger.debug("Waiting for lock processQueue")
          ProcessManager.processQueue.synchronized{
            logger.debug("Acquired lock processQueue")
            logger.debug("nb running process is : "+runningProcess)
            if(runningProcess <=maxProcess){
              logger.debug("executing now")
              new ExecutableProcessCMDMessage(processmessage).execute();
              logger.debug("nb running process is now : "+runningProcess)
            }else{
              logger.debug("enqueing")
              ProcessManager.processQueue.enqueue(processmessage)
              logger.debug("process queue lenght is "+processQueue.length)
            }
            logger.debug("Released lock processQueue")
          }

        }
      }
    })
    incomingProcess.start();


    val endedProcess = new Thread(new Runnable {
      override def run(): Unit = {
        val socket = Server.context.socket(ZMQ.PULL)

        socket.bind("inproc://processmanagerremove")

        while (true){
          val exitedProcess:ProcessCMDMessage = socket.recvStr(Charset.defaultCharset())
          logger.debug("receiving process cmd : "+exitedProcess.cmd)
          logger.debug("Waiting for lock processQueue")
          ProcessManager.processQueue.synchronized{
            logger.debug("Acquired lock processQueue")
            runningProcess -= 1
            logger.debug("nb running process is now : "+runningProcess)
            logger.debug("Waiting for lock containerMap")
            ProcessManager.containersmap.synchronized{
              logger.debug("Acquired lock containerMap")
              if(ProcessManager.containersmap.keySet.exists(_==exitedProcess.id.toString)){
                DockerManager.updateServiceStatus(ProcessManager.containersmap.get(exitedProcess.id.toString),exitedProcess.dockerimagename,false)
                ProcessManager.containersmap -= exitedProcess.id.toString
              }
              logger.debug("Released lock containerMap")
            }

            if(runningProcess<=maxProcess){
              if(processQueue.length>0) {
                val processCmd = processQueue.dequeue()
                logger.debug("process queue lenght is "+processQueue.length)
                new ExecutableProcessCMDMessage(processCmd).execute()
                logger.debug("nb running process is now : "+runningProcess)
              }else{
                logger.debug("Waiting for lock abstractQueue")
                var process : AbstractProcess = null;
                abstractProcessQueue.synchronized {
                  logger.debug("Acquired lock abstractQueue")
                  if(abstractProcessQueue.length>0){
                    process = abstractProcessQueue.dequeue()
                  }
                  logger.debug("Released lock abstractQueue")
                }
                if(process != null){
                  logger.debug("running process "+process.moduleval.namespace)
                  process.run()
                }

              }
            }
            logger.debug("Released lock processQueue")
          }

        }
      }
    })
    endedProcess.start()

  }




}


