class Variable(val name:String,val value:AbstractType){

}

abstract class AbstractType(
  implicit val defaultformat = "yaml",
  implicit val defaultencoding = "utf-8",
  implicit val defaultschema = "cpm-model"
  )
{
  def asVAL():VAL
  def fromVAL(value:VAL):Unit
  var format : String = _ 
  var encoding : String = _
  var schema : String = _
}

case class VAL() extends AbstractType (
  implicit val defaultValue = None
  )
{ // YAML, YAMLVAL, STRING, VAR
  override var value : Option[YamlElt] = _

  def toString() : String = {
    YamlElt.toString()
  }

  override def asVAL():VAL={
    VAL()
  }

  override def fromVAL(value:VAL):Unit={
    VAL()
  }
}

case class FILE() extends AbstractType {
  override def asVAL():VAL={
    VAL()
  }

  override def fromVAL(value:VAL):Unit={
    FILE()
  }
}

case class DIR() extends AbstractType{
  override def asVAL():VAL={
    VAL()
  }

  override def fromVAL(value:VAL):Unit={
    FILE()
  }

}

case class CORPUS() extends AbstractType{
  override def asVAL():VAL={
    VAL()
  }

  override def fromVAL(value:VAL):Unit={
    FILE()
  }

}

case class DB() extends AbstractType{
  override def asVAL():VAL={
    VAL()
  }

  override def fromVAL(value:VAL):Unit={
    FILE()
  }

}

case class MODVAL() extends AbstractType {
  var moduledef = _


  override def asVAL():VAL={
    VAL()
  }

  override def fromVAL(value:VAL):Unit={
    val moduledef = ModuleDef.fromConf(value)

    var moduleval = moduledef.toMODVAL(value)
  }

  def fromModuleDef(moduledef:ModuleDef)

  def exec(parentEnv:RunEnv,parentPort:String):UUID // processid, pid

  def toProcess():RunProcess
}

case class LIST[T <: AbstractType](implicit manifest: Manifest[T]) extends AbstractType{
  override def asVAL():VAL={
    VAL()
  }

  override def fromVAL(value:VAL):Unit={
    LIST[T]()
  }
}

case class ModuleList() extends LIST[MODULE] {
  def toProcess():RunProcess = {
    val x = new MODVAL()
    x.fromModuleDef(new AnonymousModuleDef(exec=this))
    x.toProcess
  } 
}

case class MAP[T <: AbstractType](implicit manifest: Manifest[T]) extends AbstractType{
  override def asVAL():VAL={
    VAL()
  }

  override def fromVAL(value:VAL):Unit={
    MAP[T]()
  } 
}

/**
cpm run "module" conf.yaml --async (True|False)
val process = ModuleManager.get("module").toMODVAL().toProcess()
val pid = process.run(conf.yaml,async=True|False)
print pid

cpm process "pid" status
val process = ProcessManager.get("pid")
print process.status

cpm process "pid" results
val process = ProcessManager.get("pid")
print(process.env) // pretty

cpm process "pid" view  ["viewname"] "variablepath"
val process = ProcessManager.get("pid")
val value = process.env.resolveVar("variablepath")
if("viewname")
  ViewManager.get("viewname").view(value) // like a module but allow launching anything like a webserver and a navigator to open the webpages served
else{
  ViewManager.view(value)  
}

*/
abstract class RunProcess {
  val uuid:UUID
  val module:MODVAL
  var conf: MAP[VAL]
  var async : Boolean
  var parentPort : String
  var hostport:String
  var status : String // running | returncode
  var rawlog : List[String]
  var env : MAP[AbstractType]

  def run(conf:MAP[VAL])={
    if (async)
      threadpool.start(supervisor_)
    else
      supervisor()
  }

  def supervisor() : Any = {
    socker = context.socket("PULL")
    while(){

      message.recv() match {
        case x : RUNNING => {

        }
        case x : FINISHED => {

        }
      }
    }
  }

  def next() 

  def fromID(uuid:UUID):RunProcess
}

class ModuleProcess extends RunProcess{
  override def next()
}

class CMDProcess extends RunProcess{
  override def next()
}

class MAPProcess extends RunProcess{
  override def next()={
    module.inputs("run").foreach(module => {
      val process =module.toProcess()
      process.run(conf);
      if(!async){
        socket.recv()
      }
      })
  }
}

trait ModuleDef(){ // name, inputs, outputs, exec 
  def toMODVAL(conf:VAL):MODVAL = { // YAMLElt

  }
}

