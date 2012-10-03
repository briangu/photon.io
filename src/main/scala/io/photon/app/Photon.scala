package io.photon.app

import io.viper.common.{ViperServer, NestServer}
import io.viper.core.server.router._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpVersion._
import org.json.{JSONArray, JSONObject}
import cloudcmd.common._
import java.io.File
import java.net.InetAddress
import srv.{SimpleOAuthSessionService, OAuthRouteConfig}
import com.ning.http.client.AsyncHttpClient
import scala.util.Random


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

      NestServer.run(port, new Photon(twitterConfig, CloudServices.CollectionsStorage, apiConfig))
    } finally {
      CloudServices.shutdown
    }
  }
}

class Photon(twitterConfig: TwitterConfig, tagsStorage: CollectionsStorage, apiConfig: OAuthRouteConfig) extends ViperServer("res:///photon.io") {

  final protected val PAGE_SIZE = 25
  final protected val MAIN_TEMPLATE = FileUtils.readResourceFile(this.getClass, "/templates/photon.io/main.html")

  val asyncHttpClient = new AsyncHttpClient()

  override def addRoutes {

    // TODO: use nextPage URI provided by api.twitter.com
    addRoute(new TwitterGetRoute(twitterConfig, "/j/$page", new TwitterRouteHandler {
      override
      def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = {
        val feed = if (!args.containsKey("q")) {
          loadFeedData(session.twitter.getId)
        } else {
          val query = args.get("q")
          val page = args.get("page")
          if (page.equals("0")) {
            val network = if (args.containsKey("network")) {
              args.get("network").toBoolean
            } else {
              true
            }
            loadFeedData(session.twitter.getId, query, network)
          } else {
            val maxId = args.get("max_id")
            val workflow = args.get("result_type")
            getPaginatedSearchResults(page, maxId, query, workflow)
          }
        }
        new JsonResponse(feed)
      }
    }))
/*
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

*/

    addRoute(new TwitterPostRoute(twitterConfig, "/tags", new TwitterRouteHandler {
      override
      def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = {
        if (!args.containsKey("tags")) return new StatusResponse(HttpResponseStatus.BAD_REQUEST)
        if (!args.containsKey("ids")) return new StatusResponse(HttpResponseStatus.BAD_REQUEST)

        val tags = args.get("tags")
        val docIds = args.get("ids").split(',').toList.map(_.toLong)

        tagsStorage.addTags(session.twitter.getScreenName, tags, docIds)

        new JsonResponse(new JSONArray())
      }
    }))

//    addRoute(new PostHandler("/u/", twitterConfig.sessions, storage, cas, fileProcessor))

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
      def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = {
        if (args.containsKey("tags")) {
          val filter = new JSONObject()
          filter.put("tags", args.get("tags"))
          if (args.containsKey("user")) {
            filter.put("userName", session.twitter.getScreenName)
          }
          val results = tagsStorage.find(filter)
          // val ids = (0 until results.length()).map(results.getJSONObject(_).getLong("id")).toList
          val tweets = getTweetsByTags(results)
          val obj = new JSONObject()
          obj.put("next_page", "") // TODO: fix pagination
          obj.put("results", tweets)
          obj.put("trends", tagsStorage.getTopTrends())
          return loadPage(session, obj)
        }
        val (query, mynetwork) = if (args.containsKey("user")) {
          // TODO: filter on tagged items for this user w/ "tagged" flag
          ("from:"+args.get("user"), false)
        } else {
          if (args.containsKey("q")) {
            (args.get("q"), true)
          } else {
            ("", true) // VANILLA page load
          }
        }
        loadPage(session, query, 0, PAGE_SIZE, mynetwork)
      }
    }))
  }

