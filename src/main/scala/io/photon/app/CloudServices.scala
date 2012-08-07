package io.photon.app

import cloudcmd.common.engine.{H2IndexStorage, ParallelCloudEngine}
import cloudcmd.common.config.JsonConfigStorage

object CloudServices {
  val ConfigService = new JsonConfigStorage
  val CloudEngine = new ParallelCloudEngine(ConfigService)
  val IndexStorage = new H2IndexStorage(CloudEngine)

  def init(configRoot: String) {
    ConfigService.init(configRoot)
    CloudEngine.init
    IndexStorage.init(configRoot)
  }

  def shutdown() {
    IndexStorage.shutdown
    CloudEngine.shutdown
    ConfigService.shutdown
  }
}
