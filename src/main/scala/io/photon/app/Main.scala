package io.photon.app

import io.stored.server.Node
import io.viper.common.{ViperServer, NestServer}
import io.viper.core.server.router.{JsonResponse, StatusResponse, HtmlResponse, RouteResponse}
import org.jboss.netty.handler.codec.http.{HttpVersion, HttpResponseStatus, DefaultHttpResponse}
import org.jboss.netty.handler.codec.http.HttpVersion._
import twitter4j.ProfileImage
import java.util
import cloudcmd.common.adapters.{Adapter, FileAdapter}
import org.json.{JSONArray, JSONObject}
import java.net.URI
import cloudcmd.common.FileMetaData


object Main {
  val projectionConfig = new JSONObject(FileUtils.readResourceFile(this.getClass, "/config/photon.io/projections.json"));

  def main(args: Array[String]) {
    val storage = Node.createSingleNode("db/photon.io", projectionConfig)
    val adapter = new FileAdapter()
    adapter.init(null, 0, "cache", new util.HashSet[String](), new URI("file:///tmp/uploads"))
    NestServer.run(8080, new Main("photon.io", 8080, storage, adapter))
  }
}

class Main(hostname: String, port: Int, storage: Node, adapter: Adapter) extends ViperServer("/Users/brianguarraci/scm/photon.io/src/main/resources/photon.io") {
  //res:///photon.io") {
  override def addRoutes {
    val sessions = SimpleTwitterSession.instance

    val config = new TwitterConfig("/login", "/logout", "callback", "http://%s:%d/callback".format(hostname, port), sessions)

    addRoute(new TwitterGetRoute(config, "/v", new TwitterRouteHandler {
      override
      def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = {
        var tmp = FileUtils.readFile("/Users/brianguarraci/scm/photon.io/src/main/resources/templates/photon.io/main.html") //FileUtils.readResourceFile(this.getClass, "/templates/photon.io/main.html")
        tmp = tmp.replace("{{dyn-screenname}}", session.twitter.getScreenName)
        tmp = tmp.replace("{{dyn-id}}", session.twitter.getId.toString)
        //        tmp = tmp.replace("{{dyn-data}}", _db.getEvents(user).toString())
        tmp = tmp.replace("{{dyn-title}}", "Hello, %s!".format(session.twitter.getScreenName))
        tmp = tmp.replace("{{dyn-profileimg}}", session.twitter.getProfileImage(session.twitter.getScreenName, ProfileImage.MINI).getURL)
        new HtmlResponse(tmp)
      }
    }))

    // get meta-data
    addRoute(new TwitterGetRoute(config, "/m/$docId", new TwitterRouteHandler {
      override
      def exec(session: TwitterSession, args: util.Map[String, String]): RouteResponse = {
        // TODO: prepared statement
        val result = storage.select("select * from fmd where id in (%s)".format(args.get("docId")))
        if (result == null) {
          new StatusResponse(HttpResponseStatus.NOT_FOUND)
        } else {
          val raw = result(0).toJson
          // TODO: shared-to auth check
          if (raw.getLong("ownerId") == session.twitter.getId) {
            val obj = ResponseUtil.createResponseData(FileMetaData.create(raw), raw.getString("__id"))
            val arr = new JSONArray()
            arr.put(obj)
            new JsonResponse(arr)
          } else {
            new StatusResponse(HttpResponseStatus.FORBIDDEN)
          }
        }
      }
    }))

    addRoute(new TwitterGetRoute(config, "/t/$docId", new TwitterRouteHandler {
      override
      def exec(session: TwitterSession, args: util.Map[String, String]): RouteResponse = {
        // TODO: prepared statement
        val result = storage.select("select * from fmd where id in (%s)".format(args.get("docId")))
        if (result == null) {
          new StatusResponse(HttpResponseStatus.NOT_FOUND)
        } else {
          val raw = result(0).toJson
          // TODO: shared-to auth check
          if (raw.getLong("ownerId") == session.twitter.getId) {
            val is = adapter.loadChannel(raw.getString("thumbnail"))
            val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
            response.setContent(is)
            new RouteResponse(response)
          } else {
            new StatusResponse(HttpResponseStatus.FORBIDDEN)
          }
        }
      }
    }))
    addRoute(new TwitterGetRoute(config, "/d/$docId", new TwitterRouteHandler {
      override
      def exec(session: TwitterSession, args: util.Map[String, String]): RouteResponse = {
        // TODO: prepared statement
        val result = storage.select("select * from fmd where id in (%s)".format(args.get("docId")))
        if (result == null) {
          new StatusResponse(HttpResponseStatus.NOT_FOUND)
        } else {
          val raw = result(0).toJson
          // TODO: shared-to auth check
          if (raw.getLong("ownerId") == session.twitter.getId) {
            val is = adapter.loadChannel(raw.getJSONArray("blocks").getString(0))
            val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
            response.setContent(is)
            new RouteResponse(response)
          } else {
            new StatusResponse(HttpResponseStatus.FORBIDDEN)
          }
        }
      }
    }))

    //  addRoute(new HttpChunkProxyHandler("/u/", new FileChunkProxy(uploads), new FileUploadEventListener(hostname, uploads, thumbs, 640, 480)))
    addRoute(new PostHandler("/u/", sessions, storage, adapter, 640, 480))

    addRoute(new TwitterLogin(
      new TwitterRouteHandler {
        override
        def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = {
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
        def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = {
          sessions.deleteSession(session.id)
          val response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND)
          response.setHeader("Location", "/")
          new RouteResponse(response)
        }
      },
      config))
  }
}
