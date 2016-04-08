package fr.limsi.iles.cpm.module.value

import java.util.function.Consumer

import com.typesafe.scalalogging.LazyLogging
import fr.limsi.iles.cpm.module.definition.AnonymousDef._
import fr.limsi.iles.cpm.module.definition._
import fr.limsi.iles.cpm.module.parameter._
import fr.limsi.iles.cpm.module.process._
import fr.limsi.iles.cpm.utils.{YamlList, YamlMap, YamlElt}

/**
 * Created by buiquang on 9/7/15.
 */


abstract class AbstractModuleVal(val moduledef:ModuleDef,val conf:Option[java.util.Map[String,Any]]) extends LazyLogging{
  val namespace:String
  val inputs:Map[String,AbstractParameterVal] = AbstractModuleVal.initInputs(moduledef,conf)

  def needsDocker():Boolean

  def getInput(paramName:String,env:RunEnv)={
    inputs(paramName) match {
      case x:AbstractParameterVal => env.resolveValue(x)
      case _ => env.getRawVar(paramName).getOrElse("") match {
        case x:AbstractParameterVal => x
        case _ => throw new Exception("couldn't resolve any value for this input ("+paramName+")")
      }
    }
  }

  /**
   * Indicates if a module can be run provided and execution environment
   * TODO allow infering of previous run result to fill missing inputs if run type allow it
   * @param env
   * @return
   */
  def isExecutable(env:RunEnv):Boolean = {
    inputs.foldLeft(true)((result,input) => {
      val vars = input._2.extractVariables()
      if(!input._2.isExpression()){
        logger.info("variable is not an expression, should check if type match...")
      }
      var exist = true
      vars.foreach(varname => {
        logger.info("Looking for "+varname)
        val varexist = env.getVars().exists(varname == _._1)
        exist = exist && varexist
        if(varexist) logger.info("found") else logger.info("not found")
      })
      exist && result
    })
  }
  def toProcess(parentProcess:Option[AbstractProcess]):AbstractProcess
  def getNbChildren():Int
}


object AbstractModuleVal extends LazyLogging{

  def fromConf(yaml:Any,context:List[AbstractModuleVal],env:Map[String,AbstractModuleParameter]) : AbstractModuleVal= {
    YamlElt.fromJava(yaml) match {
      case YamlMap(moduleval) => {
        if(moduleval.keySet().size()!=1){
          throw new Exception("Module run item error")
        }
        val runitemname = moduleval.keySet().iterator().next()
        val cmdmatch = (ModuleDef.extendedNameRegex+"""(#(?:\w|-)+)?""").r.findFirstMatchIn(runitemname)
        val modulename = cmdmatch match {
          case Some(result) => {
            result.group(1)
          }
          case None => {
            throw new Exception("Error parsing module value name")
          }
        }
        if(ModuleManager.modules.keys.exists(_ == modulename) && !ModuleDef.builtinmodules.contains(modulename)){
          val inputs : java.util.Map[String,Any] = YamlElt.readAs[java.util.HashMap[String,Any]](moduleval.get(runitemname)) match {
            case Some(map) => map
            case None => throw new Exception("malformed module value")
          }
          // TODO check for outputs consistency with module def and multiple variable def
          val modulevaldef = ModuleManager.modules(modulename)
          if(modulevaldef.inputs.filter(input => {
            !inputs.containsKey(input._1) && input._2.value.isEmpty
          }).size==0){
            ModuleVal(
              runitemname,
              modulevaldef,
              Some(inputs)
            )
          }else{
            throw new Exception("required module inputs are not all set for module "+modulename)
          }
        }else{
          val inputs : java.util.Map[String,Any] = YamlElt.readAs[java.util.Map[String,Any]](moduleval.get(runitemname)) match {
            case Some(map) => map
            case None => throw new Exception("malformed module value")
          }
          modulename match {
            case "_CMD" => {
              CMDVal(runitemname,Some(inputs))
            }
            case "_MAP" => {
              MAPVal(runitemname,Some(inputs))
            }
            case "_IF" => {
              IFVal(runitemname,Some(inputs))
            }
            case _ => throw new Exception("unknown run module item")
          }
        }
      }
      case YamlList(list) => {
        var modulelist = List[AbstractModuleVal]()
        list.forEach(new Consumer[Any] {
          override def accept(t: Any): Unit = {
            modulelist ::= AbstractModuleVal.fromConf(t,context,env)
          }
        })
        modulelist = modulelist.reverse
        val anonymousmodule = new AnonymousDef(modulelist,context,env)
        ModuleVal("",anonymousmodule,None)
      }
      case _ => throw new Exception("Malformed run definition")
    }
  }

