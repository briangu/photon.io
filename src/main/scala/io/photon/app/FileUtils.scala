package io.photon.app

import java.net.{JarURLConnection, URL}
import java.util.jar.JarFile
import io.viper.core.server.Util
import java.io.{FileInputStream, File, InputStream}
import java.nio.channels.FileChannel
import java.nio.charset.Charset

object FileUtils {
  def readFile(path: String): String = {
    val stream = new FileInputStream(new File(path));
    try {
      val fc = stream.getChannel();
      val bb = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
      return Charset.forName("UTF-8").decode(bb).toString();
    }
    finally {
      stream.close();
    }
  }

  def readResourceFile(clazz: Class[_], path: String): String = {
    val url: URL = clazz.getResource(path)

    if (url == null) {
      return null
    }

    if (url.toString.startsWith("jar:")) {
      var bytes: Array[Byte] = null
      clazz.synchronized {
        val conn: JarURLConnection = url.openConnection.asInstanceOf[JarURLConnection]
        val jarFile: JarFile = conn.getJarFile
        val input: InputStream = jarFile.getInputStream(conn.getJarEntry)
        bytes = Util.copyStream(input)
        jarFile.close
      }
      new String(bytes, "UTF-8")
    } else {
      readFile(url.getFile)
    }
  }
}