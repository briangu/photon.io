package io.photon.app

import org.json.JSONObject
import cloudcmd.common.FileMetaData

object ModelUtil {

  def hasReadPriv(meta: FileMetaData, ownerId: Long) : Boolean = isOwner(meta, ownerId) || isPublic(meta)
  def hasSharePriv(meta: FileMetaData, ownerId: Long) : Boolean = isCreator(meta, ownerId) || isPublic(meta)

  def isCreator(meta: FileMetaData, creatorId: Long) : Boolean = {
    creatorId == (if (meta.hasProperty("creatorId")) meta.getProperties().getLong("creatorId") else 13257392L)
  }
  def isOwner(meta: FileMetaData, ownerId: Long) : Boolean = {
    ownerId == (if (meta.hasProperty("ownerId")) meta.getProperties().getLong("ownerId") else 13257392L)
  }
  def isPublic(meta: FileMetaData) : Boolean = {
    (if (meta.hasProperty("isPublic")) meta.getProperties().getBoolean("isPublic") else false)
  }

  def reshareMeta(meta: JSONObject, sharee: Long) : JSONObject = {
    val obj = new JSONObject(meta.toString)
    if (obj.has("__id")) obj.remove("__id") // for stored.io row ids
    if (!obj.has("properties")) {
      obj.put("properties", new JSONObject)
    }
    obj.getJSONObject("properties").put("ownerId", sharee)
    obj
  }

  def createResponseDataFromTweet(session: TwitterSession, rawTweet: JSONObject) : JSONObject = {
    val obj = new JSONObject()

    val id = rawTweet.getString("id_str")
    obj.put("id", id)

    val user = rawTweet.getJSONObject("user")
    val creator = if (rawTweet.has("retweeted_status")) {
      rawTweet.getJSONObject("retweeted_status").getJSONObject("user")
    } else {
      user
    }

    val entities = rawTweet.getJSONObject("entities")
    val media = entities.getJSONArray("media")

    if (media.length() > 0) {
      obj.put("url", media.getJSONObject(0).getString("expanded_url"))
      obj.put("thumbnail_url",  media.getJSONObject(0).getString("media_url"))
    }

    // TODO: use text directly
    obj.put("tags", rawTweet.getString("text"))
    obj.put("creatorName", creator.getString("screen_name"))
    obj.put("ownerName", user.getString("screen_name"))
    obj.put("creatorProfileImg", user.getString("profile_image_url"))
    obj.put("isPublic", true)
    obj.put("isSharable", true)

    obj
  }

  def createResponseData(session: TwitterSession, rawFmd: JSONObject, docId: String) : JSONObject = {
    createResponseData(session, FileMetaData.create(rawFmd), docId)
  }

  def createResponseData(session: TwitterSession, fmd: FileMetaData, docId: String) : JSONObject = {
    val id = docId.replaceFirst(".meta","")

    val obj = new JSONObject()
    obj.put("id", id)
    obj.put("url", String.format("/d/%s", id))
    obj.put("name", fmd.getFilename)
    obj.put("type", fmd.getType)
    obj.put("size", fmd.getFileSize)
    obj.put("filedate", fmd.getFileDate)

    val properties = fmd.getProperties()

    obj.put("thumbnail_url", "/t/%s".format(id))
    obj.put("delete_url", String.format("/d/%s", id))
    obj.put("delete_type", "DELETE")

    obj.put("tags", fmd.getTags.mkString(" "))
    obj.put("creatorName", session.getScreenName(properties.getLong("creatorId")))
    obj.put("ownerName", session.getScreenName(properties.getLong("ownerId")))
    obj.put("creatorProfileImg", session.getProfileImageUrl(properties.getLong("creatorId")))
    obj.put("isPublic", properties.getBoolean("isPublic"))
    obj.put("isSharable", hasSharePriv(fmd, properties.getLong("ownerId")))

    obj
  }
}
