package io.photon.app

import io.viper.core.server.router.{RouteResponse, RouteUtil, RouteHandler, Route}
import twitter4j._
import java.io.{File, InputStreamReader, BufferedReader}
import twitter4j.auth.{RequestToken, AccessToken}
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1
import collection.mutable
import org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive
import org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer
import org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength
import java.net.URI
import org.jboss.netty.handler.codec.http


object TwitterCLI {

  def login(userId: String): Twitter = {
    var accessToken = loadAccessToken(userId)
    if (accessToken == null) {
      accessToken = getNewAccessToken(userId)
      storeAccessToken(userId, accessToken)
    }

    if (accessToken == null) {
      null
    } else {
      val tw = new TwitterFactory().getInstance()
      tw.setOAuthConsumer("[consumer key]", "[consumer secret]")
      tw.setOAuthAccessToken(accessToken)
      tw
    }
  }

  private def getNewAccessToken(userId: String): AccessToken = {
    val twitter = new TwitterFactory().getInstance()
    twitter.setOAuthConsumer("[consumer key]", "[consumer secret]")

    val requestToken = twitter.getOAuthRequestToken()
    val br = new BufferedReader(new InputStreamReader(System.in))

    var accessToken: AccessToken = null
    while (null == accessToken) {
      System.out.println("Open the following URL and grant access to your account:")
      System.out.println(requestToken.getAuthorizationURL())
      System.out.print("Enter the PIN(if aviailable) or just hit enter.[PIN]:")

      val pin = br.readLine()
      try {
        if (pin.length() > 0) {
          accessToken = twitter.getOAuthAccessToken(requestToken, pin)
        } else {
          accessToken = twitter.getOAuthAccessToken()
        }
      } catch {
        case te: TwitterException => {
          if (401 == te.getStatusCode()) {
            System.out.println("Unable to get the access token.")
          } else {
            te.printStackTrace()
          }
        }
      }
    }

    accessToken
  }

  private def storeAccessToken(userId: String, accessToken: AccessToken) {
    //store accessToken.getToken()
    //store accessToken.getTokenSecret()
  }

  private def loadAccessToken(userId: String): AccessToken = {
    /*
        val token = ""
        val tokenSecret = ""
        new AccessToken(token, tokenSecret)
    */
    null
  }
}

class TwitterConfig(val loginRoute: String, val logoutRoute: String, val callbackRoute: String, val callbackUrl: String, val sessions: TwitterSessionService) {}

class TwitterLogin(handler: TwitterRouteHandler, config: TwitterConfig) extends TwitterGetRoute(config, config.loginRoute, handler) {}
class TwitterAuthCallback(config: TwitterConfig) extends TwitterGetRoute(config, "/" + config.callbackRoute, null) {}
class TwitterLogout(handler: TwitterRouteHandler, config: TwitterConfig) extends TwitterGetRoute(config, config.logoutRoute, handler) {}

class TwitterSession(val id: String, val twitter: Twitter, var requestToken: RequestToken, var postLoginRoute: String) {

  private val _userIdCache = new mutable.HashMap[Long, User] with mutable.SynchronizedMap[Long, User];
  private val _userNameCache = new mutable.HashMap[String, User] with mutable.SynchronizedMap[String, User];

  private def ensureId(id: Long) {
    if (!_userIdCache.contains(id)) {
      _userIdCache.synchronized {
        if (!_userIdCache.contains(id)) {
          try {
            val user = twitter.showUser(id)
            if (user != null) {
              _userIdCache.put(id, user)
            }
          } catch {
            case e: Exception => ;
          }
        }
      }
    }
  }

  private def ensureName(name: String) {
    if (!_userNameCache.contains(name)) {
      _userNameCache.synchronized {
        if (!_userNameCache.contains(name)) {
          try {
            val user = twitter.showUser(name)
            if (user != null) {
              _userNameCache.put(name, user)
            }
          } catch {
            case e: Exception => ;
          }
        }
      }
    }
  }

  def getIdFromScreenName(name: String): Long = {
    ensureName(name)
    if (_userNameCache.contains(name)) _userNameCache.get(name).get.getId else 0
  }

