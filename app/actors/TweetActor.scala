package actors

import akka.actor._
import twitter4j._
import twitter4j.conf.ConfigurationBuilder

// TweetActor messages
case object AreYouReady
case class WatchTerm(watchTerm: String)
case object StopWatching
case class TweetMessage(tweet: Status)
case class AddFollowings(handleList: Seq[java.lang.Long])
case class ErrorMessage(message: String, cause: Throwable, stackTrace: Array[StackTraceElement])

object TweetActor {
  def props(authData: Map[String, String]) = Props(new TweetActor(authData))
}

/**
 * Connects to Twitter streaming end point using Apache HttpClient API.
 * @param authData Authentication keys
 */
class TweetActor(authData: Map[String, String]) extends Actor with Stash {
  import context._

  def receive = {
    case msg =>
      play.Logger.debug("[TweetActor] msg stashed")
      stash()
  }

  var connectionStatus = false
  
  val config = new ConfigurationBuilder()
    .setOAuthConsumerKey(authData("consumerKey"))
    .setOAuthConsumerSecret(authData("consumerSecret"))
    .setOAuthAccessToken(authData("token"))
    .setOAuthAccessTokenSecret(authData("secret"))
    .build()

  def simpleStatusListener = new StatusListener {
    override def onStallWarning(stallWarning: StallWarning): Unit = {
      play.Logger.info(" -- Stalled -- ")
    }

    override def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice): Unit = {
      play.Logger.info(" -- Deletion notice received for id " + statusDeletionNotice.getStatusId + " by user: " + statusDeletionNotice.getUserId)
    }

    override def onScrubGeo(l: Long, l1: Long): Unit = {

    }

    override def onStatus(status: Status): Unit = {
      // TODO: Save tweets, add check for retweets
      if ( status.isRetweet ) { //|| !status.getLang.equals("en") ) {
        play.Logger.debug(" -- ignoring tweet --")
        play.Logger.debug("status.getLang = " + status.getLang + ", status.isRetweet = " + status.isRetweet)
      } else {
        parent ! TweetMessage(status)
      }
    }

    override def onTrackLimitationNotice(i: Int): Unit = {

    }

    override def onException(e: Exception): Unit = {
      play.Logger.error("Exception in TweetActor.simpleStatusListener: " + e.getMessage)
      parent ! ErrorMessage(e.getMessage, e.getCause, e.getStackTrace)
      self ! PoisonPill
//      throw e
    }

  }

  val twitterStream = new TwitterStreamFactory(config).getInstance()
  twitterStream.addListener(simpleStatusListener)
  val filterQuery = new FilterQuery
  play.Logger.info("Connected to twitterStream as " + twitterStream.getScreenName)
  connectionStatus = true

  play.Logger.debug("[TweetActor] Setup complete, becoming afterInit")
  become(afterInit)
  play.Logger.debug("[TweetActor] un-stashing")
  unstashAll()

  def afterInit: Receive = {

    case AreYouReady =>
      sender ! true

    case WatchTerm(term: String) =>
      play.Logger.debug("[TweetActor] Received StartWatching term: " + term)
      watchTerm(term)

    case StopWatching =>
      play.Logger.debug("[TweetActor]  ----------- Received StopWatching -----------")
      twitterStream.cleanUp
  }

  def watchTerm(term: String) {
    play.Logger.debug("[TweetActor.watchTerm] Setting watchTerm to '" + term + "'")
    twitterStream.cleanUp
    val trackStrings = term.split(",")
    filterQuery.track(trackStrings)
    twitterStream.filter(filterQuery)
  }

  override def postStop() = {
    twitterStream.shutdown
    play.Logger.debug("Stopped TweetActor")
  }
}