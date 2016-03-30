package fr.limsi.iles.cpm.module.parameter


import fr.limsi.iles.cpm.module.definition.ModuleDef
import fr.limsi.iles.cpm.module.value._
import fr.limsi.iles.cpm.utils.{YamlElt, YamlList, YamlMap, YamlString}






abstract class AbstractModuleParameter{
  type T <: AbstractParameterVal

  var value : Option[T] = None
  var paramType : String
  val desc:Option[String]
  val format:Option[String]
  val schema:Option[String]

  def setVal(yaml:Any,theval:T)={
    value = Some(theval)
    value.get.fromYaml(yaml)
  }

  def createVal(): AbstractParameterVal

  def toAbstractParameterVal():AbstractParameterVal={
    value match{
      case Some(paramval)=>paramval
      case None=>createVal()
    }
  }

  override def toString():String={
    val descin = if(desc.isEmpty){
      ""
    }else{
      "// "+desc.get
    }
    paramType + "( format : " + format.getOrElse("none") + " ; schema : " +schema.getOrElse("none")+ ")" + descin
  }

}

class ModuleParameter[U <: AbstractParameterVal](theparamType:String,override val desc:Option[String], override val format:Option[String], override val schema:Option[String], defaultval : Option[U])(implicit manifest: Manifest[U]) extends AbstractModuleParameter{
  override type T = U
  override var paramType: String = theparamType
  value = defaultval


  def this(theparamType:String,desc:Option[String],format:Option[String],schema:Option[String])(implicit manifest: Manifest[U]) = this(theparamType,desc,format,schema,None)

  /* Problem with LIST[T] , T is lost and newInstance fail on instancing a LIST without parameters
  override def createVal(): U = {
      manifest.runtimeClass.newInstance().asInstanceOf[U]
  }*/
  override def createVal() : AbstractParameterVal ={
    AbstractModuleParameter.createVal(paramType,format,schema)
  }
}

object AbstractModuleParameter{


  def createVal(typestr:String,format:Option[String]=None,schema:Option[String]=None) : AbstractParameterVal ={
    """(\w+)\s*((\+|\*)*)""".r.findFirstMatchIn(typestr) match {
      case Some(matched) => {
        matched.group(2) match {
          case arity:String => {
            if(arity.length==1){
              matched.group(1) match {
                case "VAL" => LIST[VAL](format,schema)
                case "FILE" => LIST[FILE](format,schema)
                case "DIR" => LIST[DIR](format,schema)
                case "CORPUS" => LIST[CORPUS](format,schema)
                case "MODVAL" => LIST[MODVAL](format,schema)
                case _ => throw new Exception("unknown type \"" + typestr + "\"")
              }
            }else if(arity.length==2){
              matched.group(1) match {
                case "VAL" => LIST[LIST[VAL]](format,schema)
                case "FILE" => LIST[LIST[FILE]](format,schema)
                case "DIR" => LIST[LIST[DIR]](format,schema)
                case "CORPUS" => LIST[LIST[CORPUS]](format,schema)
                case "MODVAL" => LIST[LIST[MODVAL]](format,schema)
                case _ => throw new Exception("unknown type \""+typestr+"\"")
              }
            }else if(arity.length==3){
              matched.group(1) match {
                case "VAL" => LIST[LIST[LIST[VAL]]](format,schema)
                case "FILE" => LIST[LIST[LIST[FILE]]](format,schema)
                case "DIR" => LIST[LIST[LIST[DIR]]](format,schema)
                case "CORPUS" => LIST[LIST[LIST[CORPUS]]](format,schema)
                case "MODVAL" => LIST[LIST[LIST[MODVAL]]](format,schema)
                case _ => throw new Exception("unknown type \""+typestr+"\"")
              }
            }else{
              matched.group(1) match {
                case "VAL" => VAL(format,schema)
                case "FILE" => FILE(format,schema)
                case "DIR" => DIR(format,schema)
                case "CORPUS" => CORPUS(format,schema)
                case "MODVAL" => MODVAL(format,schema)
                case _ => throw new Exception("unknown type \"" + typestr + "\"")
              }
            }
          }
          case _ => throw new Exception("problem with match") // should never happen
        }
      }
      case None => throw new Exception("unknown type \""+typestr+"\"")
    }
  }