  def getScreenName(id: Long) : String = {
    ensureId(id)
    if (_userIdCache.contains(id)) {
      _userIdCache.get(id).get.getScreenName
    } else {
      "anonymous"
    }
  }

  def getProfileImageUrl(id: Long) : String = {
    ensureId(id)

    if (_userIdCache.contains(id)) {
      _userIdCache.get(id).get.getProfileImageURL.toString
    } else {
      "https://twimg0-a.akamaihd.net/sticky/default_profile_images/default_profile_0_normal.png"
    }
  }
}

trait TwitterSessionService {
  def getSession(key: String): TwitterSession
  def setSession(key: String, session: TwitterSession)
  def deleteSession(key: String)
  def getSessionId(cookieString: String, sessionName: String): (String, List[Cookie]) = {
    if (cookieString == null) {
      (null, List())
    } else {
      import collection.JavaConversions._
      val cookieDecoder = new CookieDecoder()
      var sessionId : String = null
      val cookies = cookieDecoder.decode(cookieString).flatMap {
        cookie =>
          if (cookie.getName.equals(sessionName)) {
            sessionId = cookie.getValue
            Nil
          } else {
            List(cookie)
          }
      }.toList
      (sessionId, cookies)
    }
  }
}

object SimpleTwitterSessionService {
  val sessions = new collection.mutable.HashMap[String, TwitterSession] with mutable.SynchronizedMap[String, TwitterSession]
  val instance = new SimpleTwitterSessionService
}

class SimpleTwitterSessionService extends TwitterSessionService {
  def getSession(key: String): TwitterSession = SimpleTwitterSessionService.sessions.get(key).getOrElse(null)
  def setSession(key: String, session: TwitterSession) = SimpleTwitterSessionService.sessions.put(key, session)
  def deleteSession(key: String) = SimpleTwitterSessionService.sessions.remove(key)
}

object WhiteListService {
  def inWhiteList(screenname: String): Boolean = {
    new File("whitelist/%s".format(screenname.toLowerCase)).exists
  }
}

object TwitterRestRoute {
  val SESSION_NAME = "photon-session"
}

class TwitterRestRoute(route: String, handler: RouteHandler, method: HttpMethod, protected val config: TwitterConfig) extends Route(route) {

  val sessionKey: String = null
  val session: TwitterSession = null

