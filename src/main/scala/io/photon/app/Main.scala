package io.photon.app

import io.viper.core.server.file.{StaticFileServerHandler, ThumbnailFileContentInfoProvider, HttpChunkProxyHandler, FileChunkProxy}
import io.viper.common.{StaticFileContentInfoProviderFactory, ViperServer, NestServer}
import io.viper.core.server.router.{RouteResponse, RouteHandler}
import java.util
import org.jboss.netty.handler.codec.http.{HttpResponseStatus, HttpVersion, DefaultHttpResponse}


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
    addRoute(new TwitterLogin)
    addRoute(new TwitterCallback)
    addRoute(new TwitterLogout(
      new RouteHandler {
        def exec(args: util.Map[String, String]) : RouteResponse = {
//          sessions.deleteSession(sessionKey)
          new RouteResponse(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK))
      }
    }))
  }
}
