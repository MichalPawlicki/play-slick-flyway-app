package services

import javax.inject.Inject

import auth.UserIdentity
import com.example.user.UserDAO
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService

import scala.concurrent.Future

class UserIdentityService @Inject()(userDAO: UserDAO) extends IdentityService[UserIdentity] {
  override def retrieve(loginInfo: LoginInfo): Future[Option[UserIdentity]] = ???
}
