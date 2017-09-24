package controllers

import java.util.UUID
import javax.inject.{Inject, Singleton}

import auth.SessionEnv
import com.example.user.{User, UserToken}
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.util.{Credentials, PasswordHasher}
import com.mohiva.play.silhouette.api.{LoginInfo, Silhouette}
import com.mohiva.play.silhouette.impl.exceptions.IdentityNotFoundException
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import org.joda.time.DateTime
import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText, tuple, _}
import play.api.data.validation.Constraints.minLength
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc.{AbstractController, Action, ControllerComponents}
import services.{UserIdentityService, UserTokenService}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object AuthForms {

  // Sign up
  case class SignUpData(email: String, password: String)

  def signUpForm(implicit messages: Messages) = Form(
    mapping(
      "email" -> email,
      "password" -> tuple(
        "password1" -> nonEmptyText.verifying(minLength(6)),
        "password2" -> nonEmptyText
      ).verifying(Messages("error.passwordsDontMatch"), password => password._1 == password._2)
      //    "firstName" -> nonEmptyText,
      //    "lastName" -> nonEmptyText
    )
    ((email, password) => SignUpData(email, password._1))
    (signUpData => Some((signUpData.email, ("", ""))))
  )

  // Sign in
  case class SignInData(email: String, password: String, rememberMe: Boolean)

  val signInForm = Form(
    mapping(
      "email" -> email,
      "password" -> nonEmptyText,
      "rememberMe" -> boolean
    )(SignInData.apply)(SignInData.unapply)
  )
}

@Singleton
class Auth @Inject()(val cc: ControllerComponents,
                     silhouette: Silhouette[SessionEnv],
                     credentialsProvider: CredentialsProvider,
                     userIdentityService: UserIdentityService,
                     userTokenService: UserTokenService,
                     passwordHasher: PasswordHasher,
                     startSignUpTemplate: views.html.auth.startSignUp,
                     finishSignUpTemplate: views.html.auth.finishSignUp,
                     notFoundTemplate: views.html.errors.notFound,
                     signInTemplate: views.html.auth.signIn)
                    (implicit ec: ExecutionContext)
  extends AbstractController(cc)
    with I18nSupport {

  import AuthForms._

  def startSignUp = silhouette.UserAwareAction.async { implicit request =>
    Future.successful(request.identity match {
      case Some(user) => Redirect(routes.HomeController.index())
      case None => Ok(startSignUpTemplate(signUpForm))
    })
  }

  def handleStartSignUp = Action.async { implicit request =>
    signUpForm.bindFromRequest.fold(
      bogusForm => Future.successful(BadRequest(startSignUpTemplate(bogusForm))),
      signUpData => {
        val loginInfo = LoginInfo(CredentialsProvider.ID, signUpData.email)
        userIdentityService.retrieve(loginInfo).flatMap {
          case Some(_) =>
            Future.successful(Redirect(routes.Auth.startSignUp()).flashing(
              "error" -> Messages("error.userExists", signUpData.email)))
          case None =>
            val passwordInfo = passwordHasher.hash(signUpData.password)
            val user = User(id = UUID.randomUUID().toString, email = signUpData.email, createdAt = DateTime.now,
              updatedAt = None, passwordHash = passwordInfo.password, isConfirmed = false)
            val token = UserToken.create(user.id, signUpData.email, isSignUp = true)
            for {
              _ <- userIdentityService.save(user)
              _ <- userTokenService.save(token)
            } yield {
              println(routes.Auth.signUp(token.id.toString).absoluteURL())
              Ok(finishSignUpTemplate(user))
            }
        }
      }
    )
  }

  def signUp(tokenId: String) = Action.async { implicit request =>
    val eventualMaybeToken = Try(UUID.fromString(tokenId))
      .map(id => userTokenService.find(id))
      .getOrElse(Future.successful(None))
    eventualMaybeToken.flatMap {
      case None =>
        Future.successful(NotFound(notFoundTemplate(request)))
      case Some(token) if token.isSignUp && !token.isExpired =>
        userIdentityService.find(token.userId).flatMap {
          case None => Future.failed(new IdentityNotFoundException(Messages("error.noUser")))
          case Some(user) =>
            val loginInfo = LoginInfo(CredentialsProvider.ID, token.email)
            for {
              authenticator <- silhouette.env.authenticatorService.create(loginInfo)
              value <- silhouette.env.authenticatorService.init(authenticator)
              _ <- userIdentityService.confirm(loginInfo)
              _ <- userTokenService.remove(token.id)
              result <- silhouette.env.authenticatorService.embed(value, Redirect(routes.HomeController.index()))
            } yield result
        }
      case Some(token) =>
        userTokenService.remove(token.id).map { _ => NotFound(notFoundTemplate(request)) }
    }
  }

  def signIn = silhouette.UserAwareAction.async { implicit request =>
    Future.successful(request.identity match {
      case Some(user) => Redirect(routes.HomeController.index())
      case None => Ok(signInTemplate(signInForm))
    })
  }

  def authenticate = Action.async { implicit request =>
    signInForm.bindFromRequest.fold(
      bogusForm => Future.successful(BadRequest(signInTemplate(bogusForm))),
      signInData => {
        val credentials = Credentials(signInData.email, signInData.password)
        credentialsProvider.authenticate(credentials).flatMap { loginInfo =>
          userIdentityService.retrieve(loginInfo).flatMap {
            case None =>
              Future.successful(Redirect(routes.Auth.signIn()).flashing("error" -> Messages("error.noUser")))
            case Some(userIdentity) if !userIdentity.user.isConfirmed =>
              Future.successful(Redirect(routes.Auth.signIn()).flashing("error" -> Messages("error.unregistered", signInData.email)))
            case Some(_) =>
              for {
                authenticator <- silhouette.env.authenticatorService.create(loginInfo)
                value <- silhouette.env.authenticatorService.init(authenticator)
                result <- silhouette.env.authenticatorService.embed(value, Redirect(routes.HomeController.index()))
              } yield result
          }
        }.recover {
          case e: ProviderException =>
            Redirect(routes.Auth.signIn()).flashing("error" -> Messages("error.invalidCredentials"))
        }
      }
    )
  }
}
