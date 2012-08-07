package io.photon.app

import io.stored.server.common.Record
import io.stored.server.Node
import io.viper.common.{ViperServer, NestServer}
import io.viper.core.server.router._
import io.viper.core.server.router.RouteResponse.RouteResponseDispose
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpVersion._
import org.json.{JSONArray, JSONObject}
import com.thebuzzmedia.imgscalr.AsyncScalr
import cloudcmd.common._
import engine.IndexStorage
import java.io.File
import java.net.InetAddress
import util.JsonUtil


object Photon {
  val projectionConfig = new JSONObject(FileUtils.readResourceFile(this.getClass, "/config/photon.io/projections.json"))

  def getIpAddress: String = {
    InetAddress.getLocalHost.getHostAddress
  }

  def main(args: Array[String]) {
    val ipAddress = getIpAddress
    val port = 8080
    println("booting at http://%s:%d".format(ipAddress, port))

    AsyncScalr.setServiceThreadCount(20) // TODO: set via config

    var configRoot: String = FileUtil.findConfigDir(FileUtil.getCurrentWorkingDirectory, ".cld")
    if (configRoot == null) {
      configRoot = System.getenv("HOME") + File.separator + ".cld"
      new File(configRoot).mkdir
    }

    CloudServices.init(configRoot)

    try {
     // val storage = Node.createSingleNode("db/photon.io", projectionConfig)
      NestServer.run(port, new Photon(ipAddress, port, CloudServices.IndexStorage, CloudServices.CloudEngine))
    } finally {
      CloudServices.shutdown
    }
  }
}

class Photon(hostname: String, port: Int, storage: IndexStorage, cas: ContentAddressableStorage) extends ViperServer("res:///photon.io") {

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
          resultsToJsonArray(
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
          if (ModelUtil.hasReadPriv(meta.getRawData, session.twitter.getScreenName)) {
            val obj = ModelUtil.createResponseData(session, meta, meta.getHash)
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
          if (ModelUtil.hasReadPriv(meta.getRawData, session.twitter.getScreenName)) {
            val (thumbType, ctx) = if (meta.getRawData.has("thumbHash") && meta.getType != null) {
              (meta.getType, new BlockContext(meta.getRawData.getString("thumbHash")))
            } else {
              val mimeType = getMimeType(meta)
              if (mimeType.equals("application/octet-stream")) {
                ("image/png", new BlockContext("76b321f040f6035c65b048821dcd373bf96dfbba1ffc0a739d5b4da2116180c4", Set("t")))
              } else {
                (mimeType, meta.createBlockContext(meta.getBlockHashes.getString(0)))
              }
            }
            buildDownloadResponse(cas, ctx, thumbType)
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
          if (ModelUtil.hasReadPriv(meta.getRawData, session.twitter.getScreenName)) {
            buildDownloadResponse(cas, meta.createBlockContext(meta.getBlockHashes.getString(0)), getMimeType(meta))
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
        metas = metas.filter(m => ModelUtil.hasSharePriv(m.getRawData, session.twitter.getScreenName))
        if (metas.length != docIds.length) return new StatusResponse(HttpResponseStatus.FORBIDDEN)

        val sharees = args.get("sharees").split(',')
        val shareMsg = args.get("sharemsg")

        val arr = new JSONArray()

        // TODO: validate sharees are mutually following
        // TODO: dm

        metas.foreach{ meta =>
          sharees.foreach{ sharee =>
            val reshareMeta = ModelUtil.reshareMeta(meta.getRawData, sharee)
            val fmd = FileMetaData.create(reshareMeta)
            storage.add(fmd)
//            val docId = storage.insert("fmd", fmd.toJson.getJSONObject("data"))
            arr.put(ModelUtil.createResponseData(session, reshareMeta, fmd.getHash))
          }
        }

        new JsonResponse(arr)
      }
    }))

    addRoute(new PostHandler("/u/", sessions, storage, cas, 640, 480))

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

  private def getMimeType(fmd: FileMetaData) : String = {
    if (fmd.getType != null) return fmd.getType
    Option(fmd.getFileExt) match {
      case Some("jpg") => "image/jpg"
      case Some("gif") => "image/gif"
      case Some("png") => "image/png"
      case _ => "application/octet-stream"
    }
  }

