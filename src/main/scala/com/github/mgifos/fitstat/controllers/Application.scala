package com.github.mgifos.fitstat.controllers

import java.nio.file.Path
import javax.inject.Inject

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{ Flow, Keep, Sink, Source }
import com.github.mgifos.fitstat.tcx.Activity
import com.github.mgifos.fitstat.{ DefaultProcessor, FileEntry, ZipSource }
import org.webjars.play.WebJarsUtil
import play.api.libs.json.{ Format, Json }
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{ AbstractController, ControllerComponents }

import scala.concurrent.{ ExecutionContext, Future }

class Application @Inject() (implicit
  cc: ControllerComponents,
    webJarsUtil: WebJarsUtil,
    mat: Materializer,
    ec: ExecutionContext) extends AbstractController(cc) {

  case class ActivityRef(id: String, name: String, sport: String, start: String)

  implicit val actionRefFormat = Json.using[Json.WithDefaultValues].format[ActivityRef]

  def index = Action { implicit req =>
    Ok(views.html.index(webJarsUtil))
  }

  def importFromZip = Action.async(parse.multipartFormData(50 * 1024 * 1024)) { req =>
    val fileOption: Option[Path] = req.body.file("userfile").map {
      case FilePart(_, _, _, file) => file.toPath
    }
    fileOption match {
      case Some(file) => process(new ZipSource(file).get).map(s => Ok(Json.toJson(s)))
      case None => Future.successful(BadRequest("File not attached!"))
    }
  }

  private def process(source: Source[FileEntry, NotUsed]): Future[Seq[ActivityRef]] = {

    val processor = new DefaultProcessor(outputDir = "target", keepTcx = true)

    val flow = Flow[FileEntry].mapAsync(3)(processor.process)
    val sink = Sink.fold(Seq.empty[ActivityRef]) { (acc: Seq[ActivityRef], a: Activity) =>
      acc :+ ActivityRef(a.id, a.notes.map(_.takeWhile(_ != '\n')).getOrElse("no-name"), a.sport, a.startTime)
    }

    source
      .via(flow)
      .toMat(sink)(Keep.right)
      .run()
  }
}
