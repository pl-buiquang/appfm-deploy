package fr.limsi.iles.cpm.utils

import com.mongodb.casbah.Imports._
/**
 * Created by buiquang on 10/7/15.
 */
object DB {
  val dbhost = ConfManager.get("dbhost") match {
    case x:String=>x
    case _ => "localhost"
  }
  val dbport = ConfManager.get("dbport") match {
    case x:String=>x.toInt
    case _ => 27017
  }

  val mongoClient = MongoClient(dbhost, dbport)
  val get = mongoClient(ConfManager.get("dbname").toString)
}
