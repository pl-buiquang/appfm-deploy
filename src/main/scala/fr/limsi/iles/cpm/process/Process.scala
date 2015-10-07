

class Foo{
  def userRequestRunPipeline(module:ModuleDef,env:RunEnv) = {
    val runid = module.run(env)
    println(runid)
  }

  def userRequestStatusPipeline(modulerun,connection){
    val run = runs(modulerun)
    val context = CPM.zmqContext
    val sock = new ZMQSocket(context,PULL)
    val port = run.port
    sock.bind("tcp://localhost:"+port)
    while(connection){
      println(sock.recv())
    }
  }
}

class UserModule{
  val env
  val runitems : List[ModuleVal]
  var completion : Int = 0

  def next(port:String) = {
    runitems.foreach(module => {
      var canRun = true;

      module match {
        case m : UserModule => {
          module.in.foreach(paramname => {
            if(env.args.values.exists(paramname==_)){
              canRun = false;
              break;
            }
          })

          if(canRun){
            val t = new Thread(module.run(UserModule.envinit(env)))
            t.start()
          }
        }
        case MAP =>
        case c : CMD =>{
            if(c.inputs("DOCKERIZED")){
              (c.cmd + "--cpmport "+port)!
            }else{
              ModuleContainer.run(c.cmd,port)
            }
        }

      }
    });
  }

  def run(env) = {
    val context = CPM.zmqContext
    val sock = new ZMQSocket(context,REQ)
    val port = Network.newPort()
    Network.portused = port :: Network.portused
    sock.bind("tcp://*:"+port)

    while(true){

      next(port)

      val message = socket.recv()

      val info = parse(message)

      info match {
        case FINISHED(module) => {
          module.setEnv(env)
          next(port)
          socket.send("are you waiting for my message or just quit after the job?")
        }
        case RUNNING(module) => {
          socket.send("good keep up the work")
        }
        case ERROR(module) => {
          socket.send("i think you're dead now eh?")
        }
        case _ => {
          socket.send("what?? who are you? and what do you mean?")
        }
      }

      setParentEnv()

      if(completion == runitems.size){
        break
      }


    }    
  }

  def parser(message:String) = {
    ...
    val module = modules(modulename)
    ? match {
      case "finished" => 
        FINISHED(module)
      case "running" =>
        RUNNING(module)
      case "erro" =>
        ERROR(module)
    }
  }

}


class Module{
  def run = {
    this 
  }
}

object ModuleContainer{

  def container(cmd:String) = {
    val context = CPM.zmqContext
    val sock = new ZMQSocket(context,REQ)
    sock.send("running:")
    val message = sock.recv()
    cmd !!
    sock.send("finished:")
    sock.recv()
  }

  def run(cmd:String) = {
    val t = new Thread(container(cmd));
    t.start()
  }
}
