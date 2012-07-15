package io.photon.app

import io.viper.core.server.file.{StaticFileServerHandler, HttpChunkProxyHandler, FileChunkProxy}
import io.viper.common.{StaticFileContentInfoProviderFactory, ViperServer, NestServer}
import io.viper.core.server.router.{RouteResponse, RouteHandler}
import org.jboss.netty.handler.codec.http.{HttpResponseStatus, HttpVersion, DefaultHttpResponse}


object Main {
  def main(args: Array[String]) {
    NestServer.run(8080, new Main("photon.io", 8080, "/tmp/uploads", "/tmp/thumbs"))
  }
}

class Main(hostname: String, port: Int, uploads: String, thumbs: String) extends ViperServer("res:///photon.io") {
  override def addRoutes {
    val sessions = SimpleTwitterSession.instance

    get("/thumb/$path", new StaticFileServerHandler(StaticFileContentInfoProviderFactory.create(this.getClass, thumbs)))
    get("/d/$path", new StaticFileServerHandler(StaticFileContentInfoProviderFactory.create(this.getClass, uploads)))

    addRoute(new HttpChunkProxyHandler("/u/", new FileChunkProxy(uploads), new FileUploadEventListener(hostname, thumbs, 640, 480)))

    addRoute(new TwitterLogin(sessions, "http://%s:%d/callback".format(hostname, port)))
    addRoute(new TwitterCallback(sessions))
    addRoute(new TwitterLogout(
      new TwitterRouteHandler {
        override
        def exec(session: TwitterSession, args: java.util.Map[String, String]) : RouteResponse = {
          sessions.deleteSession(session.id)
          new RouteResponse(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK))
      }
    },
    sessions))
  }
}
