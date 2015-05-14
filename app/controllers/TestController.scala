package controllers

import play.api.mvc._
import play.cache.Cache

object TestController extends Controller {
  def cacheset = Action { implicit request =>
    Cache.set("some.key", request.getQueryString("v").getOrElse("defaultvalue"))

    Ok("Cache set")
  }

  def cacheget = Action { implicit request =>
    val value = Cache.get("some.key")

    Ok("from cache: " + value)
  }

  def requestProps = Action { implicit  request =>
    Ok (
      "host: " + request.host + "\n" +
      "uri: " + request.uri + "\n" +
      "path: " + request.path + "\n" +
      "remoteAddress: " + request.remoteAddress + "\n" +
      "domain: " + request.domain + "\n" +
      "method: " + request.method + "\n" +
      "secure: " + request.secure + "\n" +
      "queryString: " + request.queryString + "\n" +
      "version: " + request.version + "\n" +
      "rawQueryString (v): " + request.rawQueryString + "\n"
    )
  }

  def streamUI = Action { implicit request =>
    Ok (views.html.sampletweets())
  }

}
