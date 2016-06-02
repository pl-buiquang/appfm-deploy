package fr.limsi.iles.cpm.utils

import java.util.concurrent.{ExecutorService, Executors}

/**
  * Created by paul on 6/2/16.
  */
object CRONCycle {

  var cycle = 0

  def start()={
    val service: ExecutorService = Executors.newSingleThreadExecutor()
    service.execute(new Runnable {
      override def run(): Unit = {
        routine(cycle)
        Thread.sleep(3600000)
        cycle += 1
      }
    })
    service.shutdown()
  }

  def routine(cycle:Int)={
    if (cycle % 24 == 0){

    }
  }
}


