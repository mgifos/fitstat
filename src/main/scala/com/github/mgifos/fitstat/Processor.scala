package com.github.mgifos.fitstat

import java.io.PrintWriter
import java.nio.file.Paths

import com.github.mgifos.fitstat.tcx.JsonWritesImplicits._
import com.github.mgifos.fitstat.tcx.{ Activity, TCX }
import com.typesafe.scalalogging.Logger
import play.api.libs.json.Json

import scala.concurrent.{ ExecutionContext, Future }
import scala.xml.XML

/**
 * Activity file entry processor
 */
trait Processor {

  /**
   * @param input Raw TCX file entry to be processed
   * @return TCX activity future
   */
  def process(input: FileEntry): Future[Activity]
}

class PrintFileNameProcessor extends Processor {
  private val log = Logger(getClass)
  override def process(input: FileEntry) = {
    log.info(input.fileName)
    Future.successful(Activity(input.fileName, Seq.empty))
  }
}

/**
 * Converts XML to JSON and saves it in local folder, optionally saves original TCX file.
 * @param keepTcx
 * @param ec
 */
class DefaultProcessor(outputDir: String, keepTcx: Boolean = true)(implicit ec: ExecutionContext) extends Processor {

  private val log = Logger(getClass)

  override def process(tcx: FileEntry): Future[Activity] = {

    log.info(s"${tcx.fileName} ...")

    val saveTcxFuture =
      if (keepTcx)
        printToFile(Paths.get(outputDir, tcx.fileName).toString, tcx.content)
      else
        Future.successful(Unit)

    for {
      activity <- Future {
        val activityNode = (XML.loadString(tcx.content) \ "Activities" \ "Activity").head
        TCX.activity(activityNode)
      }
      _ <- Future {
        val jsonFileName = Paths.get("target", tcx.fileName.replace(".tcx", ".json")).toString
        printToFile(jsonFileName, Json.prettyPrint(Json.toJson(activity)))
      }
      _ <- saveTcxFuture
    } yield activity
  }

  private def printToFile(filename: String, content: => String): Future[Unit] = Future {
    new PrintWriter(filename) {
      write(content)
      close
    }
  }

}
