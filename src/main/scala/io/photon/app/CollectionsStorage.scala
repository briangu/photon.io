package io.photon.app

import org.apache.log4j.Logger
import org.h2.fulltext.{FullText, FullTextLucene}
import org.h2.jdbcx.JdbcConnectionPool
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.sql.{PreparedStatement, SQLException, Statement, Connection}
import collection.mutable.ListBuffer
import cloudcmd.common.{StringUtil, FileUtil, SqlUtil}
import cloudcmd.common.util.JsonUtil
import collection.mutable

class CollectionsStorage() {
  private val log = Logger.getLogger(classOf[CollectionsStorage])

  private val BATCH_SIZE = 1024
  private val WHITESPACE = " ,:-._" + File.separator
  private val MAX_FETCH_RETRIES = 3

  private var _configRoot: String = null

  private var _cp: JdbcConnectionPool = null

  private def getDbFile = "%s%sindex".format(_configRoot, File.separator)

  private def createConnectionString: String = "jdbc:h2:%s".format(getDbFile)

  private def getDbConnection = _cp.getConnection

  private def getReadOnlyDbConnection: Connection = {
    val conn = getDbConnection
    conn.setReadOnly(true)
    conn
  }

  def init(configRoot: String) {
    _configRoot = configRoot

    Class.forName("org.h2.Driver")
    Class.forName("org.h2.fulltext.FullTextLucene")
    _cp = JdbcConnectionPool.create(createConnectionString, "sa", "sa")
    val file: File = new File(getDbFile + ".h2.db")
    if (!file.exists) {
      purge
    }
  }

  def shutdown {
    if (_cp != null) {
      FullText.closeAll()
      _cp.dispose
      _cp = null
    }
  }

  private def bootstrapDb {
    var db: Connection = null
    var st: Statement = null
    try {
      db = getDbConnection
      st = db.createStatement
      st.execute("DROP TABLE if exists FILE_INDEX")
      st.execute("CREATE TABLE FILE_INDEX ( UID bigint auto_increment PRIMARY KEY, USERNAME VARCHAR, TAGS VARCHAR, ID BIGINT )")
      db.commit

      createLuceneIndex(db)
    }
    catch {
      case e: SQLException => log.error(e)
    }
    finally {
      SqlUtil.SafeClose(st)
      SqlUtil.SafeClose(db)
    }
  }

  private def createLuceneIndex(db: Connection) {
    FullTextLucene.init(db)
    FullText.setWhitespaceChars(db, WHITESPACE)
    FullTextLucene.createIndex(db, "PUBLIC", "FILE_INDEX", "TAGS")
  }

  def purge {
    var db: Connection = null
    try {
      db = getDbConnection
      FullText.dropIndex(db, "PUBLIC", "FILE_INDEX")
      FullTextLucene.dropAll(db)
      FullText.closeAll
    }
    catch {
      case e: SQLException => ;
    }
    finally {
      SqlUtil.SafeClose(db)
    }

    shutdown

    Class.forName("org.h2.fulltext.FullTextLucene")

    var file = new File(getDbFile + ".h2.db")
    if (file.exists) FileUtil.delete(file)

    file = new File(getDbFile)
    if (file.exists) FileUtil.delete(file)

    _cp = JdbcConnectionPool.create(createConnectionString, "sa", "sa")

    bootstrapDb
  }

  private val fields = List("USERNAME", "TAGS", "ID")
  private val addMetaSql = "INSERT INTO FILE_INDEX (%s) VALUES (%s)".format(fields.mkString(","), StringUtil.joinRepeat(fields.size, "?", ","))

  def addTags(userName: String, tags: String, ids: List[Long]) {
    var db: Connection = null
    var statement: PreparedStatement = null
    try {
      db = getDbConnection
      db.setAutoCommit(false)

      val bind = new ListBuffer[AnyRef]
      statement = db.prepareStatement(addMetaSql)

      var k = 0

      for (id <- ids) {
        bind.clear
        bind.append(userName)
        bind.append(tags)
        bind.append(id.asInstanceOf[AnyRef])
        (0 until bind.size).foreach(i => bindVar(statement, i + 1, bind(i)))
        statement.addBatch

        k += 1
        if (k > BATCH_SIZE) {
          statement.executeBatch
          k = 0
        }
      }
      statement.executeBatch
    }
    catch {
      case e: Exception => log.error(e)
    }
    finally {
      SqlUtil.SafeClose(statement)
      SqlUtil.SafeClose(db)
    }
  }

