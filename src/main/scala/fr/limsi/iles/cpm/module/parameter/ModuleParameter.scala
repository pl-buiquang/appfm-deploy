package fr.limsi.iles.cpm.module.parameter


import fr.limsi.iles.cpm.module.value._
import fr.limsi.iles.cpm.utils.{YamlElt, YamlList, YamlMap, YamlString}






abstract class AbstractModuleParameter{
  type T <: AbstractParameterVal

  var value : Option[T] = None
  var paramType : String

  def setVal(yaml:Any,theval:T)={
    value = Some(theval)
    value.get.parseYaml(yaml)
  }

  def createVal(): AbstractParameterVal
}

class ModuleParameter[U <: AbstractParameterVal](theparamType:String,val desc:Option[String],  val format:Option[String], val schema:Option[String], defaultval : Option[U])(implicit manifest: Manifest[U]) extends AbstractModuleParameter{
  override type T = U
  override var paramType: String = theparamType
  value = defaultval

  def this(theparamType:String,desc:Option[String],format:Option[String],schema:Option[String])(implicit manifest: Manifest[U]) = this(theparamType,desc,format,schema,None)

  /* Problem with LIST[T] , T is lost and newInstance fail on instancing a LIST without parameters
  override def createVal(): U = {
      manifest.runtimeClass.newInstance().asInstanceOf[U]
  }*/
  override def createVal() : AbstractParameterVal ={
    """(\w+)\s*(\+|\*)?""".r.findFirstMatchIn(paramType) match {
      case Some(matched) => {
        matched.group(2) match {
          case arity:String => {
            matched.group(1) match {
              case "VAL" => LIST[VAL]()
              case "FILE" => LIST[FILE]()
              case "DIR" => LIST[DIR]()
              case "CORPUS" => LIST[CORPUS]()
              case "MODULE" => LIST[MODVAL]()
              case _ => throw new Exception("unknown type \""+paramType+"\"")
            }
          }
          case _ => {
            matched.group(1) match {
              case "VAL" => VAL()
              case "FILE" => FILE()
              case "DIR" => DIR()
              case "CORPUS" => CORPUS()
              case "MODULE" => MODVAL()
              case _ => throw new Exception("unknown type \"" + paramType + "\"")
            }
          }
        }
      }
      case None => throw new Exception("unknown type \""+paramType+"\"")
    }
  }
}

object AbstractModuleParameter{
  def createVal(typestr:String) : AbstractParameterVal ={
    """(\w+)\s*(\+|\*)?""".r.findFirstMatchIn(typestr) match {
      case Some(matched) => {
        matched.group(2) match {
          case arity:String => {
            matched.group(1) match {
              case "VAL" => LIST[VAL]()
              case "FILE" => LIST[FILE]()
              case "DIR" => LIST[DIR]()
              case "CORPUS" => LIST[CORPUS]()
              case "MODULE" => LIST[MODVAL]()
              case _ => throw new Exception("unknown type \""+typestr+"\"")
            }
          }
          case _ => {
            matched.group(1) match {
              case "VAL" => VAL()
              case "FILE" => FILE()
              case "DIR" => DIR()
              case "CORPUS" => CORPUS()
              case "MODULE" => MODVAL()
              case _ => throw new Exception("unknown type \"" + typestr + "\"")
            }
          }
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
      x.setVal(paramdef.get("value"),manifest.runtimeClass.newInstance().asInstanceOf[T]);
    }
    else if(requireValue){
      throw new Exception("missing value for param "+name)
    }
    x
  }

  def fromYamlConf(name:String,confobj:Any):AbstractModuleParameter = fromYamlConf(name,confobj,false)

  def fromYamlConf(name:String,confobj:Any,requireValue:Boolean):AbstractModuleParameter={
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
                  case "MODULE" => AbstractModuleParameter.createParam[LIST[MODVAL]](name,paramdef,type_,encoding,desc,format,schema,requireValue)
                  case _ => throw new Exception("unknown type for input \""+name+"\"")
                }
              }
              case _ => {
                matched.group(1) match {
                  case "VAL" => AbstractModuleParameter.createParam[VAL](name, paramdef, type_, encoding, desc, format, schema, requireValue)
                  case "FILE" => AbstractModuleParameter.createParam[FILE](name, paramdef, type_, encoding, desc, format, schema, requireValue)
                  case "DIR" => AbstractModuleParameter.createParam[DIR](name, paramdef, type_, encoding, desc, format, schema, requireValue)
                  case "CORPUS" => AbstractModuleParameter.createParam[CORPUS](name, paramdef, type_, encoding, desc, format, schema, requireValue)
                  case "MODULE" => AbstractModuleParameter.createParam[MODVAL](name, paramdef, type_, encoding, desc, format, schema, requireValue)
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


