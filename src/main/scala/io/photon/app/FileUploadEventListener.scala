package io.photon.app

import io.viper.core.server.file.HttpChunkRelayEventListener
import java.io.{File, UnsupportedEncodingException}
import java.util.Map
import java.util.UUID
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.ChannelFutureListener
import org.jboss.netty.handler.codec.http.DefaultHttpResponse
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.jboss.netty.handler.codec.http.HttpVersion
import org.json.JSONException
import org.json.JSONObject
import io.viper.core.server.Util
import javax.imageio.ImageIO
import com.thebuzzmedia.imgscalr.{Scalr, AsyncScalr}

class FileUploadEventListener(hostname: String, thumbFileRoot: String, thumbWidth: Int, thumbHeight: Int) extends HttpChunkRelayEventListener {

  def onStart(props: Map[String, String]) {
    Util.base64Encode(UUID.randomUUID())
  }

  def onCompleted(fileKey: String, clientChannel: Channel) = sendResponse(fileKey, clientChannel, true)

  def onError(clientChannel: Channel) {
    if (clientChannel != null) sendResponse(null, clientChannel, false)
  }

  private def sendResponse(fileKey: String, clientChannel: Channel, success: Boolean) {
    try {
      val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

      val jsonResponse = new JSONObject()
      jsonResponse.put("success", success)
      if (success) {
        jsonResponse.put("thumbnail", String.format("%s/thumb/%s", hostname, fileKey))
        jsonResponse.put("url", String.format("/d/%s", fileKey))
        jsonResponse.put("key", fileKey)
      }

      response.setContent(ChannelBuffers.wrappedBuffer(jsonResponse.toString(2).getBytes("UTF-8")))
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