  private def bindVar(statement: PreparedStatement, idx: Int, obj: AnyRef) {
    if (obj.isInstanceOf[String]) {
      statement.setString(idx, obj.asInstanceOf[String])
    }
    else if (obj.isInstanceOf[Long]) {
      statement.setLong(idx, obj.asInstanceOf[Long])
    }
    else if (obj == null) {
      statement.setString(idx, null)
    }
    else {
      throw new IllegalArgumentException("unknown obj type: " + obj.toString)
    }
  }

  def getTagCounts(ids: Set[Long]) : Map[Long, Int] = {
    val results = new mutable.HashMap[Long, Int]()

    if (ids.size == 0) {
      return results.toMap
    }

    var db: Connection = null
    var statement: PreparedStatement = null
    try {
      db = getDbConnection
      val idList = ids.map(_.toString).reduceLeft(_  + "," + _)
      statement = db.prepareStatement("SELECT ID,COUNT(ID) AS TAGCOUNT FROM FILE_INDEX WHERE ID IN (%s) GROUP BY ID".format(idList))

      val rs = statement.executeQuery
      while (rs.next) {
        results.put(rs.getLong("ID"), rs.getInt("TAGCOUNT"))
      }
    }
    catch {
      case e: JSONException => log.error(e)
      case e: SQLException => log.error(e)
    }
    finally {
      SqlUtil.SafeClose(statement)
      SqlUtil.SafeClose(db)
    }

    results.toMap
  }

  def getUserTagInfo(ids: Set[Long], userName: String) : Map[Long, JSONObject] = {
    val results = new mutable.HashMap[Long, JSONObject]()

    if (ids.size == 0) {
      return results.toMap
    }

    var db: Connection = null
    var statement: PreparedStatement = null
    try {
      db = getDbConnection
      val idList = ids.map(_.toString).reduceLeft(_  + "," + _)
      statement = db.prepareStatement("SELECT USERNAME,TAGS,ID FROM FILE_INDEX WHERE ID IN (%s) and USERNAME=?".format(idList))
      statement.setString(1, userName)

      val rs = statement.executeQuery
      while (rs.next) {
        val info = JsonUtil.createJsonObject(
          "id", rs.getLong("ID").asInstanceOf[AnyRef],
          "tags", rs.getString("TAGS"),
          "userName", rs.getString("USERNAME")
        )

        results.put(rs.getLong("ID"), info)
      }
    }
    catch {
      case e: JSONException => log.error(e)
      case e: SQLException => log.error(e)
    }
    finally {
      SqlUtil.SafeClose(statement)
      SqlUtil.SafeClose(db)
    }

    results.toMap
  }

  def getTagInfo(ids: Set[Long]) : Map[Long, JSONObject] = {
    val results = new mutable.HashMap[Long, JSONObject]()

    var db: Connection = null
    var statement: PreparedStatement = null
    try {
      db = getDbConnection
      val idList = ids.map(_.toString).reduceLeft(_  + "," + _)
      statement = db.prepareStatement("SELECT USERNAME,TAGS,ID FROM FILE_INDEX WHERE ID IN (%s)".format(idList))

      val rs = statement.executeQuery
      while (rs.next) {
        val info = JsonUtil.createJsonObject(
          "id", rs.getLong("ID").asInstanceOf[AnyRef],
          "tags", rs.getString("TAGS"),
          "userName", rs.getString("USERNAME")
        )

        results.put(rs.getLong("ID"), info)
      }
    }
    catch {
      case e: JSONException => log.error(e)
      case e: SQLException => log.error(e)
    }
    finally {
      SqlUtil.SafeClose(statement)
      SqlUtil.SafeClose(db)
    }

    results.toMap
  }

  def getTopTrends() : JSONArray = {
    val results = new JSONArray

    var db: Connection = null
    var statement: PreparedStatement = null
    try {
      db = getDbConnection
      statement = db.prepareStatement("SELECT TAGS,COUNT(TAGS) AS TREND FROM FILE_INDEX GROUP BY TAGS ORDER BY TREND DESC LIMIT 10")

      val rs = statement.executeQuery
      while (rs.next) {
        results.put(rs.getString("TAGS"))
      }
    }
    catch {
      case e: JSONException => log.error(e)
      case e: SQLException => log.error(e)
    }
    finally {
      SqlUtil.SafeClose(statement)
      SqlUtil.SafeClose(db)
    }

    results
  }

