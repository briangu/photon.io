package io.photon.app

import io.stored.server.common.Record
import io.stored.server.Node
import io.viper.common.{ViperServer, NestServer}
import io.viper.core.server.router._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpVersion._
import cloudcmd.common.adapters.{Adapter, FileAdapter}
import org.json.{JSONArray, JSONObject}
import java.net.URI
import cloudcmd.common.FileMetaData
import com.thebuzzmedia.imgscalr.AsyncScalr


object Photon {
  val projectionConfig = new JSONObject(FileUtils.readResourceFile(this.getClass, "/config/photon.io/projections.json"))

  def main(args: Array[String]) {
    AsyncScalr.setServiceThreadCount(20) // TODO: set via config
    val storage = Node.createSingleNode("db/photon.io", projectionConfig)
    val adapter = new FileAdapter()
    adapter.init(null, 0, "cache", new java.util.HashSet[String](), new URI("file:///Users/brianguarraci/uploads"))
    NestServer.run(8080, new Photon("photon.io", 8080, storage, adapter))
  }
}

class Photon(hostname: String, port: Int, storage: Node, adapter: Adapter)
  extends ViperServer("res:///photon.io") {

  final val PAGE_SIZE = 25
  final val MAIN_TEMPLATE = FileUtils.readResourceFile(this.getClass, "/templates/photon.io/main.html")

  override def addRoutes {
    val sessions = SimpleTwitterSession.instance

    val config = new TwitterConfig("/login", "/logout", "callback", "http://%s:%d/callback".format(hostname, port), sessions)

    addRoute(new TwitterGetRoute(config, "/v", new TwitterRouteHandler {
      override
      def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = loadPage(session, 0, PAGE_SIZE)
    }))
    addRoute(new TwitterGetRoute(config, "/v/$page", new TwitterRouteHandler {
      override
      def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = loadPage(session, (math.max(args.get("page").toInt-1, 0)) * PAGE_SIZE, PAGE_SIZE)
    }))

    addRoute(new TwitterGetRoute(config, "/j/$page", new TwitterRouteHandler {
      override
      def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = {
        new JsonResponse(
          toJsonArray(
            session,
            getChronResults(
              storage,
              session.twitter.getScreenName,
              PAGE_SIZE,
              (math.max(args.get("page").toInt-1, 0)) * PAGE_SIZE)))
      }
    }))

    addRoute(new TwitterGetRoute(config, "/m/$docId", new TwitterRouteHandler {
      override
      def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = {
        val meta = getMetaById(args.get("docId"))
        if (meta == null) {
          new StatusResponse(HttpResponseStatus.NOT_FOUND)
        } else {
          if (ModelUtil.hasReadPriv(meta, session.twitter.getScreenName)) {
            val obj = ModelUtil.createResponseData(session, FileMetaData.create(meta), meta.getString("__id"))
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
      def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = {
        val meta = getMetaById(args.get("docId"))
        if (meta == null) {
          new StatusResponse(HttpResponseStatus.NOT_FOUND)
        } else {
          if (ModelUtil.hasReadPriv(meta, session.twitter.getScreenName)) {
            buildResponse(adapter, meta.getString("thumbHash"), meta.getString("type"))
          } else {
            new StatusResponse(HttpResponseStatus.FORBIDDEN)
          }
        }
      }
    }))
    addRoute(new TwitterGetRoute(config, "/d/$docId", new TwitterRouteHandler {
      override
      def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = {
        val meta = getMetaById(args.get("docId"))
        if (meta == null) {
          new StatusResponse(HttpResponseStatus.NOT_FOUND)
        } else {
          if (ModelUtil.hasReadPriv(meta, session.twitter.getScreenName)) {
            buildResponse(adapter, meta.getJSONArray("blocks").getString(0), meta.getString("type"))
          } else {
            new StatusResponse(HttpResponseStatus.FORBIDDEN)
          }
        }
      }
    }))

    addRoute(new TwitterPostRoute(config, "/shares", new TwitterRouteHandler {
      override
      def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = {
        if (!args.containsKey("sharees")) return new StatusResponse(HttpResponseStatus.BAD_REQUEST)
        if (!args.containsKey("ids")) return new StatusResponse(HttpResponseStatus.BAD_REQUEST)
        if (!args.containsKey("sharemsg")) return new StatusResponse(HttpResponseStatus.BAD_REQUEST)

        val docIds = args.get("ids").split(',')
        var metas = getMetaListById(docIds.toList)
        if (metas.length != docIds.length) return new StatusResponse(HttpResponseStatus.BAD_REQUEST)
        metas = metas.filter(m => ModelUtil.hasSharePriv(m.rawData, session.twitter.getScreenName))
        if (metas.length != docIds.length) return new StatusResponse(HttpResponseStatus.FORBIDDEN)

        val sharees = args.get("sharees").split(',')
        val shareMsg = args.get("sharemsg")

        val arr = new JSONArray()

        // TODO: validate sharees are mutually following
        // TODO: dm

        metas.foreach{ meta =>
          sharees.foreach{ sharee =>
            val reshareMeta = ModelUtil.reshareMeta(meta.rawData, sharee)
            val fmd = FileMetaData.create(reshareMeta)
            val docId = storage.insert("fmd", fmd.toJson.getJSONObject("data"))
            arr.put(ModelUtil.createResponseData(session, reshareMeta, docId))
          }
        }

        new JsonResponse(arr)
      }
    }))

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

  private def getMetaById(id: String) : JSONObject = {
    val result = storage.select("select * from fmd where hash = '%s'".format(id)) // TODO: use prepared statements
    if (result == null || result.size == 0) {
      null
    } else {
      result(0).toJson
    }
  }

  private def getMetaListById(ids: List[String]) : List[Record] = {
    val ws = ids.map("'%s'".format(_)).mkString(",")
    val result = storage.select("select * from fmd where hash in (%s)".format(ws)) // TODO: use prepared statements
    if (result == null || result.size == 0) {
      List()
    } else {
      result
    }
  }

  private def buildResponse(adapter: Adapter, hash: String, contentType: String) : RouteResponse = {
    val is = adapter.loadChannel(hash)
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.setHeader(HttpHeaders.Names.CONTENT_TYPE, contentType)
    response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, is.capacity())
    response.setContent(is)
    new RouteResponse(response)
  }

  private def loadPage(session: TwitterSession, pageIdx: Int, countPerPage: Int) : RouteResponse = {
    var tmp = MAIN_TEMPLATE.toString
    tmp = tmp.replace("{{dyn-screenname}}", session.twitter.getScreenName)
    tmp = tmp.replace("{{dyn-id}}", session.twitter.getId.toString)
    tmp = tmp.replace("{{dyn-data}}", toJsonArray(session, getChronResults(storage, session.twitter.getScreenName, countPerPage, pageIdx)).toString())
    tmp = tmp.replace("{{dyn-title}}", "Hello, %s!".format(session.twitter.getScreenName))
    tmp = tmp.replace("{{dyn-profileimg}}", session.getProfileImageUrl(session.twitter.getScreenName))
    new HtmlResponse(tmp)
  }

  private def toJsonArray(session: TwitterSession, records: List[Record]) : JSONArray = {
    val arr = new JSONArray()
    records.foreach{r : Record => arr.put(ModelUtil.createResponseData(session, r.rawData, r.id)) }
    arr
  }

  private def getSearchResults(storage: Node, ownerId: String, tags: String, count: Int, offset: Int) : List[Record] = {
//    val sql = "SELECT T.HASH,T.RAWMETA FROM FT_SEARCH_DATA('%s', 0, 0) FT, DATA_INDEX T WHERE FT.TABLE='DATA_INDEX' AND T.HASH = FT.KEYS[0] AND T.OWNERID = '%s".format(tags, ownerId)
//    storage.search(tags, ")
//    storage.select(sql)
    null
  }

  private def getChronResults(storage: Node, ownerId: String, count: Int, offset: Int = 0) : List[Record] = {
    storage.select("select * from fmd where ownerId = '%s' order by filedate DESC limit %d OFFSET %d".format(ownerId.toLowerCase, count, offset))
  }
}
