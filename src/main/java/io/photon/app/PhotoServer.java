package io.photon.app;


import com.thebuzzmedia.imgscalr.AsyncScalr;
import io.viper.core.server.file.FileChunkProxy;
import io.viper.core.server.file.FileContentInfoProvider;
import io.viper.core.server.file.HttpChunkProxyHandler;
import io.viper.core.server.file.HttpChunkRelayProxy;
import io.viper.core.server.file.StaticFileContentInfoProvider;
import io.viper.core.server.file.StaticFileServerHandler;
import io.viper.core.server.file.ThumbnailFileContentInfoProvider;
import io.viper.core.server.router.GetRoute;
import io.viper.core.server.router.PostRoute;
import io.viper.core.server.router.Route;
import io.viper.core.server.router.RouteHandler;
import io.viper.core.server.router.RouteResponse;
import io.viper.core.server.router.RouterMatcherUpstreamHandler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;
import org.jboss.netty.channel.DefaultChannelPipeline;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.channel.group.ChannelGroupFuture;
import org.jboss.netty.channel.group.DefaultChannelGroup;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.json.JSONException;


public class PhotoServer
{
  private ServerBootstrap _bootstrap;

  static final ChannelGroup allChannels = new DefaultChannelGroup("server");

  public static PhotoServer create(
    String localhostName,
    int port,
    String staticFileRoot,
    String uploadDir,
    PhotosController photosController
  )
      throws Exception
  {
    AsyncScalr.setService(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
    AsyncScalr.setServiceThreadCount(Runtime.getRuntime().availableProcessors());

    PhotoServer photoServer = new PhotoServer();

    photoServer._bootstrap = new ServerBootstrap(new NioServerSocketChannelFactory(Executors.newCachedThreadPool(), Executors.newCachedThreadPool()));

    String localhost = String.format("http://%s:%s", localhostName, port);

    ChannelPipelineFactory pipelineFactory =
      new PhotosPipelineFactory(
        (1024 * 1024) * 1024,
        uploadDir,
        staticFileRoot,
        localhost,
        photosController);

    photoServer._bootstrap.setOption("tcpNoDelay", true);
    photoServer._bootstrap.setOption("keepAlive", true);

    photoServer._bootstrap.setPipelineFactory(pipelineFactory);
    Channel channel = photoServer._bootstrap.bind(new InetSocketAddress(port));

    allChannels.add(channel);

    return photoServer;
  }

  public void shutdown()
  {
    ChannelGroupFuture future = allChannels.close();
    future.awaitUninterruptibly();

    AsyncScalr.getService().shutdownNow();
  }

  private static class PhotosPipelineFactory implements ChannelPipelineFactory
  {
    final int _maxContentLength;
    final String _uploadFileRoot;
    final String _staticFileRoot;
    final FileContentInfoProvider _thumbFileProvider;
    final FileContentInfoProvider _staticFileProvider;
    final FileContentInfoProvider _photoFileProvider;
    final String _downloadHostname;
    final PhotosController _photosController;

    public PhotosPipelineFactory(int maxContentLength,
                                 String uploadFileRoot,
                                 String staticFileRoot,
                                 String downloadHostname,
                                 PhotosController photosController)
      throws IOException, JSONException
    {
      _maxContentLength = maxContentLength;
      _uploadFileRoot = uploadFileRoot;
      _staticFileRoot = staticFileRoot;
      _downloadHostname = downloadHostname;

      _staticFileProvider = StaticFileContentInfoProvider.create(_staticFileRoot);
      _photoFileProvider = StaticFileContentInfoProvider.create(_uploadFileRoot);
      _thumbFileProvider = ThumbnailFileContentInfoProvider.create(_uploadFileRoot);

      _photosController = photosController;
    }

    @Override
    public ChannelPipeline getPipeline()
      throws Exception
    {
      List<Route> routes = new ArrayList<Route>();

      routes.add(new GetRoute("/photos/myfeed", new RouteHandler()
      {
        @Override
        public RouteResponse exec(Map<String, String> args)
          throws Exception
        {
          return _photosController.getMemberPhotoFeed(args);
        }
      }));

      routes.add(new GetRoute("/photos/feed", new RouteHandler()
      {
        @Override
        public RouteResponse exec(Map<String, String> args)
          throws Exception
        {
          return _photosController.getPublicPhotoFeed(args);
        }
      }));

      routes.add(new GetRoute("/photos/photocomments", new RouteHandler()
      {
        @Override
        public RouteResponse exec(Map<String, String> args)
          throws Exception
        {
          return _photosController.getPhotoFeed(args);
        }
      }));

      routes.add(new PostRoute("/photos/add", new RouteHandler()
      {
        @Override
        public RouteResponse exec(Map<String, String> args)
          throws Exception
        {
          return _photosController.addPhotoEvent(args);
        }
      }));

      routes.add(new PostRoute("/photos/comments", new RouteHandler()
      {
        @Override
        public RouteResponse exec(Map<String, String> args)
          throws Exception
        {
          return _photosController.addPhotoCommentEvent(args);
        }
      }));

      HttpChunkRelayProxy proxy = new FileChunkProxy(_uploadFileRoot);
      FileUploadChunkRelayEventListener relayListener = new FileUploadChunkRelayEventListener(_downloadHostname);
      routes.add(new HttpChunkProxyHandler("/u/", proxy, relayListener, _maxContentLength));

      routes.add(new GetRoute("/thumb/$path", new StaticFileServerHandler(_thumbFileProvider)));
      routes.add(new GetRoute("/d/$path", new StaticFileServerHandler(_photoFileProvider)));
      routes.add(new GetRoute("/$path", new StaticFileServerHandler(_staticFileProvider)));

      ChannelPipeline lhPipeline = new DefaultChannelPipeline();
      lhPipeline.addLast("decoder", new HttpRequestDecoder());
      lhPipeline.addLast("encoder", new HttpResponseEncoder());
      lhPipeline.addLast("router", new RouterMatcherUpstreamHandler("uri-handlers", routes));

      return lhPipeline;
    }
  }
}