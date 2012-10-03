package io.photon.app

import cloudcmd.common.engine.{DefaultFileProcessor, H2IndexStorage, ParallelCloudEngine}
import cloudcmd.common.config.JsonConfigStorage
import com.thebuzzmedia.imgscalr.AsyncScalr

object CloudServices {
  val TwitterStream = new TwitterStreamClient()
  val CollectionsStorage = new CollectionsStorage()

  def init(configRoot: String) {
    TwitterStream.init()
    CollectionsStorage.init(".")

    println("ready!")
  }

  def shutdown() {
    TwitterStream.shutdown()
  }
}
