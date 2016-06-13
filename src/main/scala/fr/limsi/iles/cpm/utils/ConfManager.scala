/**
 * Created by buiquang on 9/7/15.
 */
package fr.limsi.iles.cpm.utils

import java.io.{File, FileInputStream}
import org.yaml.snakeyaml.Yaml

object ConfManager {
  val defaultDockerBaseImage = "base_cpm_shell"
  val moduleDefinitionDirectory = "_DEF_DIR"
  val runWorkingDirectory = "_RUN_DIR"

  var confMap : java.util.Map[String,Any] = null
  val defaultConfFile = "/conf.yml"

  def get(key:String)={
    if(confMap==null){
      init()
    }
    if(confMap.containsKey(key)){
      confMap.get(key)
    }else{
      throw new Exception(key+" was not found in configuration!")
    }
  }

  /**
   * Initialize ConfManager from a yaml file, default to conf.yml
   * @param confPath
   */
  def init(confPath:String) :Unit={
    val conffile = getClass.getResource(confPath)
    if(conffile==null){

    }
    var filename : String = null
    if(conffile == null){
      filename = confPath
    }else{
      filename = conffile.getFile;
    }
    val file = new File(filename)
    val input :java.io.InputStream = if(file.exists()){
      new FileInputStream(filename)
    }else{
      getClass.getResourceAsStream(confPath); // when in jar
    }

    val yaml = new Yaml()
    confMap = yaml.load(input).asInstanceOf[java.util.Map[String, Any]]
  }

  def init() :Unit= {
    init(defaultConfFile)
  }
}
