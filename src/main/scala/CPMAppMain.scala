/**
 * Created by buiquang on 8/27/15.
 */
object CPMAppMain{

  def main(args:Array[String]): Unit ={
    args.map(s => println(s));
    val now = new org.joda.time.DateTime()
    println("["+now.toString("hh:mm:ss")+"] : CPM Server Started!")
  }

  def test(): Unit={
    println("test");
  }

}
