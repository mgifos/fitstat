package com.github.mgifos.fitstat

import java.io.{ BufferedOutputStream, ByteArrayOutputStream, FileInputStream }
import java.nio.file.{ Files, Path, Paths }
import java.util.zip.{ ZipEntry, ZipInputStream }

import akka.NotUsed
import akka.stream.alpakka.file.scaladsl.Directory
import akka.stream.scaladsl.Source
import com.typesafe.scalalogging.Logger

/**
 * Raw file content to be processed
 *
 * @param fileName
 * @param content
 */
case class FileEntry(fileName: String, content: String)

/**
 * Activities Source fetcher/provider
 */
trait ActivitiesSource {

  def get: Source[FileEntry, NotUsed]
}

/**
 * Default directory source provider
 *
 * @param directory
 */
class DirectorySource(directory: String) extends ActivitiesSource {

  val log = Logger(getClass)

  override def get: Source[FileEntry, NotUsed] = {
    val dir = Paths.get(directory)
    log.info(s"Listing tcx files from directory: $dir ...")
    Directory.ls(dir).filter(_.toString.endsWith(".tcx")).map { path =>
      log.debug(s"$path")
      try {
        FileEntry(path.getFileName.toString, new String(Files.readAllBytes(path.toAbsolutePath)))
      } catch {
        case ex: Exception =>
          ex.printStackTrace
          throw ex
      }
    }
  }
}

/**
 *
 * @param path ZIP file path
 */
class ZipSource(path: Path) extends ActivitiesSource {

  override def get: Source[FileEntry, NotUsed] = {

    val zis = new ZipInputStream(new FileInputStream(path.toFile))

    def nextBytes: Array[Byte] = {
      val bos = new ByteArrayOutputStream()
      val os = new BufferedOutputStream(bos)
      try {
        val buf = new Array[Byte](1024)
        var len = zis.read(buf, 0, 1)
        while (len >= 0) {
          os.write(buf, 0, len)
          len = zis.read(buf)
        }
      } finally {
        os.close()
      }
      bos.toByteArray
    }

    Source.fromIterator(() => new Iterator[FileEntry] {
      var ze: ZipEntry = zis.getNextEntry
      override def hasNext: Boolean = ze != null
      override def next(): FileEntry = {
        val filename = ze.getName()
        val content = new String(nextBytes)
        println(s"stream: $filename / ${content.take(100).replace('\n', '-')}")
        ze = zis.getNextEntry
        FileEntry(filename, content)
      }
    })
  }
}