  private def getMetaById(id: String) : FileMetaData = {
/*
    val result = storage.select("select * from fmd where hash = '%s'".format(id)) // TODO: use prepared statements
    if (result == null || result.size == 0) {
      null
    } else {
      FileMetaData.create(id, result(0).toJson)
    }
*/
    val result = storage.find(JsonUtil.createJsonObject("hash", id + ".meta"))
    if (result == null || result.length == 0) {
      null
    } else {
      FileMetaData.fromJson(result.getJSONObject(0))
    }
  }

  private def getMetaListById(ids: List[String]) : List[FileMetaData] = {
/*
    val ws = ids.map("'%s'".format(_)).mkString(",")
    val result = storage.select("select * from fmd where hash in (%s)".format(ws)) // TODO: use prepared statements
*/
    val arr = new JSONArray
    ids.foreach(id => arr.put(id + ".meta"))
    val result = storage.find(JsonUtil.createJsonObject("hash", arr))
    if (result == null || result.length() == 0) {
      List()
    } else {
      (0 until result.length).map(idx => FileMetaData.fromJson(result.getJSONObject(idx))).toList
    }
  }

  private def buildDownloadResponse(cas: ContentAddressableStorage, ctx: BlockContext, contentType: String) : RouteResponse = {
    val (is, length) = cas.load(ctx)
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.setHeader(HttpHeaders.Names.CONTENT_TYPE, contentType)
    response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, length)
    response.setHeader(HttpHeaders.Names.EXPIRES, "Expires: Thu, 29 Oct 2020 17:04:19 GMT")
    response.setContent(new FileChannelBuffer(is, length))
    new RouteResponse(response, new RouteResponseDispose {
      def dispose() { is.close }
    })
  }

  private def loadPage(session: TwitterSession, pageIdx: Int, countPerPage: Int) : RouteResponse = {
    var tmp = MAIN_TEMPLATE.toString
    tmp = tmp.replace("{{dyn-screenname}}", session.twitter.getScreenName)
    tmp = tmp.replace("{{dyn-id}}", session.twitter.getId.toString)
    tmp = tmp.replace("{{dyn-data}}", resultsToJsonArray(session, getChronResults(storage, session.twitter.getScreenName, countPerPage, pageIdx)).toString())
//    tmp = tmp.replace("{{dyn-title}}", "Hello, %s!".format(session.twitter.getScreenName))
    tmp = tmp.replace("{{dyn-profileimg}}", session.getProfileImageUrl(session.twitter.getScreenName))
    new HtmlResponse(tmp)
  }

  private def resultsToJsonArray(session: TwitterSession, records: List[Record]) : JSONArray = {
    val arr = new JSONArray()
    records.foreach{r : Record => arr.put(ModelUtil.createResponseData(session, r.rawData, r.id)) }
    arr
  }

  private def resultsToJsonArray(session: TwitterSession, records: JSONArray) : JSONArray = {
    val arr = new JSONArray()
    (0 until records.length).map { idx =>
      val obj = records.getJSONObject(idx)
      arr.put(ModelUtil.createResponseData(session, obj.getJSONObject("data"), obj.getString("hash")))
    }
    arr
  }

  private def getSearchResults(storage: Node, ownerId: String, tags: String, count: Int, offset: Int) : List[Record] = {
//    val sql = "SELECT T.HASH,T.RAWMETA FROM FT_SEARCH_DATA('%s', 0, 0) FT, DATA_INDEX T WHERE FT.TABLE='DATA_INDEX' AND T.HASH = FT.KEYS[0] AND T.OWNERID = '%s".format(tags, ownerId)
//    storage.search(tags, ")
//    storage.select(sql)
    null
  }

  private def getChronResults(storage: IndexStorage, ownerId: String, count: Int = PAGE_SIZE, offset: Int = 0) : JSONArray = {
//    storage.select("select * from fmd where ownerId = '%s' order by filedate DESC limit %d OFFSET %d".format(ownerId.toLowerCase, count, offset))
    val filter = JsonUtil.createJsonObject(
//      "ownerId", ownerId,
      "count", count.asInstanceOf[AnyRef],
      "offset", offset.asInstanceOf[AnyRef],
      "orderBy", JsonUtil.createJsonObject(
        "name", "filedate",
        "desc", true.asInstanceOf[AnyRef]
      ))
    storage.find(filter)
  }
}
