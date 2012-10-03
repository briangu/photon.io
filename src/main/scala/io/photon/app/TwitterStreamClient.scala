package io.photon.app

import org.apache.log4j.Logger
import twitter4j._
import collection.mutable.ListBuffer
import scala.collection
import collection.mutable
import org.json.{JSONObject, JSONArray}

class TwitterStreamClient() {
  private val log = Logger.getLogger(classOf[TwitterStreamClient])

  var twitterStream : TwitterStream = null
  var followingFunnel: Map[Long, List[Long]] = null
  var followingGraph: Map[Long, List[String]] = null

  def init() {

    followingGraph = loadFollowingGraph()
    followingFunnel = loadCachedFunnel()

    val listener = new StatusListener() {
      def onStatus(status: Status) {
        //if (followingFunnel.contains(status.getUser.getId)) {
        if (true) {
          if (status.getMediaEntities != null) {
            val media = status.getMediaEntities
            media.foreach{ entity =>
              entity.getType match {
                case "photo" => {
                  // TODO: fanout one copy of the meta data for each recipient
                  println(status.getUser().getName() + " : " + entity.getMediaURL())
//                  tweetProcessor.add(status, 1234)
                  /*
                  followingFunnel.get(status.getUser.getId).get.foreach{ userId =>
                    tweetProcessor.add(status, userId)
                  }
                  */
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
/*
    val cb = new ConfigurationBuilder
    cb.setDebugEnabled(true)
    cb.setOAuthConsumerKey(System.getProperty("twitter4j.oauth.consumerKey"))
    cb.setOAuthConsumerSecret(System.getProperty("twitter4j.oauth.consumerSecret"))
    cb.setOAuthAccessToken(System.getProperty("oauth.at"))
    cb.setOAuthAccessTokenSecret(System.getProperty("oauth.ats"))
    twitterStream = new TwitterStreamFactory(cb.build()).getInstance()
    twitterStream.addListener(listener)
    twitterStream.sample()
*/
  }

  def loadFollowingGraph() : Map[Long, List[String]] = {
    val map = new collection.mutable.HashMap[Long, List[String]]

    val cachedFollowing = FileUtils.readResourceFile(this.getClass, "/config/photon.io/followgraph.json")
    if (cachedFollowing != null) {
      val json = new JSONObject(cachedFollowing)
      val keys = json.keys()
      while (keys.hasNext) {
        val key = keys.next().toString
        val lkey = key.toLong
        val jsonMembers = json.getJSONArray(key)
        val members = new ListBuffer[String]
        (0 until jsonMembers.length()).foreach{ idx =>
          members.append(jsonMembers.getString(idx))
        }
        map.put(lkey, members.toList)
      }
    }

    map.toMap
  }

  def loadCachedFunnel() : Map[Long, List[Long]] = {
    val map = new collection.mutable.HashMap[Long, List[Long]]

    val cachedFunnel = FileUtils.readResourceFile(this.getClass, "/config/photon.io/followfunnel.json")
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

  def buildFollowGraph() : Map[Long, List[String]] = {
    val employees = getTwitterTeamMembers

    val map = new collection.mutable.HashMap[Long, List[String]]

    employees.foreach{ userId : Long => {
      try {
        println("getFollowing: " + userId.toString)
        val userCache = new mutable.HashMap[Long, User]()

        val following = getFollowing(userId) ++ List(userId)
        val followingNames = List() ++ following.flatMap { followingId =>
          if (!userCache.contains(followingId)) {
            try {
              val twitter = TwitterFactory.getSingleton
              val followingUser = twitter.showUser(followingId)
              userCache.put(followingId, followingUser)
            } catch {
              case e:Exception => {
                println("failed to resolve userId: " + followingId)
              }
            }
          }
          if (userCache.contains(followingId)) {
            List(userCache.get(followingId).get.getScreenName)
          } else {
            Nil
          }
        }

        map.put(userId, followingNames)
      } catch {
        case e: Exception => {
          println("failed to query user: " + userId)
        }
      }
    }}

    val obj = new JSONObject()
    map.keySet.foreach{ key => {
      val following = new JSONArray()
      map.get(key).get.foreach(following.put)
      obj.put(key.toString, following)
    }}

    println(obj.toString())

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
