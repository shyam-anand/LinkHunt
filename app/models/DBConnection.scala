package models

import play.api.Play.current
import anorm._
import play.api.db.DB

object DBConnection {

  def logTweet(tweetObj: Tweet): Unit = {
    DB.withConnection("twitstream") { implicit c =>
      SQL("INSERT INTO tweets (id, user, screen_name, tweet, created_at) VALUES ({id}, {user}, {screen_name}, {tweet}, {created_at})")
      .on('id -> tweetObj.id, 'user -> tweetObj.user.name, 'screen_name -> tweetObj.user.screen_name, 'tweet -> tweetObj.text, 'created_at -> tweetObj.created_at).executeInsert()

    }
  }

}
