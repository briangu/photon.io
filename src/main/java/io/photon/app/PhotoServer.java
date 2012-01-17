package io.photon.app;


import com.thebuzzmedia.imgscalr.AsyncScalr;
import httpjsonclient.HttpJSONClient;
import io.viper.core.server.Util;
import io.viper.core.server.file.FileChunkProxy;
import io.viper.core.server.file.FileContentInfoProvider;
import io.viper.core.server.file.HttpChunkProxyHandler;
import io.viper.core.server.file.HttpChunkRelayProxy;
import io.viper.core.server.file.StaticFileContentInfoProvider;
import io.viper.core.server.file.StaticFileServerHandler;
import io.viper.core.server.file.ThumbnailFileContentInfoProvider;
import io.viper.core.server.router.DeleteRoute;
import io.viper.core.server.router.GetRoute;
import io.viper.core.server.router.PostRoute;
import io.viper.core.server.router.PutRoute;
import io.viper.core.server.router.Route;
import io.viper.core.server.router.RouteHandler;
import io.viper.core.server.router.RouteResponse;
import io.viper.core.server.router.RouterMatcherUpstreamHandler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
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
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequestDecoder;
import org.jboss.netty.handler.codec.http.HttpResponseEncoder;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.json.JSONException;
import org.json.JSONObject;


public class PhotoServer
{
  private ServerBootstrap _bootstrap;

  static final ChannelGroup allChannels = new DefaultChannelGroup("server");

  public static PhotoServer create(
    String localhostName,
    int port,
    String staticFileRoot,
    String uploadDir,
    String backendHost,
    HttpJSONClient publishClient,
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
        backendHost,
        publishClient,
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
    final String _backendHost;
    final HttpJSONClient _publishClient;
    final PhotosController _photosController;

    public PhotosPipelineFactory(int maxContentLength,
                                 String uploadFileRoot,
                                 String staticFileRoot,
                                 String downloadHostname,
                                 String backendHost,
                                 HttpJSONClient publishClient,
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

      _backendHost = backendHost;
      _publishClient = publishClient;
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

      routes.add(new GetRoute("/feeds/public", new RouteHandler()
      {
        @Override
        public RouteResponse exec(Map<String, String> args)
          throws Exception
        {
          return _photosController.getPublicPhotoFeed(args);
        }
      }));

      routes.add(new GetRoute("/posts/$postId", new RouteHandler()
      {
        @Override
        public RouteResponse exec(Map<String, String> args)
          throws Exception
        {
          return _photosController.getPosts(args);
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
          return _photosController.addPhotoAlbum(args);
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

      routes.add(new PutRoute("/me/likes/$memberId/$objectId", new RouteHandler()
      {
        @Override
        public RouteResponse exec(Map<String, String> args)
          throws Exception
        {
          String memberId = args.get("memberId");
          String objectId = args.get("objectId");

          HttpJSONClient likeClient = HttpJSONClient.create(String.format("%s/likes/actorId=%s&objectId=%s", _backendHost, memberId, objectId));

          return Util.createJsonResponse(likeClient.doPut("{}"));
        }
      }));

      routes.add(new DeleteRoute("/me/likes/$memberId/$objectId", new RouteHandler()
      {
        @Override
        public RouteResponse exec(Map<String, String> args)
          throws Exception
        {
          String memberId = args.get("memberId");
          String objectId = args.get("objectId");

          HttpJSONClient likeClient = HttpJSONClient.create(String.format("%s/likes/actorId=%s&objectId=%s", _backendHost, memberId, objectId));

          return Util.createJsonResponse(likeClient.doDelete());
        }
      }));

      routes.add(new PostRoute("/activities/$activityId/comments", new RouteHandler()
      {
        @Override
        public RouteResponse exec(Map<String, String> args)
            throws Exception
        {
          String id = args.get("id");
          String name = args.get("name");
          String message = args.get("message");
          String activityId = args.get("activityId");

          JSONObject commentRequest = new JSONObject();
          commentRequest.put("name", name);
          commentRequest.put("commenterId", "member:" + id);
          commentRequest.put("message", message);
          commentRequest.put("app", "photon");

          HttpJSONClient createCommentClient = HttpJSONClient.create(String.format("%s/threads/%s/comments", _backendHost, activityId));
          HttpJSONClient.JSONRequestResponse result = createCommentClient.doPost(commentRequest);
          if (result.StatusCode == 201)
          {
            JSONObject meta = result.JsonResult.getJSONObject("meta");
            String commentId = meta.getString("Id");

            HttpJSONClient getCommentClient = HttpJSONClient.create(String.format("%s/threads/%s/comments/%s", _backendHost, activityId, commentId));

            HttpJSONClient.JSONRequestResponse commentResult = getCommentClient.doQuery();
            if (commentResult.StatusCode == 200)
            {
              return Util.createJsonResponse(commentResult.JsonResult);
            }
          }

          return new RouteResponse(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST));
        }
      }));

      routes.add(new PostRoute("/text/add", new RouteHandler()
      {
        @Override
        public RouteResponse exec(Map<String, String> args)
            throws Exception
        {
          String id = args.get("id");
          String msg = args.get("msg");

          String member = String.format("member:%s", id);
          JSONObject post = new JSONObject();
          post.put("actor", member);

          JSONObject verb = new JSONObject();
          verb.put("type", "linkedin:post");
          verb.put("commentary", msg);
          post.put("verb", verb);

          post.put("object", new JSONObject());

          post.put("app", "photon");

          return Util.createJsonResponse(_publishClient.doPost(post.toString(2), Collections.<String, String>emptyMap()));
        }
      }));

      HttpChunkRelayProxy proxy = new FileChunkProxy(_uploadFileRoot);
      FileUploadChunkRelayEventListener relayListener = new FileUploadChunkRelayEventListener(_downloadHostname);
      routes.add(new HttpChunkProxyHandler("/u/", proxy, relayListener, _maxContentLength));

      routes.add(new GetRoute("/thumb/$path", new StaticFileServerHandler(_thumbFileProvider)));
      routes.add(new GetRoute("/d/$path", new StaticFileServerHandler(_photoFileProvider)));
      routes.add(new GetRoute("/$path", new StaticFileServerHandler(_staticFileProvider)));
      routes.add(new GetRoute("/", new StaticFileServerHandler(_staticFileProvider)));

      ChannelPipeline lhPipeline = new DefaultChannelPipeline();
      lhPipeline.addLast("decoder", new HttpRequestDecoder());
      lhPipeline.addLast("encoder", new HttpResponseEncoder());
      lhPipeline.addLast("router", new RouterMatcherUpstreamHandler("uri-handlers", routes));

      return lhPipeline;
    }
  }
}
