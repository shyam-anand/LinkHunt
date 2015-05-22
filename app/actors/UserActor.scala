package actors

import akka.actor.Status._
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.json._
import twitter4j.URLEntity

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
case class Tweet(user: twitter4j.User, text: String, urls: Array[Map[String, String]], createdAt: String, source: String)
case class TwitterError(message: String, code: Int)

object Tweet {

  def makeTweetText(text: String, urls: Array[Map[String, String]]): String = {
    var tweetText = text

    play.Logger.debug("urls.length: " + urls.length)

    urls foreach { url =>
      val expandedUrl = url("url")
      val urlText = url("text")
      tweetText = text.replaceAll( url("text"), raw"""<a class="timeline-link" data-expanded-url="$expandedUrl">$urlText</a>""" )
    }
    play.Logger.debug("new tweetText:" + tweetText)
    tweetText
  }


  implicit val tweetWrites = new Writes[Tweet] {

    def writes(tweet: Tweet) = Json.obj(
      "img" -> tweet.user.getProfileImageURL,
      "user" -> tweet.user.getName,
      "screenName" -> tweet.user.getScreenName,
      "isVerified" -> tweet.user.isVerified,
      "text" -> makeTweetText(tweet.text, tweet.urls),
      "createdAt" -> tweet.createdAt,
      "source" -> tweet.source
    )
  }
}

abstract class WSMessage[T] {
  val messageType: String
  val data: T
}

case class ActorReadyMessage(messageType: String = "status", data: String) extends WSMessage[String]

object ActorReadyMessage {
  implicit val actorReadyMessageWrites = new Writes[ActorReadyMessage] {
    def writes(actorReadyMessage: ActorReadyMessage) = Json.obj(
      "type" -> actorReadyMessage.messageType,
      "data" -> actorReadyMessage.data
    )
  }
}

case class TweetUpdateMessage(messageType: String = "tweet", data: Tweet) extends WSMessage[Tweet]

object TweetUpdateMessage {
  implicit val tweetUpdateMessageWrites = new Writes[TweetUpdateMessage] {
    def writes(tweetUpdateMessage: TweetUpdateMessage) = Json.obj(
      "type" -> tweetUpdateMessage.messageType,
      "data" -> tweetUpdateMessage.data
    )
  }
}

case class ErrorUpdateMessage(messageType: String = "error", data: ErrorMessage) extends  WSMessage[ErrorMessage]

object ErrorUpdateMessage {
  implicit val errorUpdateMessageWrites = new Writes[ErrorUpdateMessage] {
    def writes(eum: ErrorUpdateMessage) = Json.obj(
      "type" -> "error",
      "data" -> eum.data.message
    )
  }
}

case class TwitterErrorMessage(messageType: String = "error", data: TwitterError) extends WSMessage[TwitterError]
object TwitterErrorMessage {
  implicit val twitterErrorMessageWrites = new Writes[TwitterErrorMessage] {
    def writes(em: TwitterErrorMessage) = Json.obj(
      "type" -> "error",
      "data" -> (em.data.code + ": " + em.data.message)
    )
  }
}

/**
 * UserActor - Interface between client (over web socket) and Twitter Stream (TweetActor)
 */

object UserActor {
  def props(out: ActorRef, authData: Map[String, String]) = Props(new UserActor(out, authData))
}

class UserActor(out: ActorRef, authData: Map[String, String]) extends Actor with Stash {
  import context._

  val tweetActorProps = TweetActor.props(authData)
  val tweetActor = actorOf(tweetActorProps, "TweetActor")
  watch(tweetActor)

  def receive = {

    case TweetActorReady =>
      become(actualReceiver)
      unstashAll()

      out ! Json.toJson(ActorReadyMessage(data = "ACTOR_READY")).toString

    case ErrorMessage(message, cause, stackTrace) =>
      play.Logger.error("[UserActor] Received ErrorMessage from " + sender.path + " - " + message)
      throw cause
      out ! Json.toJson(ErrorUpdateMessage(data = ErrorMessage(message, cause, stackTrace))).toString
      self ! PoisonPill

    case TwitterError(message, code) =>
      play.Logger.error("[UserActor] Received TwitterError from " + sender.path + " - " + message)
      out ! Json.toJson(TwitterErrorMessage(data = TwitterError(message, code)))

    case Terminated =>
      play.Logger.debug(sender().path + " terminated, poisoning self")
      self ! PoisonPill

    case msg =>
      play.Logger.debug("[UserActor] msg Stashed")
      stash()
  }

