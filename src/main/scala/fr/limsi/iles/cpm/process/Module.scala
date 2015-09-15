package fr.limsi.iles.cpm.process

/**
 * Created by buiquang on 9/7/15.
 */
class Module(moduleName:String,definitionFilePath:String) {

  def init(definitionFilePath:String):Unit={

  }

  def exec():Unit = {
    println("Executing "+moduleName)
  }
}
