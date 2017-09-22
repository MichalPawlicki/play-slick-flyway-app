package auth

import com.example.user.User
import com.mohiva.play.silhouette.api.Identity

case class UserIdentity(user: User) extends Identity