  def initInputs(definition:ModuleDef,conf:Option[java.util.Map[String,Any]]) :Map[String,AbstractParameterVal] ={
    conf match {
      case Some(yaml) => initInputs(definition,yaml)
      case None => initInputs(definition)
    }
  }

  def initInputs(definition:ModuleDef,conf:java.util.Map[String,Any]) :Map[String,AbstractParameterVal]={
    var inputs = Map[String,AbstractParameterVal]()
    definition.inputs.map(in => {
      val value = in._2.createVal()
      if(conf.containsKey(in._1)){
        value.fromYaml(conf.get(in._1))
      }else if(!in._2.value.isEmpty){
        value.fromYaml(in._2.value.get.toYaml())
      }else{
        throw new Exception("missing input value "+in._1)
      }
      inputs += (in._1 -> value)
    })
/*
    val paramnames = conf.keySet()
    val it = paramnames.iterator()
    while(it.hasNext){
      val paramname = it.next()
      if(!definition.inputs.contains(paramname)){
        logger.warn("Warning, "+definition.name+" moduledef does not contain this input "+paramname)
      }else{
        val value = definition.inputs(paramname).createVal()
        value.parseYaml(conf.get(paramname))
        inputs += (paramname -> value)
      }
    }*/
    inputs
  }


  def initInputs(definition:ModuleDef):Map[String,AbstractParameterVal]={
    var x = Map[String,AbstractParameterVal]()
    definition.inputs.map(in => {
      val value = in._2.createVal()
      value.fromYaml("$"+in._1)
      x += (in._1 -> value)
    })
    x
  }

}



case class ModuleVal(override val namespace:String,override val moduledef:ModuleDef,override val conf:Option[java.util.Map[String,Any]]) extends AbstractModuleVal(moduledef,conf){

  override def toProcess(parentProcess:Option[AbstractProcess]): AbstractProcess = {
    new ModuleProcess(new ModuleVal(namespace,this.moduledef,conf),parentProcess)

  }

  override def needsDocker(): Boolean = {
    this.moduledef.needsDocker()
  }

  override def getNbChildren(): Int = {
    this.moduledef.exec.foldLeft(0)((agg,modval)=>{
      agg+modval.getNbChildren()
    })
  }
}



case class CMDVal(override val namespace:String,override val conf:Option[java.util.Map[String,Any]]) extends AbstractModuleVal(CMDDef,conf){


  override def toProcess(parentProcess:Option[AbstractProcess]): AbstractProcess = {
    new CMDProcess(new CMDVal(namespace,conf),parentProcess)

  }

  override def needsDocker(): Boolean = {
    inputs("DOCKERFILE").toYaml() match {
      case x :String => {
        if(x!="false"){
          true
        }else{
          false
        }
      }
      case _ =>  false
    }
  }

  override def getNbChildren(): Int = 1
}


case class IFVal(override val namespace:String,override val conf:Option[java.util.Map[String,Any]]) extends AbstractModuleVal(IFDef,conf){
  override def toProcess(parentProcess: Option[AbstractProcess]): AbstractProcess = {
    new IFProcess(new IFVal(namespace,conf),parentProcess)
  }

  override def isExecutable(env: RunEnv): Boolean = {
    inputs.foldLeft(true)((result, input) => {
      val vars = if (input._1 == "THEN" || input._1 == "ELSE") {
        var outervariables = MAPVal.extractVarsFromModuleVals(input._2.asInstanceOf[LIST[MODVAL]], Map[String, AbstractModuleParameter]())
        /*Array[String]()
      var innervariables = Map[String,AbstractModuleParameter]()
      val modulelist = AbstractParameterVal.paramToScalaListModval(input._2.asInstanceOf[LIST[MODVAL]])
      val implicitvars = List("_","_RUN_DIR","_DEF_DIR","_CUR_MOD","_MOD_CONTEXT")
      modulelist.foreach(moduleval => {
        moduleval.inputs.foreach(input => {
          logger.debug("looking inner variable "+input._1+" of type "+input._2._mytype)
          if (input._2._mytype.startsWith("MODVAL")){
            val submodules = LIST[MODVAL](None,None)
            submodules.fromYaml(input._2.toYaml())
            outervariables = outervariables ++ MAPVal.extractVarsFromModuleVals(submodules,innervariables)
          }else{
            val variables = input._2.extractVariables()
            variables.foreach(variable => {
              if(!innervariables.contains(variable) && !implicitvars.contains(variable)){
                outervariables = outervariables :+ variable
              }
            })
          }
        })
        moduleval.moduledef.outputs.foreach(output => {
          innervariables += (moduleval.namespace+"."+output._1 -> output._2)
          logger.debug("added "+moduleval.namespace+"."+output._1)
        })
      })*/
        outervariables
      } else {
        input._2.extractVariables()
      }
      var exist = true
      vars.foreach(varname => {
        logger.info("Looking for " + varname)
        val varexist = env.getVars().exists(varname == _._1)
        exist = exist && varexist
        if (varexist) logger.info("found") else logger.info("not found")
      })
      exist && result
    })
  }

  override def needsDocker(): Boolean = {

    AbstractParameterVal.paramToScalaListModval(inputs("ELSE").asInstanceOf[LIST[MODVAL]]).foldLeft(false)((valence,modval)=>{
      valence || modval.needsDocker()
    }) || AbstractParameterVal.paramToScalaListModval(inputs("THEN").asInstanceOf[LIST[MODVAL]]).foldLeft(false)((valence,modval)=>{
      valence || modval.needsDocker()
    })
  }

  override def getNbChildren(): Int = 1 // this is a anonymous module
}

