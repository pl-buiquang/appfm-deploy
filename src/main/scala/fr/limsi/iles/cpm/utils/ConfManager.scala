/**
 * Created by buiquang on 9/7/15.
 */
package fr.limsi.iles.cpm.utils

import java.io.{File, FileInputStream}
import org.yaml.snakeyaml.Yaml

object ConfManager {
  var confMap : java.util.Map[String,Any] = null
  val defaultConfFile = "/conf.yml"

  def get(key:String)={
    if(confMap==null){
      init()
    }
    confMap.get(key)
  }

  def init(confPath:String) :Unit={
    val conffile = getClass.getResource(confPath)
    val ios = new FileInputStream(new File(conffile.getFile))
    val yaml = new Yaml()
    confMap = yaml.load(ios).asInstanceOf[java.util.Map[String, Any]]
  }

  def init() :Unit= {
    init(defaultConfFile)
  }
}
