package io.photon.app;


import io.viper.core.server.file.HttpChunkRelayEventListener;
import java.io.UnsupportedEncodingException;
import java.util.Map;
import java.util.UUID;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.json.JSONException;
import org.json.JSONObject;
import io.viper.core.server.Util;


public class FileUploadChunkRelayEventListener implements HttpChunkRelayEventListener
{
  final String _hostname;

  public FileUploadChunkRelayEventListener(String hostname)
  {
    _hostname = hostname;
  }

  public void onError(Channel clientChannel)
  {
    if (clientChannel == null) return;
    sendResponse(null, clientChannel, false);
  }

  public void onCompleted(String fileKey, Channel clientChannel)
  {
    sendResponse(fileKey, clientChannel, true);
  }

  @Override
  public String onStart(Map<String, String> props)
  {
    return Util.base64Encode(UUID.randomUUID());
  }

  private void sendResponse(String fileKey, Channel clientChannel, boolean success)
  {
    try
    {
      HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);

      JSONObject jsonResponse = new JSONObject();
      jsonResponse.put("success", Boolean.toString(success));
      if (success)
      {
        jsonResponse.put("thumbnail", String.format("%s/thumb/%s", _hostname, fileKey));
        jsonResponse.put("url", String.format("%s/d/%s", _hostname, fileKey));
        jsonResponse.put("key", fileKey);
      }

      response.setContent(ChannelBuffers.wrappedBuffer(jsonResponse.toString(2).getBytes("UTF-8")));
      clientChannel.write(response).addListener(ChannelFutureListener.CLOSE);
    }
    catch (JSONException e)
    {
      e.printStackTrace();
    }
    catch (UnsupportedEncodingException e)
    {
      e.printStackTrace();
    }
  }
}
