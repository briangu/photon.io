package io.photon.app

import cloudcmd.common.engine.{DefaultFileProcessor, H2IndexStorage, ParallelCloudEngine}
import cloudcmd.common.config.JsonConfigStorage
import com.thebuzzmedia.imgscalr.AsyncScalr

object CloudServices {
  val ConfigService = new JsonConfigStorage
  val CloudEngine = new ParallelCloudEngine(ConfigService)
  val IndexStorage = new H2IndexStorage(CloudEngine)
  val FileProcessor = new DefaultFileProcessor(ConfigService, CloudEngine, IndexStorage, 640, 480)
  val TwitterStream = new TwitterStreamClient(new MediaTweetProcessor(CloudEngine, IndexStorage))

  def init(configRoot: String) {
    ConfigService.init(configRoot)
    CloudEngine.init
    IndexStorage.init(configRoot)
    TwitterStream.init()

//    AsyncScalr.setServiceThreadCount(2) // TODO: set via config

/*
    println("refreshing adapter caches")
    CloudEngine.refreshCache()

    println("initializing adapters with describe()")
    CloudEngine.describe()

    println("reindexing index storage")
    IndexStorage.reindex()
*/

    println("ready!")
  }

  def shutdown() {
    IndexStorage.shutdown
    CloudEngine.shutdown
    ConfigService.shutdown
    TwitterStream.shutdown()

    if (AsyncScalr.getService != null) {
      AsyncScalr.getService.shutdownNow
    }
  }
}