  def actualReceiver: Receive = {

    // Received from client ws -- initialises twitter stream api
    case jsonIn: JsValue =>
      play.Logger.debug("received some JsValue")
      val qResult: JsResult[String] = (jsonIn \ "reqType").validate[String]
      qResult match {
        case s: JsSuccess[String] =>
          if (s.get.equals("search")) {
            val term = (jsonIn \ "term").as[String]
            play.Logger.debug("Sending SetWatching for term: " + term)
            tweetActor ! WatchTerm(term)
          } else if (s.get.equals("action")) {
            val action = (jsonIn \ "action").as[String]
            play.Logger.debug( "Sending action '$action' to TweetActor" )

            if (action.equals("StopWatching")) {
              tweetActor ! StopWatching
            }
          }

        case e: JsError =>
          play.Logger.error("Something went wrong")
      }


    // Received from TweetActor
    case TweetMessage(status: twitter4j.Status) =>

      if (!status.isRetweet && status.getLang.equals("en")) {

        val contributers = status.getContributors.mkString(",")
        val replyToUserId = status.getInReplyToUserId
        val replyToScreenName = status.getInReplyToScreenName
        val replytoTweet = status.getInReplyToStatusId

        val geoLocation = for {
          geoLocationOption <- Option(status.getGeoLocation)
        } yield {
          val lat = geoLocationOption.getLatitude
          val long = geoLocationOption.getLongitude
        }

        val placeEntities = for {
          place <- Option(status.getPlace)
        } yield {
            val placeId = place.getId
            val placeName = place.getFullName
            val streetAddress = place.getStreetAddress
            val country = place.getCountry
          }

        val countriesWithHeldIn = for {
          countriesWithHeldIn <- Option(status.getWithheldInCountries)
        } yield countriesWithHeldIn

        play.Logger.debug("status.getUrlEntities.length: " + status.getURLEntities.length)
        val urls = ArrayBuffer.empty[Map[String, String]]
        status.getURLEntities foreach { urlEntity =>
          play.Logger.debug("displayUrl: " + urlEntity.getDisplayURL)
          play.Logger.debug("text: " + urlEntity.getText)
          urls += Map (
            "displayUrl" -> urlEntity.getDisplayURL.toString,
            "url" -> urlEntity.getExpandedURL.toString,
            "text" -> urlEntity.getText.toString,
            "start" -> urlEntity.getStart.toString,
            "end" -> urlEntity.getEnd.toString
          )
        }

        val mentionEntities = for {
          mentionEntity <- status.getUserMentionEntities
          mention <- Map (
            "screenName" -> mentionEntity.getScreenName,
            "name" -> mentionEntity.getName,
            "id" -> mentionEntity.getId,
            "start" -> mentionEntity.getStart,
            "end" -> mentionEntity.getEnd,
            "text" -> mentionEntity.getText
          )
        } yield mention

        val mediaEntities = for {
          mediaEntity <- status.getMediaEntities
          media <- Map (
            "id" -> mediaEntity.getId,
            "url" -> mediaEntity.getMediaURL,
            "httpsUrl" -> mediaEntity.getMediaURLHttps,
            "sizes" -> mediaEntity.getSizes
          )
        } yield media

        val hashtags = for {
          hashtag <- status.getHashtagEntities
          ht <- Map(
            "text" -> hashtag.getText,
            "start" -> hashtag.getStart,
            "end" -> hashtag.getEnd
          )
        } yield ht

        if (status.getURLEntities.length > 0) {
          models.Tweet(status.getId, authData("userid").toLong, status.getUser.getId, status.getUser.getScreenName, status.getText, status.getCreatedAt.formatted("Y-m-d H:i:s"))

          val tweetObj = Tweet(status.getUser, status.getText, urls.toArray, status.getCreatedAt.toString, status.getSource)
          val tweetUpdateMessage = TweetUpdateMessage("tweet", tweetObj)
          val tweetUpdateJson = Json.toJson(tweetUpdateMessage)

          play.Logger.debug("Sending to out -- " + tweetUpdateJson.toString)
          out ! tweetUpdateJson.toString
        }
      }

    case "StopWatching" => // Received from client ws
      play.Logger.debug("Sending StopWatching")
      tweetActor ! StopWatching

    case text: String =>
      play.Logger.debug("[UserActor] received String message: " + text)

    case Terminated =>
      play.Logger.debug(sender().path + " terminated, poisoning self")
      self ! PoisonPill
  }

  override def postStop(): Unit = {
    tweetActor ! PoisonPill
    play.Logger.debug("UserActor stopped")
  }
}

//HoseBirdClient
//
//import com.twitter.hbc.ClientBuilder
//import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint
//import com.twitter.hbc.core.event.Event
//import com.twitter.hbc.core.processor.StringDelimitedProcessor
//import com.twitter.hbc.core.{Client, Constants, Hosts, HttpHosts}
//import com.twitter.hbc.httpclient.auth.{Authentication, OAuth1}

//  val msgQueue: BlockingQueue[String] = new LinkedBlockingQueue[String](100000)
//  val eventQueue: BlockingQueue[Event] = new LinkedBlockingQueue[Event](1000)
//
//  val hosebirdHosts: Hosts = new HttpHosts(Constants.STREAM_HOST)
//  val hosebirdEndpoint: StatusesFilterEndpoint = new StatusesFilterEndpoint()
//
//  val followings: java.util.List[java.lang.Long] = ListBuffer(List[java.lang.Long](1234L, 566788L): _*)
//  val terms: java.util.List[String] = ListBuffer(List("twitter", "api"): _*)
//  //  hosebirdEndpoint.followings(followings)
//  hosebirdEndpoint.trackTerms(terms)
//
//  // These secrets should be read from a config file
//  val hosebirdAuth: Authentication = new OAuth1(authData("consumerKey"), authData("consumerSecret"), authData("token"), authData("secret"))
//
//  val builder: ClientBuilder = new ClientBuilder()
//    .name("Hosebird-Client-01")                              // optional: mainly for the logs
//    .hosts(hosebirdHosts)
//    .authentication(hosebirdAuth)
//    .endpoint(hosebirdEndpoint)
//    .processor(new StringDelimitedProcessor(msgQueue))
//    .eventMessageQueue(eventQueue)                          // optional: use this if you want to process client events
//
//  val hosebirdClient: Client = builder.build()
//  Logger.info("Connecting to " + Constants.STREAM_HOST + hosebirdEndpoint.getPath + "?" + hosebirdEndpoint.getPostParamString)
//  hosebirdClient.connect()
//
//  while (!hosebirdClient.isDone()) {
//    Logger.debug("waiting for msgs")
//    val msg = msgQueue.take()
//    Logger.info(msg)
//    out ! msg
//  }
