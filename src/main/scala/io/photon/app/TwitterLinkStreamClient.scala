package io.photon.app

import org.apache.log4j.Logger
import twitter4j._
import conf.ConfigurationBuilder
import collection.mutable.ListBuffer
import scala.collection
import org.json.{JSONObject, JSONArray}

class TwitterLinkStreamClient {
  private val log = Logger.getLogger(classOf[TwitterLinkStreamClient])

  var twitterStream : TwitterStream = null
  var followingFunnel: Map[Long, List[Long]] = null

  def init() {

    followingFunnel = loadCachedFunnel()

    val listener = new StatusListener() {
      def onStatus(status: Status) {
        if (status.getMediaEntities != null) {
          val media = status.getMediaEntities
          media.foreach{ entity =>
            entity.getType match {
              case "photo" => {
                if (followingFunnel.contains(status.getUser.getId)) {
                  println(status.getUser().getName() + " : " + status.getText())
                }
              }
            }
          }
        }
      }
      def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) {}
      def onTrackLimitationNotice(numberOfLimitedStatuses: Int) {}
      def onException(ex: Exception) {
        ex.printStackTrace()
      }
      def onScrubGeo(p1: Long, p2: Long) {}
    }

    val cb = new ConfigurationBuilder
    cb.setDebugEnabled(true)
    cb.setOAuthConsumerKey(System.getProperty("twitter4j.oauth.consumerKey"))
    cb.setOAuthConsumerSecret(System.getProperty("twitter4j.oauth.consumerSecret"))
    cb.setOAuthAccessToken(System.getProperty("oauth.at"))
    cb.setOAuthAccessTokenSecret(System.getProperty("oauth.ats"))
    twitterStream = new TwitterStreamFactory(cb.build()).getInstance()
    twitterStream.addListener(listener)
    twitterStream.sample()
  }

  def loadCachedFunnel() : Map[Long, List[Long]] = {
    val map = new collection.mutable.HashMap[Long, List[Long]]

    val cachedFunnel = FileUtils.readResourceFile(this.getClass, "/config/photon.io/twitter_team.json")
    if (cachedFunnel != null) {
      val json = new JSONObject(cachedFunnel)
      val keys = json.keys()
      while (keys.hasNext) {
        val key = keys.next().toString
        val lkey = key.toLong
        val jsonMembers = json.getJSONArray(key)
        val members = new ListBuffer[Long]
        (0 until jsonMembers.length()).foreach{ idx =>
          members.append(jsonMembers.getLong(idx))
        }
        map.put(lkey, members.toList)
      }
    }

    map.toMap
  }

  // get all twitter employees
  // for each twitter employee,
  //    get all the members the user follows as Follows
  //    for all Follows + emp
  def buildFollowingFunnel() : Map[Long, List[Long]] = {
    val employees = getTwitterTeamMembers

    val map = new collection.mutable.HashMap[Long, List[Long]]

    employees.foreach{ userId : Long => {
      try {
        println("getFollowing: " + userId.toString)

        val following = getFollowing(userId) ++ List(userId)
        following.foreach { followingId =>
          if (!map.contains(followingId)) {
            map.put(followingId, List())
          }
          map.put(followingId, map.get(followingId).get ++ List(userId))
        }
      } catch {
        case e: Exception => {
          println("failed to query user: " + userId)
        }
      }
    }}

    val obj = new JSONObject()
    map.keySet.foreach{ key => {
      val followers = new JSONArray()
      map.get(key).get.foreach(followers.put)
      obj.put(key.toString, followers)
    }}

    println(obj.toString())

    map.toMap
  }

  def getFollowing(userId: Long) : List[Long] = {
    val twitter = TwitterFactory.getSingleton
    val members = new ListBuffer[Long]
    var response: IDs = null
    var cursor : Long = -1
    do {
      response = twitter.getFriendsIDs(userId, cursor)
      members.appendAll(response.getIDs)
      cursor = response.getNextCursor
    } while(response.hasNext)
    members.toList
  }

  def getTwitterTeamMembers() : List[Long] = {
    val twitter = TwitterFactory.getSingleton
    val members = new ListBuffer[Long]
    var response: PagableResponseList[User] = null
    var cursor : Long = -1
    do {
      response = twitter.getUserListMembers(574, cursor)
      val iter = response.listIterator()
      while (iter.hasNext) {
        members.append(iter.next().getId)
      }
      cursor = response.getNextCursor
    } while(response.hasNext)
    members.toList
  }

  def shutdown() {
    if (twitterStream != null) {
      twitterStream.shutdown()
    }
  }
}