/*
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
        response.setHeader("Content-disposition", "attachment filename=%s".format(fileName))
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

  protected def getInitialSearchResults(storage: IndexStorage, ownerId: Long, tags: String, count: Int, offset: Int) : JSONArray = {
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

  protected def loadLatestTweets(session: TwitterSession) : List[Status] = {
    import scala.collection.JavaConversions._
    session.twitter.getHomeTimeline(new Paging(1, 500)).toList
//    session.twitter.search(new Query("filter:images")).getTweets.toList
  }

  protected def loadMediaTweets(session: TwitterSession) : JSONArray = {
    filterMediaTweets(loadLatestTweets(session))
  }

  protected def filterMediaTweets(tweets: List[Status]) : JSONArray = {
    val results = new JSONArray()
    tweets.foreach{ status =>
      if (status.getMediaEntities != null) {
        status.getMediaEntities.foreach{ entity =>
          println(entity.getType)
          if (entity.getType.equals("photo")) {
            System.out.println(status.getUser().getScreenName() + ":" + status.getText())
            results.put(new JSONObject(DataObjectFactory.getRawJSON(status)))
          }
        }
      }
    }
    results
  }

  protected def loadFeedData(session: TwitterSession, storage: IndexStorage, ownerId: Long, count: Int = PAGE_SIZE, offset: Int = 0) : JSONArray = {
    val mediaTweets = twitterResultsToJsonArray(session, loadMediaTweets(session))
    val backFeed = resultsToJsonArray(session, getChronResults(storage, ownerId, count, offset))
    val results = new JSONArray
    (0 until mediaTweets.length()).foreach(idx => results.put(mediaTweets.get(idx)))
    (0 until backFeed.length()).foreach(idx => results.put(backFeed.get(idx)))
    results
  }
*/

  // https://api.twitter.com/search.json?result_type=parallel_realtime&include_entities=1&q=filter:images%20filter:twimg%20OR%20filter:videos&rpp=100
  //    val url = "https://api.twitter.com/search.json?result_type=%s&include_entities=1&q=filter:images%20filter:twimg%20OR%20filter:videos+%s&rpp=%d".format(workflow, query, rpp)
  //    val url = "https://api.twitter.com/search.json?result_type=%s&include_entities=1&q=filter:images%20filter:twimg+%s&rpp=%d".format(workflow, query, rpp)
  // https://api.twitter.com/search.json?q=(from:mcuban+OR+from:eismcc+OR+from:sm+OR+from:jayz)+(filter%3Aimages+OR+filter%3Atwimg)&include_entities=1&result_type=parallel_realtime
  protected def getInitialSearchResults(query: String, friends: List[String], rpp: Int = PAGE_SIZE, workflow: String = "parallel_realtime") : JSONObject = {
    var expandedQuery = "(filter:images+OR+filter:twimg) -flickr"
    if (friends.size > 0) {
      val scope = Random.shuffle(friends).slice(0,math.min(20, friends.size)).map("from:%s".format(_)).reduceLeft(_ + "+OR+" + _)
      expandedQuery = expandedQuery + "(%s)".format(scope)
    }
    if (query.length > 0) {
      expandedQuery = expandedQuery + "(%s)".format(query)
    }
    val response = asyncHttpClient
      .prepareGet("https://api.twitter.com/search.json")
      .addQueryParameter("result_type", workflow)
      .addQueryParameter("include_entities", "1")
      .addQueryParameter("q", expandedQuery)
      .addQueryParameter("rpp", rpp.toString)
      .execute
      .get
    val responseBody = response.getResponseBody
    val json = new JSONObject(responseBody)
    if (json.has("errors")) {
      println("error for " + query)
    }
    json.put("result_type", workflow)
    json
  }

  protected def getPaginatedSearchResults(page: String, maxId: String, query: String, workflow: String) : JSONObject = {
    val response = asyncHttpClient
      .prepareGet("https://api.twitter.com/search.json")
      .addQueryParameter("result_type", workflow)
      .addQueryParameter("include_entities", "1")
      .addQueryParameter("q", query)
      .addQueryParameter("page", page)
      .addQueryParameter("max_id", maxId)
      .execute
      .get
    val responseBody = response.getResponseBody
    val json = new JSONObject(responseBody)
    if (json.has("errors")) {
      println("error for " + query)
    }
    json.put("result_type", workflow)
    json
  }

  //http://api.twitter.com/1/statuses/show/253507835592327168.json?include_entities=1
  protected def getTweetById(id: Long) : JSONObject = {
    val response = asyncHttpClient
      .prepareGet("http://api.twitter.com/1/statuses/show/%s.json".format(id.toString))
      .addQueryParameter("include_entities", "1")
      .execute
      .get
    val responseBody = response.getResponseBody
    val json = new JSONObject(responseBody)
    json
  }

  protected def getTweetsByTags(searchResults: JSONArray) : JSONArray = {
    val tweets = new JSONArray()
    (0 until searchResults.length()).foreach{ idx => {
      val searchResult = searchResults.getJSONObject(idx)
      val tweet = getTweetById(searchResult.getLong("id"))
      val user = tweet.getJSONObject("user")
      tweet.put("profile_image_url", user.getString("profile_image_url"))
      tweet.put("from_user", user.getString("screen_name"))
      tweet.put("tags", searchResult.getString("tags"))
      tweets.put(tweet)
    }}
    tweets
  }

  protected def loadFeedData(ownerId: Long, query: String = "", network: Boolean = true, count: Int = PAGE_SIZE) : JSONObject = {
    val friends = if (network) CloudServices.TwitterStream.followingGraph.getOrElse(ownerId, List()) else List()
    val searchResults = getInitialSearchResults(query, friends, count)
    val trends = tagsStorage.getTopTrends()
    searchResults.put("trends", trends)
    searchResults
  }

  protected def loadPage(session: TwitterSession, query: String, pageIdx: Int, countPerPage: Int, network: Boolean = true) : RouteResponse = {
    val data = loadFeedData(session.twitter.getId, query, network, countPerPage)
    loadPage(session, data)
  }

  protected def loadPage(session: TwitterSession, data: JSONObject) : RouteResponse = {
    var tmp = MAIN_TEMPLATE.toString
    // TODO: make this more efficient.
    tmp = tmp.replace("{{dyn-screenname}}", session.twitter.getScreenName)
    tmp = tmp.replace("{{dyn-id}}", session.twitter.getId.toString)
    tmp = tmp.replace("{{dyn-data}}", data.toString())
    //    tmp = tmp.replace("{{dyn-title}}", "Hello, %s!".format(session.twitter.getScreenName))
    tmp = tmp.replace("{{dyn-profileimg}}", session.getProfileImageUrl(session.twitter.getId))
    new HtmlResponse(tmp)
  }

  protected def twitterResultsToJsonArray(session: TwitterSession, records: JSONArray) : JSONArray = {
    val arr = new JSONArray()
    (0 until records.length).map { idx =>
      arr.put(ModelUtil.createResponseDataFromTweet(session, records.getJSONObject(idx)))
    }
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