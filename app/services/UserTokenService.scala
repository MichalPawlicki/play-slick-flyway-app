package services

import java.util.UUID
import javax.inject._

import com.example.user.{UserToken, UserTokenDAO}

import scala.concurrent.Future

class UserTokenService @Inject()(userTokenDao: UserTokenDAO) {
  def find(id: UUID): Future[Option[UserToken]] = userTokenDao.lookup(id)

  def save(token: UserToken): Future[UserToken] = userTokenDao.create(token)

  def remove(id: UUID): Future[Int] = userTokenDao.delete(id)
}
