package com.github.mgifos.fitstat.controllers

import javax.inject.Inject

import org.webjars.play.WebJarsUtil
import play.api.mvc.{ AbstractController, ControllerComponents }

class Application @Inject() (implicit cc: ControllerComponents, webJarsUtil: WebJarsUtil) extends AbstractController(cc) {

  def index = Action { implicit req =>
    Ok(views.html.index(webJarsUtil))
  }

}
