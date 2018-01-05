package com.github.mgifos.tcx

import java.time.{ LocalTime, ZonedDateTime }

import play.api.libs.json._
import play.api.libs.functional.syntax._

object JsonWritesImplicits {

  implicit val versionWrites: Writes[Version] = (
    (JsPath \ "versionMajor").write[Int] and
    (JsPath \ "versionMinor").write[Int] and
    (JsPath \ "buildMajor").writeNullable[Int] and
    (JsPath \ "buildMinor").writeNullable[Int]
  )(unlift(Version.unapply))

  implicit val deviceWrites: Writes[Device] = (
    (JsPath \ "unitId").write[Long] and
    (JsPath \ "productId").write[Int] and
    (JsPath \ "version").write[Version]
  )(unlift(Device.unapply))

  implicit val creatorWrites: Writes[Creator] = (
    (JsPath \ "name").write[String] and
    (JsPath \ "device").write[Device]
  )(unlift(Creator.unapply))

  implicit val positionWrites: Writes[Position] = (
    (JsPath \ "latitudeDegrees").write[Double] and
    (JsPath \ "longitudeDegrees").write[Double]
  )(unlift(Position.unapply))

  implicit val trackpointWrites: Writes[Trackpoint] = (
    (JsPath \ "time").write[ZonedDateTime] and
    (JsPath \ "position").writeNullable[Position] and
    (JsPath \ "altitudeMeters").writeNullable[Double] and
    (JsPath \ "distanceMeters").writeNullable[Double] and
    (JsPath \ "heartRateBpm").writeNullable[Int] and
    (JsPath \ "cadence").writeNullable[Int] and
    (JsPath \ "sensorState").writeNullable[String]
  )(unlift(Trackpoint.unapply))

  implicit val lapWrites: Writes[Lap] = (
    (JsPath \ "startTime").write[ZonedDateTime] and
    (JsPath \ "totalTimeSeconds").write[Double] and
    (JsPath \ "distanceMeters").write[Double] and
    (JsPath \ "maximumSpeed").writeNullable[Double] and
    (JsPath \ "calories").write[Int] and
    (JsPath \ "averageHeartRateBpm").writeNullable[Int] and
    (JsPath \ "maximumHeartRateBpm").writeNullable[Int] and
    (JsPath \ "intensity").write[String] and
    (JsPath \ "cadence").writeNullable[Int] and
    (JsPath \ "triggerMethod").write[String] and
    (JsPath \ "track").write[Seq[Trackpoint]] and
    (JsPath \ "notes").writeNullable[String] and
    (JsPath \ "extensions").write[Seq[String]]
  )(unlift(Lap.unapply))

  implicit val activityWrites: Writes[Activity] = (
    (JsPath \ "id").write[String] and
    (JsPath \ "lap").write[Seq[Lap]] and
    (JsPath \ "notes").writeNullable[String] and
    (JsPath \ "creator").writeNullable[Creator]
  )(unlift(Activity.unapply))
}
