package io.photon.app

import cloudcmd.common.engine.IndexStorage
import org.json.{JSONObject, JSONArray}
import cloudcmd.common.FileMetaData

class SimpleIndexStorage(wrappedStorage: IndexStorage) extends IndexStorage {

  def init(configRoot: String) {}

  def purge {}

  def flush {}

  def shutdown {}

  def reindex() {}

  def find(filter: JSONObject) = null

  def add(fmd: FileMetaData) {}

  def addAll(fmds: List[FileMetaData]) {}

  def remove(fmd: FileMetaData) {}

  def pruneHistory(fmds: List[FileMetaData]) {}

  def get(fmds: JSONArray) {}

  def ensure(fmds: JSONArray, blockLevelCheck: Boolean) {}

  def remove(fmds: JSONArray) {}

  def addTags(fmds: JSONArray, tags: Set[String]) = null

}
