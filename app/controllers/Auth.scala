package controllers

import java.util.UUID
import javax.inject.{Inject, Singleton}

import auth.{SessionEnv, UserIdentity}
import com.example.user.{User, UserToken}
import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.{Credentials, PasswordHasher}
import com.mohiva.play.silhouette.api.{LoginInfo, Silhouette}
import com.mohiva.play.silhouette.impl.exceptions.IdentityNotFoundException
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import org.joda.time.DateTime
import play.api.data.Form
import play.api.data.Forms.{mapping, nonEmptyText, tuple, _}
import play.api.data.validation.Constraints.minLength
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc.{AbstractController, ControllerComponents}
import services.{UserIdentityService, UserTokenService}
import utils.Mailer

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object AuthForms {

  def newPasswordField(implicit messages: Messages) = tuple(
    "password1" -> nonEmptyText.verifying(minLength(6)),
    "password2" -> nonEmptyText
  ).verifying(Messages("error.passwordsDontMatch"), password => password._1 == password._2)

  // Sign up
  case class SignUpData(email: String, password: String)

  def signUpForm(implicit messages: Messages) = {
    Form(
      mapping(
        "email" -> email,
        "password" -> newPasswordField
      )
      ((email, password) => SignUpData(email, password._1))
      (signUpData => Some((signUpData.email, ("", ""))))
    )
  }

  // Sign in
  case class SignInData(email: String, password: String, rememberMe: Boolean)

  val signInForm = Form(
    mapping(
      "email" -> email,
      "password" -> nonEmptyText,
      "rememberMe" -> boolean
    )(SignInData.apply)(SignInData.unapply)
  )

  // Start reset password
  val emailForm = Form(single("email" -> email))

  // Reset password
  def resetPasswordForm(implicit messages: Messages) = Form(
    mapping(
      "password" -> newPasswordField
    )(passwordTuple => passwordTuple._1)(_ => Some(("", "")))
  )
}

@Singleton
class Auth @Inject()(val cc: ControllerComponents,
                     silhouette: Silhouette[SessionEnv],
                     credentialsProvider: CredentialsProvider,
                     userIdentityService: UserIdentityService,
                     userTokenService: UserTokenService,
                     authInfoRepository: AuthInfoRepository,
                     passwordHasher: PasswordHasher,
                     mailer: Mailer,
                     startSignUpTemplate: views.html.auth.startSignUp,
                     finishSignUpTemplate: views.html.auth.finishSignUp,
                     notFoundTemplate: views.html.errors.notFound,
                     signInTemplate: views.html.auth.signIn,
                     startResetPasswordTemplate: views.html.auth.startResetPassword,
                     resetPasswordInstructionsTemplate: views.html.auth.resetPasswordInstructions,
                     resetPasswordTemplate: views.html.auth.resetPassword,
                     resetPasswordDoneTemplate: views.html.auth.resetPasswordDone,
                     resendEmailTemplate: views.html.auth.resendEmail,
                     finishResendTemplate: views.html.auth.finishResend)
                    (implicit ec: ExecutionContext)
  extends AbstractController(cc)
    with I18nSupport {

  import AuthForms._

  def startSignUp = silhouette.UserAwareAction.async { implicit request =>
    Future.successful(request.identity match {
      case Some(user) => Redirect(routes.Home.index())
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
              mailer.welcome(signUpData.email, routes.Auth.signUp(token.id).absoluteURL())
              Ok(finishSignUpTemplate(user))
            }
        }
      }
    )
  }

  def signUp(tokenId: UUID) = Action.async { implicit request =>
    userTokenService.find(tokenId).flatMap {
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
              result <- silhouette.env.authenticatorService.embed(value, Redirect(routes.Home.index()))
            } yield result
        }
      case Some(token) =>
        userTokenService.remove(token.id).map { _ => NotFound(notFoundTemplate(request)) }
    }
  }

  def signIn = silhouette.UserAwareAction.async { implicit request =>
    Future.successful(request.identity match {
      case Some(user) => Redirect(routes.Home.index())
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
                result <- silhouette.env.authenticatorService.embed(value, Redirect(routes.Home.index()))
              } yield result
          }
        }.recover {
          case e: ProviderException =>
            Redirect(routes.Auth.signIn()).flashing("error" -> Messages("error.invalidCredentials"))
        }
      }
    )
  }

  def signOut = silhouette.SecuredAction.async { implicit request =>
    silhouette.env.authenticatorService.discard(request.authenticator, Redirect(routes.Home.index()))
  }

  def startResetPassword = Action { implicit request =>
    Ok(startResetPasswordTemplate(emailForm))
  }

  def handleStartResetPassword = Action.async { implicit request =>
    emailForm.bindFromRequest.fold(
      bogusForm => Future.successful(BadRequest(startResetPasswordTemplate(bogusForm))),
      email => userIdentityService.retrieve(LoginInfo(CredentialsProvider.ID, email)).flatMap {
        case None => Future.successful(Redirect(routes.Auth.startResetPassword()).flashing("error" -> Messages("error.noUser")))
        case Some(UserIdentity(user)) => for {
          token <- userTokenService.save(UserToken.create(user.id, email, isSignUp = false))
        } yield {
          mailer.resetPassword(email, link = routes.Auth.resetPassword(token.id).absoluteURL())
          Redirect(routes.Auth.signIn).flashing("error" -> Messages("reset.instructions", email))
        }
      }
    )
  }

  def resetPassword(tokenId: UUID) = Action.async { implicit request =>
    userTokenService.find(tokenId).flatMap {
      case None =>
        Future.successful(NotFound(notFoundTemplate(request)))
      case Some(token) if token.isSignUp || token.isExpired =>
        userTokenService.remove(token.id).map { _ => NotFound(notFoundTemplate(request)) }
      case Some(token) =>
        Future.successful(Ok(resetPasswordTemplate(token.id, resetPasswordForm)))
    }
  }

  def handleResetPassword(tokenId: UUID) = Action.async { implicit request =>
    resetPasswordForm.bindFromRequest.fold(
      bogusForm => Future.successful(BadRequest(resetPasswordTemplate(tokenId, bogusForm))),
      newPassword => {
        userTokenService.find(tokenId).flatMap {
          case None =>
            Future.successful(NotFound(notFoundTemplate(request)))
          case Some(token) if token.isSignUp || token.isExpired =>
            userTokenService.remove(token.id).map { _ => NotFound(notFoundTemplate(request)) }
          case Some(token) =>
            val loginInfo = LoginInfo(CredentialsProvider.ID, token.email)
            for {
              _ <- authInfoRepository.save(loginInfo, passwordHasher.hash(newPassword))
            } yield Ok(resetPasswordDoneTemplate())
        }
      }
    )
  }

  def resendEmail = Action { implicit request =>
    Ok(resendEmailTemplate(emailForm))
  }

  def handleResendEmail = Action.async { implicit request =>
    emailForm.bindFromRequest.fold(
      bogusForm => Future.successful(BadRequest(resendEmailTemplate(bogusForm))),
      email => {
        val loginInfo = LoginInfo(CredentialsProvider.ID, email)
        userIdentityService.retrieve(loginInfo).flatMap {
          case Some(UserIdentity(user)) if !user.isConfirmed =>
            val token = UserToken.create(user.id, email, isSignUp = true)
            userTokenService.save(token).map { _ =>
              mailer.welcome(email, routes.Auth.signUp(token.id).absoluteURL())
              Ok(finishResendTemplate(email))
            }
          case _ =>
            Future.successful(Ok(finishResendTemplate(email)))
        }
      }
    )
  }
}
