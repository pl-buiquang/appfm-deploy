package fr.limsi.iles.cpm.process

import fr.limsi.iles.cpm.utils.ConfManager

/**
 * Created by buiquang on 9/7/15.
 */
object ModuleManager {
  def listModules():Unit = {
    val list : java.util.ArrayList[String] = ConfManager.get("modules_dir").asInstanceOf[java.util.ArrayList[String]]
    var iterator = list.iterator()
    while(iterator.hasNext) {
      println(iterator.next())
    }
  }
}
