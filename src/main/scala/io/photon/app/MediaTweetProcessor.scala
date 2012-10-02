package io.photon.app

import cloudcmd.common.engine.CloudEngine
import cloudcmd.common.engine.DefaultFileProcessor
import cloudcmd.common.engine.IndexStorage
import cloudcmd.common.FileMetaData
import cloudcmd.common.util.JsonUtil
import org.apache.log4j.Logger
import java.util.Date
import twitter4j.Status
import org.json.{JSONArray, JSONObject}
import twitter4j.json.DataObjectFactory

object MediaTweetProcessor {
  val emptyArr = new JSONArray
}

class MediaTweetProcessor(cloudEngine: CloudEngine, indexStorage: IndexStorage) {

  private val log = Logger.getLogger(classOf[DefaultFileProcessor])

  def add(status: Status, destUserId: Long) : FileMetaData = {
    try {
      val tags = new JSONArray()
      tags.put(status.getText)

      val rawFmd =
        JsonUtil.createJsonObject(
          "path", "",
          "filename", null,
          "fileext", null,
          "filesize", null.asInstanceOf[AnyRef],
          "filedate", status.getCreatedAt.getTime.asInstanceOf[AnyRef],
          "createdDate", new Date().getTime.asInstanceOf[AnyRef],  // TODO: this is not ideal as it forces duplicates
          "blocks", MediaTweetProcessor.emptyArr,
          "tags", tags)

      rawFmd.put("properties", new JSONObject(DataObjectFactory.getRawJSON(status)))

      rawFmd.put("mimeType", null.asInstanceOf[AnyRef])

      val fmd = FileMetaData.create(rawFmd)

//      cloudEngine.store(fmd.createBlockContext, new ByteArrayInputStream(fmd.getDataAsString.getBytes("UTF-8")))

//      indexStorage.add(fmd)

      fmd
    }
    finally {
    }
  }
}
