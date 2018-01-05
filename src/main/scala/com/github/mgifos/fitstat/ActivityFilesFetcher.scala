package com.github.mgifos.fitstat

import java.nio.file.{ Files, Paths }

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
trait ActivityFilesFetcher {

  def fetch: Source[FileEntry, NotUsed]
}

/**
 * Default directory source provider
 *
 * @param directory
 */
class DirectoryFetcher(directory: String) extends ActivityFilesFetcher {

  val log = Logger(getClass)

  override def fetch: Source[FileEntry, NotUsed] = {
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
