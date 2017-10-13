package controllers

import javax.inject.{Inject, Singleton}

import _root_.auth.SessionEnv
import com.example.user.UserDAO
import com.mohiva.play.silhouette.api.Silhouette
import play.api.i18n.I18nSupport
import play.api.mvc._

import scala.concurrent.ExecutionContext

@Singleton
class Home @Inject()(userDAO: UserDAO,
                     cc: ControllerComponents,
                     silhouette: Silhouette[SessionEnv],
                     indexTemplate: views.html.index,
                     profileTemplate: views.html.profile)
                    (implicit ec: ExecutionContext)
  extends AbstractController(cc) with I18nSupport {

  def index = silhouette.UserAwareAction.async { implicit request =>
    val loggedUser = request.identity.map(_.user)
    val loginInfo = request.authenticator.map(_.loginInfo)
    userDAO.all.map { users =>
      Ok(indexTemplate(users, loggedUser, loginInfo))
    }
  }

  def profile = silhouette.SecuredAction { implicit request =>
    Ok(profileTemplate(request.identity.user, request.authenticator.loginInfo))
  }

}