  def createParam[T <: AbstractParameterVal](
      name : String,
      paramdef:java.util.HashMap[String,Any],
      type_ :Option[String],
      encoding : Option[String],
      desc:Option[String],
      format : Option[String],
      schema : Option[String],
      requireValue:Boolean)(implicit manifest: Manifest[T]) : AbstractModuleParameter =
  {
    val x = new ModuleParameter[T](type_.get,desc,format,schema)
    if(paramdef.getOrDefault("value",null)!=null){
      val thevalue = paramdef.get("value")
      val theobject = AbstractModuleParameter.createVal(type_.get,format,schema).asInstanceOf[T]//manifest.runtimeClass.newInstance().asInstanceOf[T]
      x.setVal(thevalue,theobject);
    }
    else if(requireValue){
      throw new Exception("missing value for param "+name)
    }
    x
  }

  def fromYamlConf(name:String,confobj:Any):AbstractModuleParameter = fromYamlConf(name,confobj,false)

  def fromYamlConf(name:String,confobj:Any,requireValue:Boolean):AbstractModuleParameter={
    val regex = "^"+ModuleDef.variableRegex+"$"
    if(regex.r.findAllMatchIn(name).isEmpty){
      throw new Exception("variable name ("+name+") must match regex '"+regex+"'!")
    }
    YamlElt.fromJava(confobj) match {
      case YamlMap(paramdef) => {
        val type_ = YamlElt.readAs[String](paramdef.get("type"))
        val encoding = YamlElt.readAs[String](paramdef.get("format"))
        val format = YamlElt.readAs[String](paramdef.get("format"))
        val schema = YamlElt.readAs[String](paramdef.get("schema"))
        val desc = YamlElt.readAs[String](paramdef.get("desc"))
        """(\w+)\s*(\+|\*)?""".r.findFirstMatchIn(type_.getOrElse("unknown type")) match {
          case Some(matched) => {
            matched.group(2) match {
              case arity:String => {
                matched.group(1) match {
                  case "VAL" => AbstractModuleParameter.createParam[LIST[VAL]](name,paramdef,type_,encoding,desc,format,schema,requireValue)
                  case "FILE" => AbstractModuleParameter.createParam[LIST[FILE]](name,paramdef,type_,encoding,desc,format,schema,requireValue)
                  case "DIR" => AbstractModuleParameter.createParam[LIST[DIR]](name,paramdef,type_,encoding,desc,format,schema,requireValue)
                  case "CORPUS" => AbstractModuleParameter.createParam[LIST[CORPUS]](name,paramdef,type_,encoding,desc,format,schema,requireValue)
                  case "MODVAL" => AbstractModuleParameter.createParam[LIST[MODVAL]](name,paramdef,type_,encoding,desc,format,schema,requireValue)
                  case _ => throw new Exception("unknown type for input \""+name+"\"")
                }
              }
              case _ => {
                matched.group(1) match {
                  case "VAL" => AbstractModuleParameter.createParam[VAL](name, paramdef, type_, encoding, desc, format, schema, requireValue)
                  case "FILE" => AbstractModuleParameter.createParam[FILE](name, paramdef, type_, encoding, desc, format, schema, requireValue)
                  case "DIR" => AbstractModuleParameter.createParam[DIR](name, paramdef, type_, encoding, desc, format, schema, requireValue)
                  case "CORPUS" => AbstractModuleParameter.createParam[CORPUS](name, paramdef, type_, encoding, desc, format, schema, requireValue)
                  case "MODVAL" => AbstractModuleParameter.createParam[MODVAL](name, paramdef, type_, encoding, desc, format, schema, requireValue)
                  case _ => throw new Exception("unknown type for input \"" + name + "\"")
                }
              }
            }
          }
          case None => throw new Exception("unknown type for input \""+name+"\"")
        }
      }
      case _ => throw new Exception("wrong input definition for "+name)
    }
  }
}


