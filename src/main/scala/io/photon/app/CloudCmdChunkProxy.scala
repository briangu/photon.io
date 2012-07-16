package io.photon.app

import io.viper.core.server.file.{HttpChunkProxyEventListener, HttpChunkRelayProxy}
import java.io.File
import org.jboss.netty.handler.codec.http.HttpChunk

class CloudCmdChunkProxy(_rootDir: String) extends HttpChunkRelayProxy {

  var _listener: HttpChunkProxyEventListener = null
  var _filePath: String = null
  var _objectMeta: java.util.Map[String, String] = null
  var _objectName: String = null

  private var _state = false

  def isRelaying = _state

  def init(listener: HttpChunkProxyEventListener, objectName: String, meta: java.util.Map[String, String], objectSize: Long) {
    if (isRelaying) {
      throw new IllegalStateException("init cannot be called before complete or abort")
    }

    _listener = listener
    _objectMeta = meta
    _objectName = objectName

    _state = true

    _listener.onProxyConnected()
    _listener.onProxyWriteReady()
  }

  def writeChunk(chunk: HttpChunk) {
    if (!isRelaying) {
      throw new IllegalStateException("init must be called first")
    }

    try {
      if (chunk.isLast()) {
        //        _fileChannel.write(chunk.getContent().toByteBuffer())
        //        _fileChannel.close()

        _state = false
        _listener.onProxyCompleted()
        reset()
      }
      else {
        //        _fileChannel.write(chunk.getContent().toByteBuffer())
      }
    }
    catch {
      case e: Exception => {
        e.printStackTrace()
        _listener.onProxyError()
        abort()
      }
    }
  }

  def abort() {
    if (!isRelaying) {
      throw new IllegalStateException("init must be called first")
    }
    _listener.onProxyError()
    new File(_filePath).delete()
    reset()
  }

  private def reset() {
    _filePath = null
    _listener = null
  }
}
