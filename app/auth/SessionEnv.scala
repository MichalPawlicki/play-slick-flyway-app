package auth

import com.mohiva.play.silhouette.api.Env
import com.mohiva.play.silhouette.impl.authenticators.CookieAuthenticator

trait SessionEnv extends Env {
  override type I = UserIdentity
  override type A = CookieAuthenticator
}
