package fr.limsi.iles.cpm

import fr.limsi.iles.cpm.module.definition.{ModTree, ModNode, ModuleManager}
import fr.limsi.iles.cpm.utils.Utils
import org.json.JSONObject

import scala.sys.process.Process

/**
 * Created by buiquang on 10/7/15.
 */
object ScratchTests extends App{


  override def main (args: Array[String]) {
    val fw = new Utils.FileWalker(new java.io.File("/people/buiquang/projects/pulsar/ner_tools"))
    while(fw.hasMore){

      System.out.println(fw.take().getCanonicalPath)
    }
  }

}
