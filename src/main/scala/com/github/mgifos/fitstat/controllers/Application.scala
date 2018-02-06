package com.github.mgifos.fitstat.controllers

import java.nio.file.Path
import java.time.LocalDateTime
import javax.inject.Inject

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import com.github.mgifos.fitstat.tcx.Activity
import com.github.mgifos.fitstat.{ DefaultProcessor, FileEntry, ZipSource }
import org.webjars.play.WebJarsUtil
import play.api.libs.EventSource
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{ AbstractController, ControllerComponents, WebSocket }

import scala.concurrent.{ ExecutionContext, Future }
import scala.concurrent.duration._

class Application @Inject() (implicit
  cc: ControllerComponents,
    webJarsUtil: WebJarsUtil,
    mat: Materializer,
    ec: ExecutionContext) extends AbstractController(cc) {

  println("Controller created @ " + LocalDateTime.now)

  case class ActivityRef(id: String, name: String, sport: String, start: String)

  implicit val actionRefFormat = Json.using[Json.WithDefaultValues].format[ActivityRef]

  def index = Action { implicit req =>
    Ok(views.html.index(webJarsUtil))
  }

  def importFromZip = Action(parse.multipartFormData(50 * 1024 * 1024)) { req =>
    val fileOption: Option[Path] = req.body.file("userfile").map {
      case FilePart(_, _, _, file) => file.toPath
    }
    fileOption match {
      case Some(file) => Ok.chunked(serverEventsStream(new ZipSource(file).get).map(s => Ok(Json.toJson(s))))
      case None => BadRequest("File not attached!")
    }
  }

  private def serverEventsStream(source: Source[FileEntry, NotUsed]): Source[EventSource.Event, NotUsed] = {

    val processor = new DefaultProcessor(outputDir = "target", keepTcx = true)

    source
      .mapAsync(3)(processor.process)
      .map { a =>
        val ref = ActivityRef(a.id, a.notes.map(_.takeWhile(_ != '\n')).getOrElse("no-name"), a.sport, a.startTime)
        Json.toJson(ref)
      }
      .via(EventSource.flow)
  }
}
