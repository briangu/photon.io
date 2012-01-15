package io.photon.app;


import httpjsonclient.HttpJSONClient;
import io.viper.core.server.Util;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import org.json.JSONException;


public class Main
{
  public static void main(String[] args)
  {
    PhotoServer photoServer = null;

    try
    {
      String queryHost = args[0];
      String publishHost = args[1];
      String localHostName = args[2];
      Integer localHostPort = Integer.parseInt(args[3]);
      String staticFileRoot = args[4];
      String uploadDir = args[5];
      
      HttpJSONClient queryClient = HttpJSONClient.create(queryHost);
      HttpJSONClient publishClient = HttpJSONClient.create(publishHost);
      PhotosController pc = new PhotosController(queryClient, publishClient);
      
      new File(staticFileRoot).mkdir();
      new File(uploadDir).mkdir();

      photoServer = PhotoServer.create(
        localHostName,
        localHostPort,
        staticFileRoot,
        uploadDir,
        publishClient,
        pc);

      System.in.read();
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
    finally
    {
      if (photoServer != null)
      {
        photoServer.shutdown();
      }
    }
  }
}
