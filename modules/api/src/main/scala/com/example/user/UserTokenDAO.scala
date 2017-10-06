package com.example.user

import java.util.UUID

import org.joda.time.DateTime

import scala.concurrent.Future

/**
  * An implementation dependent DAO.  This could be implemented by Slick, Cassandra, or a REST API.
  */
trait UserTokenDAO {

  def lookup(id: UUID): Future[Option[UserToken]]

  def delete(id: UUID): Future[Int]

  def create(userToken: UserToken): Future[UserToken]

  def close(): Future[Unit]
}

/**
  * Implementation independent aggregate root.
  */
case class UserToken(id: UUID, userId: String, email: String, expirationTime: DateTime, isSignUp: Boolean) {
  def isExpired: Boolean = expirationTime.isBeforeNow
}

object UserToken {
  def create(userId: String, email: String, isSignUp: Boolean) =
    UserToken(UUID.randomUUID(), userId, email, new DateTime().plusHours(12), isSignUp)
}
