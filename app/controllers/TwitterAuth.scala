package controllers

import models.User._
import play.api.Play
import play.api.Play.current
import play.api.cache.Cache
import play.api.mvc._
import twitter4j.auth.RequestToken
import twitter4j.conf.{Configuration, ConfigurationBuilder}
import twitter4j.{Twitter, TwitterException, TwitterFactory}

import scala.concurrent.duration._
/**
 * Twitter OAuth.
 *
 * Created by admin on 01/04/15.
 */

object TwitterAuth extends Controller {
  final val CACHE_KEY_TWITTER = "twitter.singleton."
  final val CACHE_KEY_REQUEST_TOKEN = "twitter.requestToken."
  final val SESSION_KEY = "authuuid"

  /**
   * Implementation of Sign In with Twitter
   * Redirects user to Twitter Authorization URL
   *
   */
  def authenticate = Action { implicit request =>
    play.Logger.debug(" --- [authenticate] ---")
    val uuid = java.util.UUID.randomUUID().toString
    val callback = "http://"  + request.host + "/authenticated?" + SESSION_KEY + "=" + uuid

    val twitter = createTwitterInstance()
    play.Logger.debug("[authenticate] Got Twitter")
    play.Logger.debug("\tconsumerKey: " + twitter.getConfiguration.getOAuthConsumerKey)
    play.Logger.debug("\tconsumerSecret: " + twitter.getConfiguration.getOAuthConsumerSecret)

    val requestToken = twitter.getOAuthRequestToken(callback)
    play.Logger.debug("[authenticate] OAuthRequestToken")
    play.Logger.debug("\trequestToken: " + requestToken.getToken)
    play.Logger.debug("\trequestTokenSecret: " + requestToken.getTokenSecret)

    // For /authorize API. Not used for Sign in with Twitter, hence commented
//    val authUrl = requestToken.getAuthorizationURL()
//    play.Logger.debug("[authenticate] Authorization URL")
//    play.Logger.debug("\t" + authUrl)

    // twitter instance and requestToken cached to be used in callback, i.e. authenticated method
    Cache.set(CACHE_KEY_TWITTER + uuid, twitter, 3 minutes)
    Cache.set(CACHE_KEY_REQUEST_TOKEN + uuid, requestToken, 3 minutes)

    val authUrl = requestToken.getAuthenticationURL
    play.Logger.debug("[authenticate] Authentication URL")
    play.Logger.debug("\t" + authUrl)

    Redirect(authUrl)
  }

  /**
   * Callback after authorization. Gets the Access Token.
   */
  def authenticated = Action { implicit request =>
    play.Logger.debug(" --- [authenticated] ---")

    val tokens = for {
      oauth_token <- request.getQueryString("oauth_token")
      oauth_verifier <- request.getQueryString("oauth_verifier")
    } yield (oauth_token, oauth_verifier)

    tokens match {
      case None =>
        Ok("Authentication failed. Couldn't find oauth_token and/or oauth_verifier")
      case Some( (oauth_token, oauth_verifier) ) =>

        play.Logger.debug("[authenticated] Callback params")
        play.Logger.debug("\toauth_token: " + oauth_token)
        play.Logger.debug("\toauth_verifier: " + oauth_verifier)

        val cachedObjs = for {
          uuid <- request.getQueryString(SESSION_KEY) // session.get(SESSION_KEY)
          twitter <- Cache.get(CACHE_KEY_TWITTER + uuid).asInstanceOf[Option[Twitter]]
          requestToken <- Cache.get(CACHE_KEY_REQUEST_TOKEN + uuid).asInstanceOf[Option[RequestToken]]
        } yield (uuid, twitter, requestToken)

        cachedObjs match {
          case None =>
            play.Logger.error("Couldn't get twitter and/or requestToken from cache")
            InternalServerError("Couldn't get twitter and/or requestToken from cache")

          case Some( (uuid, twitter, requestToken) ) =>

            play.Logger.debug("[authenticated] Got Twitter and RequestToken from Cache")
            request.session - ("twitter.requestToken." + request.getQueryString(SESSION_KEY).get)
            request.session - ("twitter.singleton" + request.getQueryString(SESSION_KEY).get)

            play.Logger.debug("\tconsumerKey: " + twitter.getConfiguration.getOAuthConsumerKey)
            play.Logger.debug("\tconsumerSecret: " + twitter.getConfiguration.getOAuthConsumerSecret)
            play.Logger.debug("\toauth_token (cached): " + requestToken.getToken)

            if (oauth_token.equals(requestToken.getToken)) {
              try {
                val accessToken = twitter.getOAuthAccessToken(requestToken, oauth_verifier) // Using RequestToken object here
                play.Logger.debug("[authenticated] AccessToken")
                play.Logger.debug("\taccess_token: " + accessToken.getToken)
                play.Logger.debug("\ttoken_secret: " + accessToken.getTokenSecret)

                saveUser(accessToken.getUserId, accessToken.getScreenName, accessToken.getToken, accessToken.getTokenSecret)
                play.Logger.debug("[authenticated] User saved, redirecting to /twitstream")
                Redirect(routes.TwitStream.index()).withSession(
                  request.session
                    + ("userid" -> accessToken.getUserId.toString)
                    + ("tw_user" -> accessToken.getScreenName)
                )
              } catch {
                case ex: Exception =>
                  throw ex
                  Ok(ex.getMessage)
              }
            } else {
              play.Logger.error("OAuth token mismatch")
              Ok("OAuth token mismatch")
            }
        }
    }

  }

  /**
   * Creates Twitter object used for authorization
   * @return Twitter Twitter object
   */
  private def createTwitterInstance(): Twitter = {
    val appTokens = Map(
      "consumerKey" -> Play.application.configuration.getString("consumerKey").get,
      "consumerSecret" -> Play.application.configuration.getString("consumerSecret").get
    )
    val builder: ConfigurationBuilder = new ConfigurationBuilder()
    builder.setOAuthConsumerKey(appTokens("consumerKey"))
    builder.setOAuthConsumerSecret(appTokens("consumerSecret"))
    val configuration: Configuration = builder.build()
    val factory: TwitterFactory = new TwitterFactory(configuration)

    factory.getInstance
  }
}
