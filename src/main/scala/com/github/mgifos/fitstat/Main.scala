package com.github.mgifos.fitstat

import akka.actor.ActorSystem
import akka.stream.scaladsl.{ Flow, GraphDSL, RunnableGraph, Sink }
import akka.stream.{ ActorMaterializer, ClosedShape }
import com.github.mgifos.tcx.Activity

import scala.concurrent.Future
import scala.util.{ Failure, Success }

object Main extends App {

  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val processor = new DefaultProcessor(outputDir = "target", keepTcx = true) // new PrintFileNameProcessor()

  val activitiesFileFetcher: ActivityFilesFetcher = new DirectoryFetcher("tcx-files") //new GarminFetcher(args(0), args(1))

  def shutdown(totalFuture: Future[Int]) = totalFuture.onComplete {
    case Success(total) =>
      println(s"\nTotal activities processed: $total")
      system.terminate()
    case Failure(ex) =>
      println(s"\nFailed because of: ${ex.getCause}")
      system.terminate()
  }

  val graph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val source = activitiesFileFetcher.fetch
    val flow = Flow[FileEntry].mapAsync(3)(processor.process)
    val sink = Sink.fold(0) { (acc, _: Activity) => acc + 1 }.mapMaterializedValue(shutdown)

    source ~> flow ~> sink

    ClosedShape
  }

  RunnableGraph.fromGraph(graph).run()
}
