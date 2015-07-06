package controllers

import actors.UserActor
import models.User
import play.api.Play
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.mvc._

import scala.concurrent.Future

object TwitStream extends Controller {

  def index = Action { implicit request =>

    request.session.get("userid") match {
      case Some(userid) =>
        play.Logger.debug("User is logged in. userid = " + request.session.get("userid").get)
        Ok(views.html.tweets())
      case None =>
        Ok(views.html.twitterauth())
    }
  }

  def relogin = Action { implicit request =>
    request.session.get("userid") match {
      case Some(userid) =>
        play.Logger.debug("User is logged in. userid = " + request.session.get("userid").get)
        Redirect("/")
      case None =>
        Ok(views.html.twitterauth())
    }
  }

  def untrail() = Action {
    MovedPermanently("/twitstream")
  }

  def getStream = WebSocket.tryAcceptWithActor[JsValue, String] { request =>

    Future.successful(User.getAuthData(request.session.get("userid").getOrElse("")) match {
      case None =>
        Left(Forbidden)
      case Some(authData) =>
        val tokens = authData + (
          "consumerKey" -> Play.application.configuration.getString("consumerKey").get,
          "consumerSecret" -> Play.application.configuration.getString("consumerSecret").get
          )
        play.Logger.debug("[TwitStream.getStream] " + tokens)
        Right( out => UserActor.props( out, tokens ) )
    })

  }
}
