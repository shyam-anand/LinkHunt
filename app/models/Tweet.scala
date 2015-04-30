package models

import play.api.libs.json.{JsPath, Reads}
import play.api.libs.functional.syntax._

/**
 * JsonReads for Twitter Stream API's status JSONs
 */


case class HashTag(text: String, indices: Seq[Int])
case class TweetUrl(url: String, expandedUrl: String, displayUrl: String, indices: Seq[Int])
case class TweetEntities(hashTags: Seq[String], trends: Seq[String], urls: Seq[TweetUrl])
case class User(id: Long, name: String, screen_name: String)
case class Tweet(id: Long, text: String, source: String, user: User, entities: TweetEntities, timestamp_ms: String, created_at: String)

object HashTag {
  implicit val HashTagReads: Reads[HashTag] = (
    (JsPath \ "text").read[String] and
      (JsPath \ "indices").read[Seq[Int]]
    )(HashTag.apply _)
}

object TweetUrl {
  implicit val TweetUrlReads: Reads[TweetUrl] = (
      (JsPath \ "url").read[String] and
      (JsPath \ "expanded_url").read[String] and
      (JsPath \ "display_url").read[String] and
      (JsPath \ "indices").read[Seq[Int]]
    )(TweetUrl.apply _)
}

object TweetEntities {
  implicit val TweetEntitiesReads: Reads[TweetEntities] = (
      (JsPath \ "hashtags").read[Seq[String]] and
      (JsPath \ "trends").read[Seq[String]] and
      (JsPath \ "urls").read[Seq[TweetUrl]]
    )(TweetEntities.apply _)
}

object User {
  implicit val UserReads: Reads[User] = (
      (JsPath \ "id").read[Long] and
      (JsPath \ "name").read[String] and
      (JsPath \ "screen_name").read[String]
    )(User.apply _)
}

object Tweet {
  implicit val TweetReads: Reads[Tweet] = (
      (JsPath \ "id").read[Long] and
      (JsPath \ "text").read[String] and
      (JsPath \ "source").read[String] and
      (JsPath \ "user").read[User] and
      (JsPath \ "entities").read[TweetEntities] and
      (JsPath \ "timestamp_ms").read[String] and
      (JsPath \ "created_at").read[String]
    )(Tweet.apply _)
}