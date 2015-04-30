package models

import play.api.Play.current
import anorm._
import play.api.db.DB
import twitter4j.Twitter

case class Tweet(id: Long,
                 userId: Long,
                 authorId: Long,
                 authorScreenName: String,
                 text: String,
                 createdAt: String)

object Tweets {

  def logTweet(tweetObj: Tweet, user_id: Long): Unit = {
    DB.withConnection("twitstream") { implicit c =>
      SQL("INSERT INTO tweets (id, user, screen_name, tweet, created_at) VALUES ({id}, {user}, {screen_name}, {tweet}, {created_at})")
      .on('id -> tweetObj.id,
          'user -> tweetObj.userId,
          'author_id -> tweetObj.authorId,
          'author_screen_name -> tweetObj.authorScreenName,
          'tweet -> tweetObj.text,
          'is_deleted -> 0,
          'created_at -> tweetObj.createdAt
        ).executeInsert()

    }
  }

}
