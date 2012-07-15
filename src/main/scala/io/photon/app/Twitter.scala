package io.photon.app

import io.viper.core.server.router.{RouteUtil, RouteHandler, Route}
import twitter4j.{Twitter, TwitterException, TwitterFactory}
import java.io.{InputStreamReader, BufferedReader}
import twitter4j.auth.{RequestToken, AccessToken}
import java.util
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1
import collection.mutable
import util.UUID
import org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive
import org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer
import org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength
import java.net.URI




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

class TwitterLogin extends TwitterGetRoute("/login", null, SimpleTwitterSession.instance) {}
class TwitterCallback extends TwitterGetRoute("/callback", null, SimpleTwitterSession.instance) {}
class TwitterLogout(handler: RouteHandler) extends TwitterGetRoute("/logout", handler, SimpleTwitterSession.instance)

class TwitterSession(val id: String, val twitter: Twitter, var requestToken: RequestToken) {}

trait TwitterSessionService {
  def getSession(key: String) : TwitterSession
  def setSession(key: String, session: TwitterSession)
  def deleteSession(key: String)
}

object SimpleTwitterSession {
  val sessions = new collection.mutable.HashMap[String, TwitterSession] with mutable.SynchronizedMap[String, TwitterSession]
  val instance = new SimpleTwitterSession
}

class SimpleTwitterSession extends TwitterSessionService {
  def getSession(key: String) : TwitterSession = SimpleTwitterSession.sessions.get(key).getOrElse(null)
  def setSession(key: String, session: TwitterSession) = SimpleTwitterSession.sessions.put(key, session)
  def deleteSession(key: String) = SimpleTwitterSession.sessions.remove(key)
}

class TwitterRestRoute(route: String, handler: RouteHandler, method: HttpMethod, protected val sessions: TwitterSessionService, callbackUrl: String = null) extends Route(route) {

  val SESSION_HEADER_NAME = "photon-session"

  val sessionKey : String = null
  val session: TwitterSession = null

  override
  def handleUpstream(ctx: ChannelHandlerContext, e: ChannelEvent) {
    if (!(e.isInstanceOf[MessageEvent]) || !(e.asInstanceOf[MessageEvent].getMessage.isInstanceOf[HttpRequest])) {
      super.handleUpstream(ctx, e)
      return
    }

    val request = e.asInstanceOf[MessageEvent].getMessage.asInstanceOf[HttpRequest]
    val response = if (request.containsHeader(SESSION_HEADER_NAME)) {
      val sessionId = request.getHeader(SESSION_HEADER_NAME)
      val session = sessions.getSession(sessionId)
      if (session == null) {
        new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.UNAUTHORIZED)
      } else if (request.getUri.equals(callbackUrl)) {
        val args = RouteUtil.extractQueryParams(new URI(request.getUri()))
        val verifier = args.get("oauth_verifier")
        try {
          session.twitter.getOAuthAccessToken(session.requestToken, verifier)
          session.requestToken = null
          val response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND)
          response.setHeader(SESSION_HEADER_NAME, sessionId)
          response.setHeader("Location", "")
          response
        } catch {
          case e:TwitterException => new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
        }
      } else {
        try
        {
          val path = RouteUtil.parsePath(request.getUri())
          val args = RouteUtil.extractPathArgs(_route, path)
          args.putAll(RouteUtil.extractQueryParams(new URI(request.getUri())))

          val routeResponse = handler.exec(args)
          val keepalive = isKeepAlive(request)
          if (routeResponse.HttpResponse == null)
          {
            val response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK)
            response.setContent(wrappedBuffer("{\"status\": true}".getBytes()))
            response
          }
          else
          {
            val response = routeResponse.HttpResponse
            if (keepalive)
            {
              response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE)
              setContentLength(response, response.getContent().readableBytes())
            }
            else
            {
              response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE)
            }
            response
          }
        } catch {
          case e:TwitterException => new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
        }
      }
    } else {
      try {
        if (callbackUrl == null) {
          new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
        } else {
          val twitter = new TwitterFactory().getInstance()
          val requestToken = twitter.getOAuthRequestToken(callbackUrl)
          val sessionId = UUID.randomUUID().toString
          val session = new TwitterSession(sessionId, twitter, requestToken)
          sessions.setSession(sessionId, session)
          val response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.FOUND)
          response.setHeader(SESSION_HEADER_NAME, sessionId)
          response.setHeader("Location", requestToken.getAuthenticationURL)
          response
        }
      } catch {
        case e:TwitterException => throw new RuntimeException("failed to login")
      }
    }
    if (response != null) writeResponse(request, response, e)
  }

  def writeResponse(request: HttpRequest, response: HttpResponse, e: ChannelEvent) {
    val writeFuture = e.getChannel().write(response)
    if (!isKeepAlive(request)) {
      writeFuture.addListener(ChannelFutureListener.CLOSE)
    }
  }
}

class TwitterGetRoute(route: String, handler: RouteHandler, sessions: TwitterSessionService, callbackUrl: String = null) extends TwitterRestRoute(route, handler, HttpMethod.GET, sessions, callbackUrl) {}
class TwitterPostRoute(route: String, handler: RouteHandler, sessions: TwitterSessionService) extends TwitterRestRoute(route, handler, HttpMethod.POST, sessions) {}
class TwitterPutRoute(route: String, handler: RouteHandler, sessions: TwitterSessionService) extends TwitterRestRoute(route, handler, HttpMethod.PUT, sessions) {}
class TwitterDeleteRoute(route: String, handler: RouteHandler, sessions: TwitterSessionService) extends TwitterRestRoute(route, handler, HttpMethod.DELETE, sessions) {}

