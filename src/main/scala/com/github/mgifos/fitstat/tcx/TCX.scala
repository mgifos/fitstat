package com.github.mgifos.fitstat.tcx

import java.time.ZonedDateTime

import scala.xml.Node

/**
 * Garmin Training Center file format / model
 */
object TCX {

  def activity(a: Node): Activity = Activity(
    id = (a \ "Id").text,
    sport = a \@ "Sport",
    lap = (a \ "Lap").map(lap),
    notes = (a \ "Notes").headOption.map(_.text),
    creator = (a \ "Creator").headOption.map(creator)
  )

  def lap(l: Node): Lap = Lap(
    startTime = ZonedDateTime.parse(l \@ "StartTime"),
    totalTimeSeconds = (l \ "TotalTimeSeconds").text.toDouble,
    distanceMeters = (l \ "DistanceMeters").text.toDouble,
    maximumSpeed = (l \ "DistanceMeters").headOption.map(_.text.trim.toDouble),
    calories = (l \ "Calories").text.toInt,
    averageHeartRateBpm = (l \ "AverageHeartRateBpm").headOption.map(_.text.trim.toInt),
    maximumHeartRateBpm = (l \ "MaximumHeartRateBpm").headOption.map(_.text.trim.toInt),
    intensity = (l \ "Intensity").text,
    cadence = (l \ "Cadence").headOption.map(_.text.trim.toInt),
    triggerMethod = (l \ "TriggerMethod").text,
    track = (l \ "Track" \ "Trackpoint").map(trackpoint),
    notes = (l \ "Notes").headOption.map(_.text.trim)
  //extensions: Seq[Any] = Seq.empty[Any]
  )

  def creator(c: Node): Creator = Creator(
    name = (c \ "Name").text.trim,
    device = device(c)
  )

  def device(creator: Node): Device = Device(
    unitId = (creator \ "UnitId").text.trim.toLong,
    productId = (creator \ "ProductID").text.trim.toInt,
    version = version((creator \ "Version").head)
  )

  def version(v: Node): Version = Version(
    versionMajor = (v \ "VersionMajor").text.trim.toInt,
    versionMinor = (v \ "VersionMinor").text.trim.toInt,
    buildMajor = (v \ "BuildMajor").headOption.map(_.text.trim.toInt),
    buildMinor = (v \ "BuildMinor").headOption.map(_.text.trim.toInt)
  )

  def trackpoint(t: Node): Trackpoint = Trackpoint(
    time = ZonedDateTime.parse((t \ "Time").head.text.trim),
    position = (t \ "Position").headOption.map(position),
    altitudeMeters = (t \ "AltitudeMeters").headOption.map(_.text.trim.toDouble),
    distanceMeters = (t \ "DistanceMeters").headOption.map(_.text.trim.toDouble),
    heartRateBpm = (t \ "HeartRateBpm").headOption.map(_.text.trim.toInt),
    cadence = (t \ "Cadence").headOption.map(_.text.trim.toInt),
    sensorState = (t \ "SensorState").headOption.map(_.text.trim)
  //extensions: Seq[String] = Seq.empty[String])
  )

  def position(p: Node): Position = Position(
    latitudeDegrees = (p \ "LatitudeDegrees").head.text.trim.toDouble,
    longitudeDegrees = (p \ "LongitudeDegrees").head.text.trim.toDouble
  )
}

case class Activity(
    id: String,
    sport: String,
    lap: Seq[Lap],
    notes: Option[String] = None,
    creator: Option[Creator] = None
) {
  def startTime: String = lap.headOption.map(_.startTime.toString).getOrElse(id)
}

case class Lap(
  startTime: ZonedDateTime,
  totalTimeSeconds: Double,
  distanceMeters: Double,
  maximumSpeed: Option[Double] = None,
  calories: Int,
  averageHeartRateBpm: Option[Int] = None,
  maximumHeartRateBpm: Option[Int] = None,
  intensity: String,
  cadence: Option[Int] = None,
  triggerMethod: String,
  track: Seq[Trackpoint],
  notes: Option[String] = None,
  extensions: Seq[String] = Seq.empty[String]
)

case class Creator(name: String, device: Device)

case class Device(unitId: Long, productId: Int, version: Version)

case class Version(versionMajor: Int, versionMinor: Int, buildMajor: Option[Int], buildMinor: Option[Int])

case class Trackpoint(
  time: ZonedDateTime,
  position: Option[Position] = None,
  altitudeMeters: Option[Double] = None,
  distanceMeters: Option[Double] = None,
  heartRateBpm: Option[Int] = None,
  cadence: Option[Int] = None,
  sensorState: Option[String] = None
)

case class Position(latitudeDegrees: Double, longitudeDegrees: Double)
