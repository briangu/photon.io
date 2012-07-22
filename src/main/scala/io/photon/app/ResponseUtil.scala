package io.photon.app

import cloudcmd.common.FileMetaData
import org.json.JSONObject

object ResponseUtil {

  def createResponseData(rawFmd: JSONObject, docId: String) : JSONObject = {
    createResponseData(FileMetaData.create(rawFmd), docId)
  }

  def createResponseData(fmd: FileMetaData, docId: String) : JSONObject = {
    import collection.JavaConversions._

    val rawData = fmd.toJson.getJSONObject("data")
    val obj = new JSONObject()
    obj.put("id", docId)
    obj.put("thumbnail_url", "/t/%s".format(docId)) // references stored.io doc that contains the real thumbnail reference
    obj.put("url", String.format("/d/%s", docId)) // references stored.io doc that contains the real file reference
    obj.put("name", fmd.getFilename)
    obj.put("type", rawData.getString("type"))
    obj.put("size", fmd.getFileSize)
    obj.put("tags", rawData.getString("keywords"))
    obj.put("filedate", fmd.getFileDate)
    obj.put("ownerId", rawData.getString("ownerId"))
    obj.put("delete_url", String.format("/d/%s", docId))
    obj.put("delete_type", "DELETE")
    obj
  }
}
