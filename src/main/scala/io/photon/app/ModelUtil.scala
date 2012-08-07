package io.photon.app

import org.json.{JSONArray, JSONObject}
import org.jboss.netty.handler.codec.http2.FileUpload
import java.util.Date
import cloudcmd.common.util.JsonUtil
import cloudcmd.common.FileMetaData

object ModelUtil {

  def hasReadPriv(meta: JSONObject, ownerId: String) : Boolean = isOwner(meta, ownerId) || isPublic(meta)
  def hasSharePriv(meta: JSONObject, ownerId: String) : Boolean = isCreator(meta, ownerId) || isPublic(meta)
  def isCreator(meta: JSONObject, ownerId: String) : Boolean = {
    ownerId.toLowerCase == (if (meta.has("ownerId")) meta.getString("creatorId") else "eismcc")
  }
  def isOwner(meta: JSONObject, ownerId: String) : Boolean = {
    ownerId.toLowerCase == (if (meta.has("ownerId")) meta.getString("ownerId") else "eismcc")
  }
  def isPublic(meta: JSONObject) : Boolean = meta.has("isPublic") && meta.getBoolean("isPublic")

  def createFileMeta(upload: FileUpload, creatorId: String, ownerId: String, isPublic: Boolean, thumbHash: String, thumbSize: Long, blockHashes: List[String], tags: Set[String]) : FileMetaData = {
    val fileName = upload.getFilename
    val extIndex = fileName.lastIndexOf(".")

    val blocksArr = new JSONArray()
    blockHashes.foreach(blocksArr.put)

    val tagsArr = new JSONArray()
    tags.foreach(tagsArr.put)

    FileMetaData.create(
      JsonUtil.createJsonObject(
        "path", fileName,
        "filename", fileName,
        "fileext", if (extIndex >= 0) { fileName.substring(extIndex + 1) } else { null },
        "filesize", upload.length().asInstanceOf[AnyRef],
        "filedate", new Date().getTime.asInstanceOf[AnyRef],
        "blocks", blocksArr,
        "mimeType", upload.getContentType,
        "tags", tagsArr, // tags cloudcmd style
        "thumbHash", thumbHash,
        "thumbSize", thumbSize.asInstanceOf[AnyRef],
        "creatorId", creatorId.toLowerCase,
        "isPublic", isPublic.asInstanceOf[AnyRef],
        "ownerId", ownerId.toLowerCase,
        "keywords", tags.mkString(" ") // raw tags for indexing
        ))
  }

  def reshareMeta(meta: JSONObject, sharee: String) : JSONObject = {
    val obj = new JSONObject(meta.toString)
    if (obj.has("__id")) obj.remove("__id")
    obj.put("ownerId", sharee.toLowerCase)
    obj
  }

  def createResponseData(session: TwitterSession, rawFmd: JSONObject, docId: String) : JSONObject = {
    createResponseData(session, FileMetaData.create(rawFmd), docId)
  }

  def createResponseData(session: TwitterSession, fmd: FileMetaData, docId: String) : JSONObject = {
    val rawData = fmd.toJson.getJSONObject("data")

    val id = docId.replaceFirst(".meta","")

    val obj = new JSONObject()
    obj.put("id", id)
    obj.put("url", String.format("/d/%s", id)) // references stored.io doc that contains the real file reference
    obj.put("name", fmd.getFilename)
    obj.put("type", fmd.getType)
    obj.put("size", fmd.getFileSize)
    obj.put("filedate", fmd.getFileDate)

    val properties = if (rawData.has("properties")) {
      rawData.getJSONObject("properties")
    } else if (rawData.has("creatorId")) {
      rawData
    } else {
      JsonUtil.createJsonObject(
        "keywords", JsonUtil.createSet(rawData.getJSONArray("tags")).mkString(" "),
        "creatorId", "eismcc",
        "ownerId", "eismcc",
        "isPublic", false.asInstanceOf[AnyRef]
      )
    }

    obj.put("thumbnail_url", "/t/%s".format(id)) // references stored.io doc that contains the real thumbnail reference
    obj.put("delete_url", String.format("/d/%s", id))
    obj.put("delete_type", "DELETE")

    if (properties != null) {
      obj.put("tags", properties.getString("keywords"))
      obj.put("creatorId", properties.getString("creatorId"))
      obj.put("ownerId", properties.getString("ownerId"))
      obj.put("creatorProfileImg", session.getProfileImageUrl(properties.getString("creatorId")))
      obj.put("isPublic", properties.getBoolean("isPublic"))
      obj.put("isSharable", hasSharePriv(fmd.getRawData, properties.getString("ownerId")))
    }

    obj
  }
}
