package controllers

import javax.inject.{Inject, Singleton}

import auth.SessionEnv
import com.example.user.UserDAO
import com.mohiva.play.silhouette.api.Silhouette
import play.api.i18n.I18nSupport
import play.api.mvc._

import scala.concurrent.ExecutionContext

@Singleton
class HomeController @Inject()(userDAO: UserDAO,
                               cc: ControllerComponents,
                               silhouette: Silhouette[SessionEnv],
                               indexTemplate: views.html.index)
                              (implicit ec: ExecutionContext)
  extends AbstractController(cc) with I18nSupport {

  def index = silhouette.UserAwareAction.async { implicit request =>
    val currentUser = request.identity.map(_.user)
    userDAO.all.map { users =>
      Ok(indexTemplate(users, currentUser))
    }
  }

}
