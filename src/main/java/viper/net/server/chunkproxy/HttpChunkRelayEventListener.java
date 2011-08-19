package viper.net.server.chunkproxy;


import java.util.Map;
import org.jboss.netty.channel.Channel;


public interface HttpChunkRelayEventListener
{
  void onError(Channel clientChannel);
  void onCompleted(Channel clientChannel);
  void onStart(Map<String, String> props);
}