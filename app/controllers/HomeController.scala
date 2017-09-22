package controllers

import javax.inject.{Inject, Singleton}

import auth.SessionEnv
import com.example.user.UserDAO
import com.mohiva.play.silhouette.api.actions.UserAwareRequest
import com.mohiva.play.silhouette.api.{Environment, Silhouette}
import play.api.mvc._

import scala.concurrent.ExecutionContext

@Singleton
class HomeController @Inject()(userDAO: UserDAO,
                               cc: ControllerComponents,
                               silhouette: Silhouette[SessionEnv])
                              (implicit ec: ExecutionContext)
  extends AbstractController(cc) {

  def index = silhouette.UserAwareAction.async { implicit request =>
    val currentUser = request.identity.map(_.user)
    userDAO.all.map { users =>
      Ok(views.html.index(users, currentUser))
    }
  }

}
