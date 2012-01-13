package io.photon.app;


import httpjsonclient.HttpJSONClient;
import io.viper.core.server.Util;
import io.viper.core.server.router.RouteResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONObject;


public class PhotosController
{
  final String _commentUrlTemplate = "http://localhost:1338/threads/%s/comments";
  final HttpJSONClient _queryClient;
  final HttpJSONClient _publishClient;
  final Map<String, String> _headers = new HashMap<String, String>();

  public PhotosController(HttpJSONClient queryClient, HttpJSONClient publishClient)
  {
    _queryClient = queryClient;
    _publishClient = publishClient;
  }

  public RouteResponse getMemberPhotoFeed(Map<String, String> args)
    throws Exception
  {
    String id = args.get("id");
    List<NameValuePair> queryParams = new ArrayList<NameValuePair>();
    queryParams.add(new BasicNameValuePair("q", "feed"));
    queryParams.add(new BasicNameValuePair("id", "photon:member"));
    queryParams.add(new BasicNameValuePair("member", String.format("member:%s", id)));
    return Util.createJsonResponse(_queryClient.doQuery(queryParams));
  }

  public RouteResponse getPublicPhotoFeed(Map<String, String> args)
    throws Exception
  {
    String id = args.get("id");
    List<NameValuePair> queryParams = new ArrayList<NameValuePair>();
    queryParams.add(new BasicNameValuePair("q", "feed"));
    queryParams.add(new BasicNameValuePair("id", String.format("photon:public", id)));
    return Util.createJsonResponse(_queryClient.doQuery(queryParams));
  }

  public RouteResponse getPhotoFeed(Map<String, String> args)
    throws Exception
  {
    String threadId = args.get("threadId");
    List<NameValuePair> queryParams = new ArrayList<NameValuePair>();
    queryParams.add(new BasicNameValuePair("q", "feed"));
    queryParams.add(new BasicNameValuePair("id", String.format("photon:photo:comments:__threadId=%s", threadId)));
    queryParams.add(new BasicNameValuePair("viewerId", String.format("feed:photon:photo:comments:__threadId=%s", threadId)));
    return Util.createJsonResponse(_queryClient.doQuery(queryParams));
  }

  // curl -d "id=45310686&photoId=urn:photo:123&thumbnail=http://farm5.static.flickr.com/4105/4994478045_61d71e0b46_o.jpg" http://bguarrac-md:3000/photos/add
  public RouteResponse addPhotoEvent(Map<String, String> args)
      throws Exception
  {
    String id = args.get("id");
    String photoId = args.get("photoId");
    String thumbnail = args.get("thumbnail");
    String url = args.get("url");

    String member = String.format("member:%s", id);
    JSONObject post = new JSONObject();
    post.put("actor", member);
    
    JSONObject verb = new JSONObject();
    verb.put("type", "linkedin:share");
    post.put("verb", verb);

    JSONArray properties = new JSONArray();
    JSONObject property = new JSONObject();
    property.put("property", "id");
    property.put("content", photoId);
    properties.put(property);
    JSONObject object = new JSONObject();
    object.put("type", "photon:photo");
    object.put("title", "a photo");
    object.put("description", "");
    object.put("image", thumbnail);
    object.put("url", url);
    object.put("properties", properties);
    post.put("object", object);

    post.put("app", "photon");

    return Util.createJsonResponse(_publishClient.doPost(post.toString(2), _headers));
  }

  public RouteResponse addPhotoCommentEvent(Map<String, String> args)
    throws Exception
  {
    String id = args.get("id");
    String threadId = args.get("threadId");
    String message = args.get("message");

    JSONObject post = new JSONObject();
    post.put("commenterId", id);
    post.put("message", message);

    String url = String.format(_commentUrlTemplate, threadId);
    HttpJSONClient commentsClient = HttpJSONClient.create(url);

    return Util.createJsonResponse(commentsClient.doPost(post.toString(2), _headers));
  }
}
