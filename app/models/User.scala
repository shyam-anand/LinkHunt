package models

import anorm._
import play.api.db.DB
import play.api.Play.current

/* TODO: Cache user data */

object User {
  def saveUser(userId: Long, screen_name: String, token: String, secret: String): Option[Long] = {
    DB.withConnection("twitstream") { implicit c =>
      SQL("INSERT INTO users(userid, screen_name, token, secret) VALUES ({userid}, {screen_name}, {token}, {secret}) ON DUPLICATE KEY UPDATE token = {token}, secret = {secret}")
        .on('userid -> userId, 'screen_name -> screen_name, 'token -> token, 'secret -> secret).executeInsert()

    }
  }

  def getAuthData(userId: String): Option[Map[String, String]] = {
    DB.withConnection("twitstream") { implicit c =>
      val selectRow = SQL("SELECT token, secret FROM users WHERE userid = {userid}").on('userid -> userId)
      selectRow.apply().headOption.map( row =>
        Map(
          "userid" -> userId,
          "token" -> row[String]("token"),
          "secret" -> row[String]("secret")
        )
      )
    }
  }

  def checkCredentials(username: String, password: String): Option[Long] = {
    DB.withConnection("twitstream") { implicit c =>
      SQL("SELECT userid FROM users WHERE username = $username AND password = MD5($password)")
        .apply().headOption.map ( row => row[Long]("id") )
    }
  }
}