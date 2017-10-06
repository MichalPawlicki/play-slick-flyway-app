package com.example.user.slick

import java.util.UUID
import javax.inject.{Inject, Singleton}

import slick.jdbc.JdbcBackend.Database
import slick.jdbc.JdbcProfile
import com.example.user._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

/**
  * A User DAO implemented with Slick, leveraging Slick code gen.
  *
  * Note that you must run "flyway/flywayMigrate" before "compile" here.
  *
  * @param db the slick database that this user DAO is using internally, bound through Module.
  * @param ec a CPU bound execution context.  Slick manages blocking JDBC calls with its
  *           own internal thread pool, so Play's default execution context is fine here.
  */
@Singleton
class SlickUserTokenDAO @Inject()(db: Database)(implicit ec: ExecutionContext) extends UserTokenDAO with Tables {

  //  override val profile: JdbcProfile = _root_.slick.jdbc.H2Profile
  override val profile: JdbcProfile = _root_.slick.jdbc.PostgresProfile

  import profile.api._

  private val queryById = Compiled(
    (id: Rep[UUID]) => UserTokens.filter(_.id === id))

  def lookup(id: UUID): Future[Option[UserToken]] = {
    val f: Future[Option[UserTokensRow]] = db.run(queryById(id).result.headOption)
    f.map(maybeRow => maybeRow.map(rowToUserToken))
  }

  def delete(id: UUID): Future[Int] = {
    db.run(queryById(id).delete)
  }

  def create(userToken: UserToken): Future[UserToken] = {
    db.run(
      UserTokens.returning(UserTokens) += userTokenToRow(userToken)
    ).map(rowToUserToken)
  }

  def close(): Future[Unit] = {
    Future.successful(db.close())
  }

  private def userTokenToRow(token: UserToken): UserTokensRow = {
    UserTokensRow(token.id, token.userId, token.email, token.expirationTime, token.isSignUp)
  }

  private def rowToUserToken(row: UserTokensRow): UserToken = {
    UserToken(row.id, row.userId, row.email, row.expirationTime, row.isSignUp)
  }
}
