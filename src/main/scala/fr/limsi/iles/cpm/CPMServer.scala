/**
 * Created by buiquang on 8/27/15.
 */
package fr.limsi.iles.cpm



import fr.limsi.iles.cpm.core.Server
import fr.limsi.iles.cpm.process.{ModuleManager, Module}
import fr.limsi.iles.cpm.utils.ConfManager



object CPMServer{

  def main(args:Array[String]): Unit ={
    args.map(s => println(s))
    val now = new org.joda.time.DateTime()
    println("["+now.toString("hh:mm:ss")+"] : CPM Server Started!")


    /* // save config
    val fos = new OutputStreamWriter(new FileOutputStream(f), "UTF-8")
    props.store(fos, "")
    fos.close()
     */

    /*val module = new Module("Stanford Parser")
    module.exec()
*/


    /*
    load config
    init modules : list modules
    init pipelines : list pipelines
    init corpus : list corpus

    start master server
    start webserver
    start cli server


     */

    ModuleManager.listModules()

    val port = ConfManager.get("listen_port").toString
    println("Listening on port : "+port)
    Server.run(port)
  }

  def test(): Unit = {
    println("test")
  }

}