  def removeAll(ids: Set[Long]) {
    var db: Connection = null
    var statement: PreparedStatement = null
    try {
      db = getDbConnection
      db.setAutoCommit(false)
      statement = db.prepareStatement("DELETE FROM FILE_INDEX WHERE ID = ?")

      var k = 0
      ids.foreach {
        id =>
          bindVar(statement, 1, id.asInstanceOf[AnyRef])
          statement.addBatch

          k += 1
          if (k > BATCH_SIZE) {
            statement.executeBatch
            k = 0
          }
      }

      statement.executeBatch
      db.commit
    }
    catch {
      case e: JSONException => log.error(e)
      case e: SQLException => log.error(e)
    }
    finally {
      SqlUtil.SafeClose(statement)
      SqlUtil.SafeClose(db)
    }
  }

  def find(filter: JSONObject): JSONArray = {
    val results = new JSONArray

    var db: Connection = null
    var statement: PreparedStatement = null
    try {
      db = getReadOnlyDbConnection

      val bind = new ListBuffer[AnyRef]
      var prefix = ""
      var handledOffset = false

      var sql = if (filter.has("keywords")) {
        bind.append(filter.getString("keywords"))
        prefix = "T."
        handledOffset = true
        val limit = if (filter.has("count")) filter.getInt("count") else 0
        val offset = if (filter.has("offset")) filter.getInt("offset") else 0
        "SELECT T.USERNAME,T.TAGS,T.ID FROM FTL_SEARCH_DATA(?, %d, %d) FTL, FILE_INDEX T WHERE FTL.TABLE='FILE_INDEX' AND T.UID = FTL.UID[0]".format(limit, offset)
      }
      else {
        val select = if (filter.has("distinct")) {
          "SELECT DISTINCT"
        } else {
          "SELECT"
        }
        select + " USERNAME,TAGS,ID FROM FILE_INDEX"
      }

      val list = new ListBuffer[String]

      val iter = filter.keys
      while (iter.hasNext) {
        iter.next.asInstanceOf[String] match {
          case "orderBy" | "count" | "offset" | "distinct" => ;
          case rawKey => {
            val key = prefix + rawKey

            val obj = filter.get(rawKey)
            if (obj.isInstanceOf[Array[String]] || obj.isInstanceOf[Array[Long]]) {
              val foo = List(obj)
              list.append(String.format("%s In (%s)", key.toUpperCase, StringUtil.joinRepeat(foo.size, "?", ",")))
              bind.appendAll(foo)
            }
            else if (obj.isInstanceOf[JSONArray]) {
              val foo = obj.asInstanceOf[JSONArray]
              list.append(String.format("%s In (%s)", key.toUpperCase, StringUtil.joinRepeat(foo.length, "?", ",")))
              bind.appendAll(JsonUtil.createSet(foo))
            }
            else {
              if (obj.toString.contains("%")) {
                list.append(String.format("%s LIKE ?", key))
              }
              else {
                if (key.equals("tags")) {
                  list.append(String.format("%s = ?", key))
                } else {
                  list.append(String.format("%s IN (?)", key))
                }
              }
              bind.append(obj)
            }
          }
        }
      }

      if (list.size > 0) {
        sql += (if (sql.contains("WHERE")) " AND" else " WHERE")
        sql += " %s".format(list.mkString(" AND "))
      }

      if (filter.has("orderBy")) {
        val orderBy = filter.getJSONObject("orderBy")
        sql += " ORDER BY %s".format(prefix + orderBy.getString("name"))
        if (orderBy.has("asc")) sql += " ASC"
        if (orderBy.has("desc")) sql += " DESC"
      }

      if (!handledOffset) {
        if (filter.has("count")) sql += " LIMIT %d".format(filter.getInt("count"))
        if (filter.has("offset")) sql += " OFFSET %d".format(filter.getInt("offset"))
      }

      statement = db.prepareStatement(sql)
      (0 until bind.size).foreach(i => bindVar(statement, i + 1, bind(i)))

      val rs = statement.executeQuery
      while (rs.next) {
        results.put(JsonUtil.createJsonObject(
          "userName", rs.getString("USERNAME"),
          "tags", rs.getString("TAGS"),
          "id", rs.getLong("ID").asInstanceOf[AnyRef]))
      }
    }
    catch {
      case e: JSONException => log.error(e)
      case e: SQLException => log.error(e)
    }
    finally {
      SqlUtil.SafeClose(statement)
      SqlUtil.SafeClose(db)
    }
    results
  }
}