  override
  def handleUpstream(ctx: ChannelHandlerContext, e: ChannelEvent) {
    if (!(e.isInstanceOf[MessageEvent]) || !(e.asInstanceOf[MessageEvent].getMessage.isInstanceOf[HttpRequest])) {
      super.handleUpstream(ctx, e)
      return
    }

    val request = e.asInstanceOf[MessageEvent].getMessage.asInstanceOf[HttpRequest]
    val (sessionId, cookies) = getSessionId(request.getHeader(HttpHeaders.Names.COOKIE))

    val response = if (sessionId == null || config.sessions.getSession(sessionId) == null) {
      try {
        val twitter = new TwitterFactory().getInstance()
        val requestToken = twitter.getOAuthRequestToken(config.callbackUrl)
        val sessionId = java.util.UUID.randomUUID().toString
        val session = new TwitterSession(sessionId, twitter, requestToken, request.getUri)
        config.sessions.setSession(sessionId, session)

        val response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND)
        response.setHeader(HttpHeaders.Names.SET_COOKIE, setSessionId(sessionId, cookies))
        response.setHeader("Location", requestToken.getAuthenticationURL)
        response
      } catch {
        case e: TwitterException => throw new RuntimeException("failed to login")
      }
    } else {
      val session = config.sessions.getSession(sessionId)
      val path = RouteUtil.parsePath(request.getUri())
      if (path.size > 0 && path.get(0).equals(config.callbackRoute)) {
        val args = RouteUtil.extractQueryParams(new URI(request.getUri()))
        val verifier = args.get("oauth_verifier")
        try {
          session.twitter.getOAuthAccessToken(session.requestToken, verifier)
          session.requestToken = null

          if (userInWhiteList(session.twitter.getScreenName)) {
            val response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND)
            response.setHeader(HttpHeaders.Names.SET_COOKIE, setSessionId(sessionId, cookies))
            response.setHeader("Location", session.postLoginRoute)
            response
          } else {
            config.sessions.deleteSession(session.id)
            val response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND)
            response.setHeader("Location", "/")
            response.setHeader(HttpHeaders.Names.SET_COOKIE, setSessionId(null, cookies))
            response
          }
        } catch {
          case e: TwitterException => new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
        }
      } else {
        try {
          val args = if (request.getMethod == HttpMethod.POST || request.getMethod == HttpMethod.PUT) {
            RouteUtil.extractArgs(request, _route, path)
          } else {
            val args = RouteUtil.extractPathArgs(_route, path)
            args.putAll(RouteUtil.extractQueryParams(new URI(request.getUri())))
            args
          }

          val routeResponse = handler.asInstanceOf[TwitterRouteHandler].exec(session, args)
          if (routeResponse.HttpResponse == null) {
            val response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK)
            response.setContent(wrappedBuffer("{\"status\": true}".getBytes()))
            response
          } else {
            val response = routeResponse.HttpResponse
            if (response.getHeader(HttpHeaders.Names.CONTENT_LENGTH) == null) {
              setContentLength(response, response.getContent().readableBytes())
            }
            if (isKeepAlive(request)) {
              response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE)
            } else {
              response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE)
            }

            if (config.sessions.getSession(sessionId) == null) {
              response.setHeader(HttpHeaders.Names.SET_COOKIE, setSessionId(null, cookies))
            } else {
              //response.setHeader(HttpHeaders.Names.SET_COOKIE, setSessionId(sessionId, cookies))
            }

            response
          }
        } catch {
          case e: TwitterException => new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
        }
      }
    }
    if (response != null) writeResponse(request, response, e)
  }

  def userInWhiteList(screenname: String) : Boolean = {
    WhiteListService.inWhiteList(screenname)
  }

  def getSessionId(cookieString: String): (String, List[Cookie]) = {
    if (cookieString == null) {
      (null, List())
    } else {
      import collection.JavaConversions._
      val cookieDecoder = new CookieDecoder()
      var sessionId : String = null
      val cookies = cookieDecoder.decode(cookieString).flatMap {
        cookie =>
          if (cookie.getName.equals(TwitterRestRoute.SESSION_NAME)) {
            sessionId = cookie.getValue
            Nil
          } else {
            List(cookie)
          }
      }.toList
      (sessionId, cookies)
    }
  }

  def setSessionId(sessionId: String, cookies: List[Cookie]): String = {
    val cookieEncoder = new http.CookieEncoder(true)
    cookies.foreach(cookieEncoder.addCookie)

    val sessionCookie = if (sessionId == null) {
      val sessionCookie = new DefaultCookie(TwitterRestRoute.SESSION_NAME,  "")
      sessionCookie.setMaxAge(0)
      sessionCookie
    } else {
      val sessionCookie = new DefaultCookie(TwitterRestRoute.SESSION_NAME, sessionId)
      sessionCookie.setPath("/")
      sessionCookie
    }
    cookieEncoder.addCookie(sessionCookie)
    cookieEncoder.encode()
  }

  def writeResponse(request: HttpRequest, response: HttpResponse, e: ChannelEvent) {
    val writeFuture = e.getChannel().write(response)
    if (response.getStatus != HttpResponseStatus.OK || !isKeepAlive(request)) {
      writeFuture.addListener(ChannelFutureListener.CLOSE)
    }
  }
}

trait TwitterRouteHandler extends RouteHandler {
  def exec(session: TwitterSession, args: java.util.Map[String, String]): RouteResponse = exec(args)
  def exec(args: java.util.Map[String, String]): RouteResponse = null
}

class TwitterGetRoute(config: TwitterConfig, route: String, handler: TwitterRouteHandler) extends TwitterRestRoute(route, handler, HttpMethod.GET, config) {}
class TwitterPostRoute(config: TwitterConfig, route: String, handler: TwitterRouteHandler) extends TwitterRestRoute(route, handler, HttpMethod.POST, config) {}
class TwitterPutRoute(config: TwitterConfig, route: String, handler: TwitterRouteHandler) extends TwitterRestRoute(route, handler, HttpMethod.PUT, config) {}
class TwitterDeleteRoute(config: TwitterConfig, route: String, handler: TwitterRouteHandler) extends TwitterRestRoute(route, handler, HttpMethod.DELETE, config) {}

