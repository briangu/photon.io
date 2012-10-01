package io.photon.app

import io.stored.server.common.Record
import io.viper.common.{ViperServer, NestServer}
import io.viper.core.server.router._
import io.viper.core.server.router.RouteResponse.RouteResponseDispose
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpVersion._
import org.json.{JSONArray, JSONObject}
import cloudcmd.common._
import adapters.DataNotFoundException
import engine.{FileProcessor, IndexStorage}
import java.io.File
import java.net.InetAddress
import srv.{SimpleOAuthSessionService, CloudAdapter, OAuthRouteConfig}
import util.{FileTypeUtil, JsonUtil}


object Photon {
  val projectionConfig = new JSONObject(FileUtils.readResourceFile(this.getClass, "/config/photon.io/projections.json"))

  def getIpAddress: String = {
    InetAddress.getLocalHost.getHostName
  }

  def main(args: Array[String]) {
    val ipAddress = getIpAddress
    val port = 8080
    val baseHostPort = "http://%s:%d".format(ipAddress, port)
    println("booting at " + baseHostPort)

    var configRoot: String = FileUtil.findConfigDir(FileUtil.getCurrentWorkingDirectory, ".cld")
    if (configRoot == null) {
      configRoot = System.getenv("HOME") + File.separator + ".cld"
      new File(configRoot).mkdir
    }

    CloudServices.init(configRoot)

    try {
     // val storage = Node.createSingleNode("db/photon.io", projectionConfig)
      val sessions = SimpleTwitterSessionService.instance
      val twitterConfig = new TwitterConfig("/login", "/logout", "callback", "http://%s:%d/callback".format(ipAddress, port), sessions)
      val apiConfig = new OAuthRouteConfig(baseHostPort, SimpleOAuthSessionService.instance)

      NestServer.run(port, new Photon(CloudServices.IndexStorage, CloudServices.CloudEngine, CloudServices.FileProcessor, twitterConfig, apiConfig))
    } finally {
      CloudServices.shutdown
    }
  }
}

class Photon(storage: IndexStorage, cas: ContentAddressableStorage, fileProcessor: FileProcessor, twitterConfig: TwitterConfig, apiConfig: OAuthRouteConfig) extends ViperServer("res:///photon.io") {

  final protected val PAGE_SIZE = 25
  final protected val MAIN_TEMPLATE = FileUtils.readResourceFile(this.getClass, "/templates/photon.io/main.html")

  val _apiHandler = new CloudAdapter(cas, storage, apiConfig)

