package com.github.mgifos.fitstat.controllers

import javax.inject.Inject

import play.api.mvc.{ AbstractController, ControllerComponents }

import scala.concurrent.Future

class Application @Inject() (implicit cc: ControllerComponents) extends AbstractController(cc) {

  def index = Action.async { req =>
    Future.successful(Ok("Fitstat!"))
  }

}
