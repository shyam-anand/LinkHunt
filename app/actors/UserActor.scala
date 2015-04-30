package actors

import akka.actor.Status._
import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import play.api.libs.json._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
case class Tweet(user: twitter4j.User, text: String, createdAt: String, source: String)

object Tweet {
  implicit val tweetWrites = new Writes[Tweet] {
    def writes(tweet: Tweet) = Json.obj(
      "img" -> tweet.user.getProfileImageURL,
      "user" -> tweet.user.getName,
      "screenName" -> tweet.user.getScreenName,
      "isVerified" -> tweet.user.isVerified,
      "text" -> tweet.text,
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
          val term = (jsonIn \ "term").as[String]
          play.Logger.debug("Sending SetWatching for term: " + term)
          tweetActor ! WatchTerm(term)
        case e: JsError =>
          play.Logger.error("Something went wrong")
      }
    // Received from TweetActor
    case TweetMessage(tweet: twitter4j.Status) =>
      play.Logger.debug("Received TweetMessage")

      if (tweet.isRetweet) {
        play.Logger.debug("isRetweet, ignoring")
      } else {
        val tweetObj = Tweet(tweet.getUser, tweet.getText, tweet.getCreatedAt.toString, tweet.getSource)
        val tweetUpdateMessage = TweetUpdateMessage("tweet", tweetObj)
        val tweetUpdateJson = Json.toJson(tweetUpdateMessage)

        play.Logger.debug("Sending to out -- " + tweetUpdateJson.toString)
        out ! tweetUpdateJson.toString
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

  override def preStart(): Unit = {

    implicit val timeout = Timeout(5 seconds)
    val tweetActorStatus: Future[Boolean] = (tweetActor ? AreYouReady).mapTo[Boolean]
    play.Logger.debug("waiting for status from TweetActor")

    tweetActorStatus onComplete {
      case Success(status) =>
        if (status) {
          play.Logger.info("[UserActor] tweetActor created")

          become(actualReceiver)
          unstashAll()

          val message = ActorReadyMessage(data = "ACTOR_READY")
          val msgJson = Json.toJson(message)
          out ! msgJson.toString
        } else {
          play.Logger.error("Error creating tweetActor")
        }
      case Failure(t) =>
        play.Logger.error(t.getMessage)
    }

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
