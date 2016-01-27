package fr.limsi.iles.cpm

import fr.limsi.iles.cpm.module.definition.{ModTree, ModNode, ModuleManager}
import org.json.JSONObject

import scala.sys.process.Process

/**
 * Created by buiquang on 10/7/15.
 */
/*object ScratchTests extends App{

  override def main (args: Array[String]) {
    Process("""ping anityatva.net""").run
  }

}*/
/*
object ScratchTests extends App{

  override def main (args: Array[String]) {
    val baseTree = ModNode("",List[ModTree]())

    val r1 = ModuleManager.insertInModuleTree("foo","/vagrant/modules/default",baseTree,"")

    val r2 = ModuleManager.insertInModuleTree("bar","/vagrant/modules/default",r1.get,"")

    val r3 = ModuleManager.insertInModuleTree("biz","/vagrant/modules/",r2.get,"")

    val r4 = ModuleManager.insertInModuleTree("end","/srv/modules",r3.get,"")

    print(ModuleManager.jsonTreeExport(r4.get).asInstanceOf[JSONObject].toString(2))
  }

}*/
