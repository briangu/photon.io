package io.photon.app

import io.viper.core.server.router.Route
import org.jboss.netty.handler.codec.http2.{DiskAttribute, FileUpload, HttpPostRequestDecoder, DefaultHttpDataFactory}
import org.jboss.netty.channel.{ChannelFutureListener, ChannelHandlerContext, MessageEvent}
import org.jboss.netty.handler.codec.http._
import java.io._
import org.jboss.netty.buffer.ChannelBuffers
import org.json.{JSONException, JSONArray}
import io.stored.common.CryptoUtil
import org.jboss.netty.handler.codec.http.HttpHeaders._
import cloudcmd.common.{ContentAddressableStorage, BlockContext}
import cloudcmd.common.engine.IndexStorage

class PostHandler(route: String, sessions: TwitterSessionService, storage: IndexStorage, cas: ContentAddressableStorage, thumbWidth: Int, thumbHeight: Int) extends Route(route) {

  final val THUMBNAIL_CREATE_THRESHOLD = 128 * 1024 // TODO: come from config

  override
  def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val msg = e.getMessage

    if (!(msg.isInstanceOf[HttpMessage]) && !(msg.isInstanceOf[HttpChunk])) {
      ctx.sendUpstream(e)
      return
    }

    val request = e.getMessage.asInstanceOf[org.jboss.netty.handler.codec.http.HttpRequest]
    if (request.getMethod != HttpMethod.POST) {
      ctx.sendUpstream(e)
      return
    }

    val (sessionId, cookies) = sessions.getSessionId(request.getHeader(HttpHeaders.Names.COOKIE), TwitterRestRoute.SESSION_NAME)
    val session = sessions.getSession(sessionId)
    val response = if (session == null) {
      new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN)
    } else {
      val convertedRequest = convertRequest(request)
      val decoder = new HttpPostRequestDecoder(new DefaultHttpDataFactory(true), convertedRequest)
      try {
        if (decoder.isMultipart) {
          if (decoder.getBodyHttpDatas.size() == 2) {
            // TODO: it sucks that the tags are stored on disk too.  can we make them inmemory and make file data disk?
            val fileData = decoder.getBodyHttpData("files[]")
            val tagData = decoder.getBodyHttpData("tags")
            if (fileData != null && tagData != null) {
              val upload  = fileData.asInstanceOf[FileUpload]
              val tagSet = FileUtils.readFile(tagData.asInstanceOf[DiskAttribute].getFile)
              processFile(session, upload, tagSet) // TODO: sanitize tagset
            } else {
              new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
            }
          } else {
            new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
          }
        } else {
          new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST)
        }
      } finally {
        decoder.cleanFiles()
      }
    }

    if (isKeepAlive(request)) {
      response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.KEEP_ALIVE)
    } else {
      response.setHeader(HttpHeaders.Names.CONNECTION, HttpHeaders.Values.CLOSE)
    }
    setContentLength(response, response.getContent().readableBytes())

    val writeFuture = e.getChannel().write(response)
    if (!isKeepAlive(request)) {
      writeFuture.addListener(ChannelFutureListener.CLOSE)
    }
  }

  def convertRequest(request: HttpRequest) : org.jboss.netty.handler.codec.http2.HttpRequest = {
    val convertedRequest =
      new org.jboss.netty.handler.codec.http2.DefaultHttpRequest(
        org.jboss.netty.handler.codec.http2.HttpVersion.HTTP_1_0,
        org.jboss.netty.handler.codec.http2.HttpMethod.POST,
        request.getUri)
    convertedRequest.setContent(request.getContent)
    convertedRequest.setChunked(request.isChunked)
    import collection.JavaConversions._
    request.getHeaders.foreach { entry =>
      convertedRequest.setHeader(entry.getKey, entry.getValue)
    }
    convertedRequest
  }

  def processFile(session: TwitterSession, upload: FileUpload, tags: String) : HttpResponse = {
    var fis: InputStream = null
    try {
      val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

      // TODO: possibly do store and thumb in parallel
      fis = new FileInputStream(upload.getFile)
      val fileKey = CryptoUtil.computeHashAsString(upload.getFile)
      val tagSet = tags.split(" ").filter(_.length > 0).toSet
      val ctx = new BlockContext(fileKey, tagSet)
      cas.store(ctx, fis)
      val contentLength = upload.length()
      val contentType = upload.getContentType
      val (thumbHash, thumbSize) = if (contentType.startsWith("image")) {
        if (contentLength < THUMBNAIL_CREATE_THRESHOLD) {
          (fileKey, contentLength)
        } else {
          val ba = FileUtils.createThumbnail(upload.getFile, thumbWidth, thumbHeight)
          if (ba != null) {
            val hash = CryptoUtil.computeHash(ba)
            val bis = new ByteArrayInputStream(ba)
            try {
              cas.store(new BlockContext(hash, tagSet), bis)
            } finally {
              bis.close
            }
            (hash, ba.length.toLong)
          } else {
            ("76b321f040f6035c65b048821dcd373bf96dfbba1ffc0a739d5b4da2116180c4", 17639L)
          }
        }
      } else {
        ("76b321f040f6035c65b048821dcd373bf96dfbba1ffc0a739d5b4da2116180c4", 17639L)
      }

      val userId = session.twitter.getScreenName
      val fmd = ModelUtil.createFileMeta(upload, userId, userId, false, thumbHash, thumbSize, List(fileKey), tagSet)
      cas.store(fmd.createBlockContext, new ByteArrayInputStream(fmd.getDataAsString.getBytes("UTF-8")))
      storage.add(fmd)

      val arr = new JSONArray()
      arr.put(ModelUtil.createResponseData(session, fmd, fmd.getHash))

      response.setContent(ChannelBuffers.wrappedBuffer(arr.toString().getBytes("UTF-8")))
      response
    }
    catch {
      case e: JSONException => {
        e.printStackTrace()
        new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
      }
      case e: UnsupportedEncodingException => {
        e.printStackTrace()
        new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
      }
    } finally {
      if (fis != null) {
        fis.close()
      }
    }
  }
}

