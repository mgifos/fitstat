package com.github.mgifos.fitstat.controllers

import java.nio.file.{ Path, Paths }
import java.time.LocalDateTime
import javax.inject.Inject

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl.Source
import com.github.mgifos.fitstat.{ DefaultProcessor, FileEntry, GarminSource, ZipSource }
import org.webjars.play.WebJarsUtil
import play.api.libs.EventSource
import play.api.libs.json._
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{ AbstractController, ControllerComponents }

import scala.concurrent.ExecutionContext

case class ActivityRef(id: String, name: String, sport: String, start: String)

object ActivityRef {
  implicit val actionRefFormat = Json.format[ActivityRef]
}

class Application @Inject() (implicit
  cc: ControllerComponents,
    webJarsUtil: WebJarsUtil,
    mat: Materializer,
    system: ActorSystem,
    ec: ExecutionContext) extends AbstractController(cc) {

  println("Controller created @ " + LocalDateTime.now)

  def index = Action { implicit req =>
    Ok(views.html.index(webJarsUtil))
  }

  def importFromZip = Action(parse.multipartFormData(50 * 1024 * 1024)) { req =>
    val fileOption: Option[Path] = req.body.file("userfile").map {
      case FilePart(_, _, _, file) => file.toPath
    }
    fileOption match {
      case Some(file) => Ok.flashing("zip" -> file.toString)
      case None => BadRequest("File not attached!")
    }
  }

  def zipEventsStream = Action { req =>
    req.flash.get("zip") match {
      case Some(zip) => Ok.chunked(serverEventsStream(new ZipSource(Paths.get(zip)).get))
      case None => BadRequest("Zip path is missing!")
    }
  }

  def syncWithGarmin(email: String, pwd: String) = Action {
    //TODO: pwd security issue
    Ok.chunked(serverEventsStream(new GarminSource(email, pwd).get))
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
