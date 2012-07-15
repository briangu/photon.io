package io.photon.app

import io.viper.core.server.file.{StaticFileServerHandler, HttpChunkProxyHandler, FileChunkProxy}
import io.viper.common.{StaticFileContentInfoProviderFactory, ViperServer, NestServer}
import io.viper.core.server.router.{RouteResponse}
import org.jboss.netty.handler.codec.http.{HttpHeaders, HttpResponseStatus, HttpVersion, DefaultHttpResponse}
import org.jboss.netty.handler.codec.http.HttpVersion._


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

    val config = new TwitterConfig("/login", "/logout", "callback", "http://%s:%d/callback".format(hostname, port), sessions)

    addRoute(new TwitterLogin(
      new TwitterRouteHandler {
        override
        def exec(session: TwitterSession, args: java.util.Map[String, String]) : RouteResponse = {
          val response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND)
          response.setHeader("Location", "/")
          new RouteResponse(response)
        }
      },
      config))

    addRoute(new TwitterCallback(config))

    addRoute(new TwitterLogout(
      new TwitterRouteHandler {
        override
        def exec(session: TwitterSession, args: java.util.Map[String, String]) : RouteResponse = {
          sessions.deleteSession(session.id)
          val response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND)
          response.setHeader("Location", "/")
          new RouteResponse(response)
        }
      },
      config))
  }
}