  override def addRoutes {

    _apiHandler.addRoutes(this)

    addRoute(new TwitterGetRoute(twitterConfig, "/j/$page", new TwitterRouteHandler {
      override
      def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = {
        val offset = (math.max(args.get("page").toInt-1, 0)) * PAGE_SIZE
        val feed = if (args.containsKey("q")) {
          getSearchResults(storage, session.twitter.getId, args.get("q"), PAGE_SIZE, offset)
        } else {
          getChronResults(storage, session.twitter.getId, PAGE_SIZE, offset)
        }
        new JsonResponse(resultsToJsonArray(session, feed))
      }
    }))
    addRoute(new TwitterGetRoute(twitterConfig, "/t/$docId", new TwitterRouteHandler {
      override
      def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = {
        val meta = getMetaById(args.get("docId"))
        if (meta == null) {
          new StatusResponse(HttpResponseStatus.NOT_FOUND)
        } else {
          if (ModelUtil.hasReadPriv(meta, session.twitter.getId)) {
            var mimeType = meta.getType
            if (mimeType == null) mimeType = FileTypeUtil.instance.getTypeFromExtension(meta.getFileExt)
            val (thumbType, ctx) = if (meta.getRawData.has("thumbHash") && mimeType != null) {
              (mimeType, meta.createBlockContext(meta.getRawData.getString("thumbHash")))
            } else {
              if (mimeType == null || !mimeType.startsWith("image")) {
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
    addRoute(new TwitterGetRoute(twitterConfig, "/d/$docId", new TwitterRouteHandler {
      override
      def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = {
        val meta = getMetaById(args.get("docId"))
        if (meta == null) {
          new StatusResponse(HttpResponseStatus.NOT_FOUND)
        } else {
          if (ModelUtil.hasReadPriv(meta, session.twitter.getId)) {
            //val mimeType = if (meta.getType == null) "application/octet-stream" else meta.getType
            val mimeType = "application/octet-stream" // for downloading
            buildDownloadResponse(cas, meta.createBlockContext(meta.getBlockHashes.getString(0)), mimeType, meta.getFilename)
          } else {
            new StatusResponse(HttpResponseStatus.FORBIDDEN)
          }
        }
      }
    }))
    addRoute(new TwitterPostRoute(twitterConfig, "/shares", new TwitterRouteHandler {
      override
      def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = {
        if (!args.containsKey("sharees")) return new StatusResponse(HttpResponseStatus.BAD_REQUEST)
        if (!args.containsKey("ids")) return new StatusResponse(HttpResponseStatus.BAD_REQUEST)
        if (!args.containsKey("sharemsg")) return new StatusResponse(HttpResponseStatus.BAD_REQUEST)

        val shareeNames = args.get("sharees").split(',')
        val shareeIds = shareeNames.par.map{ name =>
          val shareeId = session.getIdFromScreenName(name)
          if (shareeId == 0) return new StatusResponse(HttpResponseStatus.BAD_REQUEST)
          shareeId
        }

        val docIds = args.get("ids").split(',')
        var metas = getMetaListById(docIds.toList)
        if (metas.length != docIds.length) return new StatusResponse(HttpResponseStatus.BAD_REQUEST)
        metas = metas.filter(m => ModelUtil.hasSharePriv(m, session.twitter.getId))
        if (metas.length != docIds.length) return new StatusResponse(HttpResponseStatus.FORBIDDEN)

        val shareMsg = args.get("sharemsg")

        val arr = new JSONArray()

        // TODO: validate sharees are mutually following
        // TODO: dm

        metas.foreach{ meta =>
          shareeIds.foreach{ sharee =>
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
    addRoute(new PostHandler("/u/", twitterConfig.sessions, storage, cas, fileProcessor))

    addRoute(new TwitterLogin(
      new TwitterRouteHandler {
        override
        def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = {
          val response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND)
          response.setHeader("Location", "/")
          new RouteResponse(response)
        }
      },
      twitterConfig))
    addRoute(new TwitterAuthCallback(twitterConfig))
    addRoute(new TwitterLogout(
      new TwitterRouteHandler {
        override
        def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = {
          twitterConfig.sessions.deleteSession(session.id)
          val response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND)
          response.setHeader("Location", "/")
          new RouteResponse(response)
        }
      },
      twitterConfig))

    addRoute(new TwitterGetRoute(twitterConfig, "/", new TwitterRouteHandler {
      override
      def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = loadPage(session, 0, PAGE_SIZE)
    }))
  }

  protected def getMetaById(id: String) : FileMetaData = {
    val result = storage.find(JsonUtil.createJsonObject("hash", id + ".meta"))
    if (result == null || result.length == 0) {
      null
    } else {
      FileMetaData.fromJson(result.getJSONObject(0))
    }
  }

  protected def getMetaListById(ids: List[String]) : List[FileMetaData] = {
    val arr = new JSONArray
    ids.foreach(id => arr.put(id + ".meta"))
    val result = storage.find(JsonUtil.createJsonObject("hash", arr))
    if (result == null || result.length() == 0) {
      List()
    } else {
      (0 until result.length).map(idx => FileMetaData.fromJson(result.getJSONObject(idx))).toList
    }
  }

  protected def buildDownloadResponse(cas: ContentAddressableStorage, ctx: BlockContext, contentType: String, fileName: String = null) : RouteResponse = {
    try {
      val (is, length) = cas.load(ctx)
      val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
      response.setHeader(HttpHeaders.Names.CONTENT_TYPE, contentType)
      response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, length)
      response.setHeader(HttpHeaders.Names.EXPIRES, "Expires: Thu, 29 Oct 2020 17:04:19 GMT")
      if (fileName != null) {
        response.setHeader("Content-disposition", "attachment; filename=%s".format(fileName))
      }
      response.setContent(new FileChannelBuffer(is, length))
      new RouteResponse(response, new RouteResponseDispose {
        def dispose() { is.close }
      })
    } catch {
      case e: DataNotFoundException => {
        new StatusResponse(HttpResponseStatus.NOT_FOUND)
      }
    }
  }

  protected def getSearchResults(storage: IndexStorage, ownerId: Long, tags: String, count: Int, offset: Int) : JSONArray = {
    //    val sql = "SELECT T.HASH,T.RAWMETA FROM FT_SEARCH_DATA('%s', 0, 0) FT, DATA_INDEX T WHERE FT.TABLE='DATA_INDEX' AND T.HASH = FT.KEYS[0] AND T.OWNERID = '%s".format(tags, ownerId)
    //    storage.search(tags, ")
    //    storage.select(sql)
    val filter = JsonUtil.createJsonObject(
      "tags", tags,
      "properties__ownerId", ownerId.asInstanceOf[AnyRef],  // TODO: use colon syntax for stored.io
      "count", count.asInstanceOf[AnyRef],
      "offset", offset.asInstanceOf[AnyRef],
      "orderBy", JsonUtil.createJsonObject(
        "name", "createdDate",
        "desc", true.asInstanceOf[AnyRef]
      ))
    storage.find(filter)
  }

  protected def getChronResults(storage: IndexStorage, ownerId: Long, count: Int = PAGE_SIZE, offset: Int = 0) : JSONArray = {
    val filter = JsonUtil.createJsonObject(
      "properties__ownerId", ownerId.asInstanceOf[AnyRef],  // TODO: use colon syntax for stored.io
      "count", count.asInstanceOf[AnyRef],
      "offset", offset.asInstanceOf[AnyRef],
      "orderBy", JsonUtil.createJsonObject(
        "name", "createdDate",
        "desc", true.asInstanceOf[AnyRef]
      ))
    storage.find(filter)
  }

  protected def loadPage(session: TwitterSession, pageIdx: Int, countPerPage: Int) : RouteResponse = {
    var tmp = MAIN_TEMPLATE.toString
    tmp = tmp.replace("{{dyn-screenname}}", session.twitter.getScreenName)
    tmp = tmp.replace("{{dyn-id}}", session.twitter.getId.toString)
    tmp = tmp.replace("{{dyn-data}}", resultsToJsonArray(session, getChronResults(storage, session.twitter.getId, countPerPage, pageIdx)).toString())
    //    tmp = tmp.replace("{{dyn-title}}", "Hello, %s!".format(session.twitter.getScreenName))
    tmp = tmp.replace("{{dyn-profileimg}}", session.getProfileImageUrl(session.twitter.getId))
    new HtmlResponse(tmp)
  }

  protected def resultsToJsonArray(session: TwitterSession, records: List[Record]) : JSONArray = {
    val arr = new JSONArray()
    records.foreach{r : Record => arr.put(ModelUtil.createResponseData(session, r.rawData, r.id)) }
    arr
  }

  protected def resultsToJsonArray(session: TwitterSession, records: JSONArray) : JSONArray = {
    val arr = new JSONArray()
    (0 until records.length).map { idx =>
      val obj = records.getJSONObject(idx)
      arr.put(ModelUtil.createResponseData(session, obj.getJSONObject("data"), obj.getString("hash")))
    }
    arr
  }
}