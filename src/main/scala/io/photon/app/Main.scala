package io.photon.app

import io.viper.core.server.file.{StaticFileServerHandler, ThumbnailFileContentInfoProvider, HttpChunkProxyHandler, FileChunkProxy}
import io.viper.common.{StaticFileContentInfoProviderFactory, ViperServer, NestServer}


object Main {
  def main(args: Array[String]) {
    NestServer.run(8080, new Main("localhost", "/tmp/uploads", "/tmp/thumbs"))
  }
}

class Main(hostname: String, uploads: String, thumbs: String) extends ViperServer("res:///photon.io") {
  override def addRoutes {
    addRoute(new HttpChunkProxyHandler("/u/", new FileChunkProxy(uploads), new FileUploadEventListener(hostname, thumbs, 640, 480)))
    get("/thumb/$path", new StaticFileServerHandler(StaticFileContentInfoProviderFactory.create(this.getClass, thumbs)))
    get("/d/$path", new StaticFileServerHandler(StaticFileContentInfoProviderFactory.create(this.getClass, uploads)))
  }
}
