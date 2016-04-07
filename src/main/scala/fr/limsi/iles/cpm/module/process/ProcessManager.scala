package fr.limsi.iles.cpm.module.process

import java.io.File
import java.nio.charset.Charset
import java.util.UUID
import java.util.concurrent.Executors

import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.module.value.CMDVal
import fr.limsi.iles.cpm.server.Server
import fr.limsi.iles.cpm.utils.ConfManager
import org.zeromq.ZMQ

import scala.collection.mutable
import scala.sys.process.{Process, ProcessLogger}


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

  val socketsend = Server.context.socket(ZMQ.PUSH)
  val socketexit = Server.context.socket(ZMQ.PUSH)

  socketsend.connect("inproc://processmanageradd")
  socketexit.connect("inproc://processmanagerremove")

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
    socketsend.send(message)
  }

  def end():Unit={
    socketexit.send(message)
  }

}


class ExecutableProcessCMDMessage(processcmdmessage:ProcessCMDMessage){
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
      ProcessManager.containersmap.synchronized{
        ProcessManager.containersmap += (processcmdmessage.id.toString -> containername.get)
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
    abstractProcessQueue.synchronized{
      abstractProcessQueue.enqueue(process)
    }
  }

  override def run()={

    // listen to new incomming process
    val executorService = Executors.newSingleThreadExecutor()
    val incomingProcess = executorService.execute(new Runnable {
      override def run(): Unit = {
        val socket = Server.context.socket(ZMQ.PULL)

        socket.bind("inproc://processmanageradd")

        while (true){
          val processmessage :ProcessCMDMessage= socket.recvStr(Charset.defaultCharset())

          ProcessManager.processQueue.synchronized{
            if(runningProcess <=maxProcess){
              new ExecutableProcessCMDMessage(processmessage).execute();
            }else{
              ProcessManager.processQueue.enqueue(processmessage)
            }
          }

        }
      }
    })
    executorService.shutdown();

    val executorService2 = Executors.newSingleThreadExecutor()
    val endedProcess = executorService2.execute(new Runnable {
      override def run(): Unit = {
        val socket = Server.context.socket(ZMQ.PULL)

        socket.bind("inproc://processmanagerremove")

        while (true){
          val exitedProcess:ProcessCMDMessage = socket.recvStr(Charset.defaultCharset())

          ProcessManager.processQueue.synchronized{
            runningProcess -= 1

            ProcessManager.containersmap.synchronized{
              if(ProcessManager.containersmap.keySet.exists(_==exitedProcess.id.toString)){
                DockerManager.updateServiceStatus(ProcessManager.containersmap.get(exitedProcess.id.toString),exitedProcess.dockerimagename,false)
                ProcessManager.containersmap -= exitedProcess.id.toString
              }
            }

            if(runningProcess<=maxProcess){
              if(processQueue.length>0) {
                val processCmd = processQueue.dequeue()
                new ExecutableProcessCMDMessage(processCmd).execute()
              }else{
                val process = abstractProcessQueue.dequeue()
                process.run()
              }
            }
          }

        }
      }
    })
    executorService2.shutdown();

  }




}


