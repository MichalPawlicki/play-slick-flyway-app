package services

import javax.inject.Inject

import auth.UserIdentity
import com.example.user.{User, UserDAO}
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider

import scala.concurrent.{ExecutionContext, Future}

class UserIdentityService @Inject()(userDAO: UserDAO)
                                   (implicit ec: ExecutionContext)
  extends IdentityService[UserIdentity] {

  override def retrieve(loginInfo: LoginInfo): Future[Option[UserIdentity]] =
    loginInfo.providerID match {
      case CredentialsProvider.ID =>
        userDAO.findByEmail(loginInfo.providerKey).map { maybeUser =>
          maybeUser.map(user => UserIdentity(user))
        }
      case _ => Future.failed(???)
    }

  def save(user: User): Future[Int] = userDAO.create(user)

  def find(id: String): Future[Option[User]] = userDAO.lookup(id)

  def confirm(loginInfo: LoginInfo): Future[User] =
    loginInfo.providerID match {
      case CredentialsProvider.ID =>
        for {
          maybeUser <- userDAO.findByEmail(loginInfo.providerKey)
          confirmedUser = maybeUser.get.copy(isConfirmed = true)
          _ <- userDAO.update(confirmedUser)
        } yield confirmedUser
      case _ => Future.failed(???)
    }
}
