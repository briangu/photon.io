package io.photon.app

import cloudcmd.common.{JsonUtil, FileMetaData}
import org.json.{JSONArray, JSONObject}
import org.jboss.netty.handler.codec.http2.FileUpload
import java.util.Date

object ModelUtil {

  def createFileMeta(upload: FileUpload, creatorId: String, ownerId: String, isPublic: Boolean, thumbHash: String, thumbSize: Long, blockHashes: List[String], tags: String) : FileMetaData = {
    val fileName = upload.getFilename
    val extIndex = fileName.lastIndexOf(".")

    val blocksArr = new JSONArray()
    blockHashes.foreach(blocksArr.put)

    val tagsArr = new JSONArray()
    tags.split(' ').foreach(tagsArr.put)

    FileMetaData.create(
      JsonUtil.createJsonObject(
        "path", fileName,
        "filename", fileName,
        "fileext", if (extIndex >= 0) { fileName.substring(extIndex + 1) } else { null },
        "filesize", upload.length().asInstanceOf[AnyRef],
        "filedate", new Date().getTime.asInstanceOf[AnyRef],
        "blocks", blocksArr,
        "type", upload.getContentType,
        "tags", tagsArr, // tags cloudcmd style
        "thumbHash", thumbHash,
        "thumbSize", thumbSize.asInstanceOf[AnyRef],
        "creatorId", creatorId,
        "isPublic", isPublic.asInstanceOf[AnyRef],
        "ownerId", ownerId,
        "keywords", tags // raw tags for indexing
      ))
  }

  def reshareMeta(meta: JSONObject, sharee: String) : JSONObject = {
    val obj = new JSONObject(meta.toString)
    if (obj.has("__id")) obj.remove("__id")
    obj.put("ownerId", sharee)
    obj
  }

  def createResponseData(rawFmd: JSONObject, docId: String) : JSONObject = {
    createResponseData(FileMetaData.create(rawFmd), docId)
  }

  def createResponseData(fmd: FileMetaData, docId: String) : JSONObject = {
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
    obj.put("creatorId", rawData.getString("creatorId"))
    obj.put("ownerId", rawData.getString("ownerId"))
    obj.put("isPublic", rawData.getBoolean("isPublic"))
    obj.put("delete_url", String.format("/d/%s", docId))
    obj.put("delete_type", "DELETE")
    obj
  }
}
