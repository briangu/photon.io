package io.photon.app

import io.viper.core.server.file.HttpChunkRelayEventListener
import java.io.{File, UnsupportedEncodingException}
import java.util.Map
import java.util.UUID
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.ChannelFutureListener
import org.jboss.netty.handler.codec.http.{HttpHeaders, DefaultHttpResponse, HttpResponseStatus, HttpVersion}
import org.json.{JSONArray, JSONException, JSONObject}
import io.viper.core.server.Util
import javax.imageio.ImageIO
import com.thebuzzmedia.imgscalr.{Scalr, AsyncScalr}

class FileUploadEventListener(hostname: String, uploadsPath: String, thumbsPath: String, thumbWidth: Int, thumbHeight: Int) extends HttpChunkRelayEventListener {

  final val THUMBNAIL_CREATE_THRESHOLD = 128 * 1024

  var _props: Map[String, String] = null

  def onStart(props: Map[String, String]) : String = {
    _props = props
    Util.base64Encode(UUID.randomUUID())
  }

  def onCompleted(fileKey: String, clientChannel: Channel) = sendResponse(fileKey, clientChannel, true)

  def onError(clientChannel: Channel) {
    if (clientChannel != null) sendResponse(null, clientChannel, false)
  }

  private def sendResponse(fileKey: String, clientChannel: Channel, success: Boolean) {
    try {
      val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

      // TODO: once we are using the cloudcmd adapters, well have to download the file first and convert it - perhaps better to do on demand.

      val url = String.format("/d/%s", fileKey)
      val contentLength = _props.get(HttpHeaders.Names.CONTENT_LENGTH).toLong

      val contentType = _props.get(HttpHeaders.Names.CONTENT_TYPE)
      val thumbnailUrl = if (contentType.startsWith("image")) {
        if (contentLength < THUMBNAIL_CREATE_THRESHOLD) {
          url
        } else {
          createThumbnail("%s%s%s".format(uploadsPath, File.separator, fileKey), "%s%s%s".format(thumbsPath, File.separator, fileKey))
          String.format("/thumb/%s", fileKey)
        }
      }

/*
      val jsonResponse = new JSONObject()
      jsonResponse.put("success", success)
      if (success) {
        jsonResponse.put("thumbnail_url", thumbnailUrl)
        jsonResponse.put("url", url)
        jsonResponse.put("name", _props.get("filename"))
        jsonResponse.put("type", contentType)
        jsonResponse.put("size", contentLength)
        jsonResponse.put("delete_url", String.format("/d/%s", fileKey))
        jsonResponse.put("delete_type", "DELETE")
      }
      val arr = new JSONArray()
      arr.put(jsonResponse)

      response.setContent(ChannelBuffers.wrappedBuffer(arr.toString().getBytes("UTF-8")))
*/
      val jsonResponse = new JSONObject();
      jsonResponse.put("success", success)
      if (success)
      {
        jsonResponse.put("thumbnail", String.format("%s/thumb/%s", hostname, fileKey));
        jsonResponse.put("url", String.format("%s/d/%s", hostname, fileKey));
        jsonResponse.put("key", fileKey);
      }
      response.setContent(ChannelBuffers.wrappedBuffer(jsonResponse.toString().getBytes("UTF-8")))


      clientChannel.write(response).addListener(ChannelFutureListener.CLOSE)
    }
    catch {
      case e: JSONException => e.printStackTrace()
      case e: UnsupportedEncodingException => e.printStackTrace()
    }
  }

  private def createThumbnail(srcFilePath: String, destFilePath: String) {
    val srcFile = new File(srcFilePath)
    if (srcFile.exists()) {
      val destFile = new File(destFilePath)
      try {
        val image = ImageIO.read(srcFile)

        val future =
          AsyncScalr.resize(
            image,
            Scalr.Method.BALANCED,
            Scalr.Mode.FIT_TO_WIDTH,
            thumbWidth,
            thumbHeight,
            Scalr.OP_ANTIALIAS)

        val thumbnail = future.get()
        if (thumbnail != null) {
          ImageIO.write(thumbnail, "jpg", destFile)
        }
      }
      catch {
        case e: Exception => e.printStackTrace
      }
    }
  }
}
