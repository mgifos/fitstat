package com.github.mgifos.tcx

import java.time.temporal.ChronoUnit
import java.time.{ LocalTime, ZonedDateTime }

import play.api.libs.json.{ JsObject, Json }

import scala.math.{ max, min }
import scala.xml.{ Node, XML }

object Main {

  def main(args: Array[String]): Unit = {
    val tcx = XML.load(ClassLoader.getSystemResource("activity_2292844227.tcx"))

    val activities = tcx \ "Activities" \ "Activity"
    val transformed = activities.map(calc)
    println(Json.prettyPrint(transformed.head))

    println(summary(activities.head))
  }

  private def calc(activity: Node): JsObject = {
    val secs = totalSec2(activity)
    Json.obj(
      "id" -> (activity \ "Id").text,
      "sport" -> activity \@ "Sport",
      "totalSec" -> secs,
      "totalTime" -> LocalTime.ofSecondOfDay(secs),
      "avgHR" -> avgHR(activity)
    )
  }

  private def nodeToLap(n: Node): JsObject = Json.obj(
    "lap" -> Json.obj(
      "start" -> ZonedDateTime.parse(n \@ "StartTime"),
      "distance" -> (n \ "DistanceMeters").text.toFloat,
      "avgHR" -> (n \ "AverageHeartRateBpm").text.toInt
    )
  )

  private def totalSec(activity: Node): Int =
    (activity \ "Lap").foldLeft(0F)((sum: Float, lap: Node) => {
      sum + (lap \ "TotalTimeSeconds").text.toFloat
    }).toInt

  private def totalSec2(activity: Node): Int = {
    val tps = activity \\ "Trackpoint"
    tps.headOption.map { tp1 =>
      val start = ZonedDateTime.parse((tp1 \ "Time").text)
      val stop = ZonedDateTime.parse((tps.last \ "Time").text)
      start.until(stop, ChronoUnit.SECONDS).toInt
    }.getOrElse(0)
  }

  private def avgHR(activity: Node): Int =
    ((activity \ "Lap").foldLeft(0F)((sum: Float, lap: Node) => {
      sum + (lap \ "TotalTimeSeconds").text.toFloat * (lap \ "AverageHeartRateBpm").text.toFloat
    }) / totalSec(activity)).toInt

  private def summary(activity: Node): Activity = {
    val tps = (activity \\ "Trackpoint").map ( tp =>
      Trackpoint(
        time = ZonedDateTime.parse((tp \ "Time").text).toLocalTime,
        distance = (tp \ "DistanceMeters").text.toFloat,
        alt = (tp \ "AltitudeMeters").text.toFloat,
        cadence = (tp \\ "RunCadence").headOption.map(_.text.toInt).getOrElse(0)
      )
    )
    val z = Activity(0, Elevation(0F, 0F), 0)
    var (max, min) = (0F, 100000F)
    val res = tps.sliding(2).foldLeft(z)((acc, tp) => {
      val distance = tp(1).distance - tp.head.distance
      val (altPrev, alt) = (tp.head.alt, tp(1).alt)
      max = scala.math.max(alt, max)
      min = scala.math.min(alt, min)
      acc + Activity(
        tp.head.time.until(tp.last.time, ChronoUnit.SECONDS).toInt,
        elevation = Elevation(
          gain = if (alt > altPrev) alt - altPrev else 0,
          loss = if (alt < altPrev) altPrev - alt else 0
        ),
        avgCadence = 0
      )
    })
    println(s"MAX: $max; MIN: $min")
    res
  }

  case class Trackpoint(time: LocalTime, distance: Float, alt: Float, cadence: Int)

  case class Activity(totalSec: Int, elevation: Elevation, avgCadence: Int) {

    def +(add: Activity): Activity = {
      Activity(
        totalSec = this.totalSec + add.totalSec,
        elevation = Elevation(
          gain = elevation.gain + add.elevation.gain,
          loss = elevation.loss + add.elevation.loss
        ),
        avgCadence = 0
      )

    }
  }

  case class Elevation(gain: Float, loss: Float)
}