object MAPVal extends LazyLogging{
  def extractVarsFromModuleVals(modulevals:LIST[MODVAL],innervars:Map[String,AbstractModuleParameter]):Array[String]={
    var outervariables = Array[String]()
    var innervariables = innervars
    val modulelist = AbstractParameterVal.paramToScalaListModval(modulevals)
    val implicitvars = List("_","_RUN_DIR","_DEF_DIR","_CUR_MOD","_MOD_CONTEXT")
    modulelist.foreach(moduleval => {
      moduleval.inputs.foreach(input => {
        //logger.debug("looking inner variable "+input._1+" of type "+input._2._mytype)
        if (input._2._mytype.startsWith("MODVAL")){
          val submodules = LIST[MODVAL](None,None)
          submodules.fromYaml(input._2.toYaml())
          outervariables = outervariables ++ MAPVal.extractVarsFromModuleVals(submodules,innervariables)
        }else{
          val variables = input._2.extractVariables()
          variables.foreach(variable => {
            if(!innervariables.contains(variable) && !implicitvars.contains(variable)){
              outervariables = outervariables :+ variable
            }
          })
        }
      })
      moduleval.moduledef.outputs.foreach(output => {
        innervariables += (moduleval.namespace+"."+output._1 -> output._2)
        //logger.debug("added "+moduleval.namespace+"."+output._1)
      })
    })
    outervariables
  }
}

case class MAPVal(override val namespace:String,override val conf:Option[java.util.Map[String,Any]]) extends AbstractModuleVal(MAPDef,conf){


  override def toProcess(parentProcess:Option[AbstractProcess]): AbstractProcess = {
    new MAPProcess(new MAPVal(namespace,conf),parentProcess)
  }

  override def isExecutable(env: RunEnv): Boolean = {
    inputs.foldLeft(true)((result,input) => {
      val vars = if(input._1 == "RUN"){
        var outervariables = MAPVal.extractVarsFromModuleVals(input._2.asInstanceOf[LIST[MODVAL]],Map[String,AbstractModuleParameter]())
          /*Array[String]()
        var innervariables = Map[String,AbstractModuleParameter]()
        val modulelist = AbstractParameterVal.paramToScalaListModval(input._2.asInstanceOf[LIST[MODVAL]])
        val implicitvars = List("_","_RUN_DIR","_DEF_DIR","_CUR_MOD","_MOD_CONTEXT")
        modulelist.foreach(moduleval => {
          moduleval.inputs.foreach(input => {
            logger.debug("looking inner variable "+input._1+" of type "+input._2._mytype)
            if (input._2._mytype.startsWith("MODVAL")){
              val submodules = LIST[MODVAL](None,None)
              submodules.fromYaml(input._2.toYaml())
              outervariables = outervariables ++ MAPVal.extractVarsFromModuleVals(submodules,innervariables)
            }else{
              val variables = input._2.extractVariables()
              variables.foreach(variable => {
                if(!innervariables.contains(variable) && !implicitvars.contains(variable)){
                  outervariables = outervariables :+ variable
                }
              })
            }
          })
          moduleval.moduledef.outputs.foreach(output => {
            innervariables += (moduleval.namespace+"."+output._1 -> output._2)
            logger.debug("added "+moduleval.namespace+"."+output._1)
          })
        })*/
        outervariables
      }else{
        input._2.extractVariables()
      }
      var exist = true
      vars.foreach(varname => {
        logger.info("Looking for "+varname)
        val varexist = env.getVars().exists(varname == _._1)
        exist = exist && varexist
        if(varexist) logger.info("found") else logger.info("not found")
      })
      exist && result
    })

  }

  override def needsDocker(): Boolean = {

    AbstractParameterVal.paramToScalaListModval(inputs("RUN").asInstanceOf[LIST[MODVAL]]).foldLeft(false)((valence,modval)=>{
      valence || modval.needsDocker()
    })
  }

  override def getNbChildren(): Int = 1
}

