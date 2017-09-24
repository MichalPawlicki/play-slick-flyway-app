package services

import javax.inject.Inject

import com.example.user.UserDAO
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.util.{PasswordHasher, PasswordInfo}
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO

import scala.concurrent.{ExecutionContext, Future}

class PasswordInfoService @Inject()(passwordHasher: PasswordHasher,
                                    userDAO: UserDAO,
                                    userIdentityService: UserIdentityService)
                                   (implicit ec: ExecutionContext)
  extends DelegableAuthInfoDAO[PasswordInfo] {

  def find(loginInfo: LoginInfo): Future[Option[PasswordInfo]] =
    userIdentityService.retrieve(loginInfo).map { maybeUserIdentity =>
      maybeUserIdentity.map { userIdentity =>
        PasswordInfo(passwordHasher.id, userIdentity.user.passwordHash)
      }
    }

  def add(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] = ???

  def update(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] = ???

  def save(loginInfo: LoginInfo, authInfo: PasswordInfo): Future[PasswordInfo] = ???

  def remove(loginInfo: LoginInfo): Future[Unit] = ???
}
