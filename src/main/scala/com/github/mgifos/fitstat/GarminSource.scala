package com.github.mgifos.fitstat

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{ Cookie, `Set-Cookie` }
import akka.stream.scaladsl.{ Flow, Source }
import akka.stream.{ ActorMaterializer, ThrottleMode }
import com.typesafe.scalalogging.Logger
import play.api.libs.json.{ JsObject, Json }

import scala.collection.immutable.{ Map, Seq }
import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Random, Try }
import scala.xml.{ Elem, Node, XML }
import scala.xml.transform.{ RewriteRule, RuleTransformer }

class GarminSource(email: String, password: String)(implicit system: ActorSystem, executionContext: ExecutionContext, mat: ActorMaterializer) extends ActivitiesSource {

  case class Session(username: String, headers: Seq[HttpHeader])

  case class ActivitiesPage(no: Int, activities: Seq[GarminActivity])

  case class GarminActivity(id: Long, name: String, date: String, desc: String) {
    def tcxFileName = s"${date}_${id}_${name}.tcx".replaceAll("[^a-zA-Z0-9\\.\\-]", "_")
  }

  private val log = Logger(getClass)

  override def get: Source[FileEntry, NotUsed] =
    Source.fromFuture(login).flatMapConcat { session =>
      pageSource(session).
        throttle(1, 2.seconds, 1, ThrottleMode.shaping).
        flatMapConcat(page => Source(page.activities)).
        via(downloadTcxActivityFlow(session))
    }

  private def login: Future[Session] = {

    def extractCookies(res: HttpResponse) = res.headers.collect { case x: `Set-Cookie` => x.cookie }.map(c => Cookie(c.name, c.value))

    def redirectionLoop(count: Int, url: String, acc: Seq[Cookie]): Future[Seq[Cookie]] = {
      Http().singleRequest {
        Thread.sleep(500 + Random.nextInt(500))
        HttpRequest(uri = Uri(url)).withHeaders(acc)
      }.flatMap { response =>
        val cookies = extractCookies(response)
        response.headers.find(_.name() == "Location") match {
          case Some(header) =>
            if (count < 7) {
              val path = header.value()
              val base = path.split("/").take(3).mkString("/")
              val nextUrl = if (path.startsWith("/")) base + path else path
              redirectionLoop(count + 1, nextUrl, acc ++ cookies)
            } else {
              Future.successful(acc ++ cookies)
            }
          case None => Future.successful(acc ++ cookies)
        }
      }
    }

    val params = Map(
      "service" -> "https://connect.garmin.com/post-auth/login",
      "clientId" -> "GarminConnect",
      "gauthHost" -> "https://sso.garmin.com/sso",
      "consumeServiceTicket" -> "false"
    )
    for {
      res1 <- Http().singleRequest(HttpRequest(uri = Uri("https://sso.garmin.com/sso/login").withQuery(Query(params))))
      res2 <- Http().singleRequest(
        HttpRequest(
          POST,
          Uri("https://sso.garmin.com/sso/login").withQuery(Query(params)),
          entity = FormData(Map(
            "username" -> email,
            "password" -> password,
            "_eventId" -> "submit",
            "embed" -> "true"
          )).toEntity
        ).withHeaders(extractCookies(res1))
      )
      sessionCookies <- redirectionLoop(0, "https://connect.garmin.com/post-auth/login", extractCookies(res2))
      username <- getUsername(sessionCookies)
    } yield Session(username, sessionCookies)
  }

  private def getUsername(sessionCookies: Seq[HttpHeader]): Future[String] = {
    val req = HttpRequest(GET, Uri("https://connect.garmin.com/user/username")).withHeaders(sessionCookies)
    Http().singleRequest(req).flatMap { res =>
      res.entity.toStrict(2.seconds).map(_.data.utf8String).map { json =>
        (Json.parse(json) \ "username").as[String]
      }
    }
  }

  private def pageSource(session: Session): Source[ActivitiesPage, NotUsed] = {
    val pageSize = 20

    Source.unfoldAsync(ActivitiesPage(0, Seq.empty[GarminActivity])) { page =>
      getActivitiesPage(page.no, pageSize, session).map {
        case Nil => None
        case activities =>
          log.info(s"> Page ${page.no} loaded")
          Some((ActivitiesPage(page.no + 1, activities), page))
      }
    }
  }

  private def getActivitiesPage(page: Int, pageSize: Int, session: Session): Future[Seq[GarminActivity]] = {
    val start = page * pageSize
    val url = s"https://connect.garmin.com/proxy/activitylist-service/activities/${session.username}?limit=$pageSize&start=$start"
    log.debug(s"Fetching page ... $url")
    Http().singleRequest(HttpRequest(GET, Uri(url)).withHeaders(session.headers)).flatMap { res =>
      if (res.status == OK) {
        res.entity.toStrict(10.seconds).map { entity =>
          Try {
            val activities = (Json.parse(entity.data.utf8String) \ "activityList").as[Seq[JsObject]]
            activities.map(a => GarminActivity(
              (a \ "activityId").as[Long],
              (a \ "activityName").asOpt[String].getOrElse("no-name"),
              (a \ "startTimeLocal").asOpt[String].map(_.take(10)).getOrElse("no-date"),
              (a \ "description").asOpt[String].getOrElse("")
            ))
          }.recoverWith {
            case e: Exception =>
              log.error(s"Error in parsing: $e")
              Failure(e)
          }.get
        }.recover {
          case e: Exception =>
            log.error("Page HTTP entity cannot be fetched. " + e.getMessage)
            throw e
        }
      } else {
        val errorMsg = s"Cannot retrieve activites page $page of $pageSize activities"
        log.error(errorMsg)
        throw new Exception(errorMsg)
      }
    }
  }

  private def downloadTcxActivityFlow(session: Session) = Flow[GarminActivity].mapAsync(1) { ga =>
    for {
      res <- Http().singleRequest(
        HttpRequest(
          GET,
          Uri(s"https://connect.garmin.com/modern/proxy/download-service/export/tcx/activity/${ga.id}")
        ).withHeaders(session.headers)
      )
      tcxContent <- res.entity.toStrict(10.seconds).map(_.data.utf8String)
    } yield {
      class FixActivityNotes(name: String, desc: String) extends RewriteRule {
        override def transform(n: Node): Seq[Node] = n match {
          case e: Elem if e.label == "Activity" =>
            val children = e.child
            val notes = children.find(_.label == "Notes")
            val newNotes = notes.map(old => <Notes>{ s"$name\n${old.text}" }</Notes>).getOrElse(<Notes>{ s"$name\n$desc" }</Notes>)
            new Elem(e.prefix, e.label, e.attributes, e.scope, e.minimizeEmpty, children.filter(_.label != "Notes") ++ newNotes: _*)
          case x => x
        }
      }
      val rule = new RuleTransformer(new FixActivityNotes(ga.name, ga.desc))
      val originalTcx = XML.loadString(tcxContent)
      FileEntry(ga.tcxFileName, rule.transform(originalTcx).head.toString)
    }
  }

}
