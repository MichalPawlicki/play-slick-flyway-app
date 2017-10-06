package utils

import javax.inject.Inject

import play.api.Configuration
import play.api.i18n.Messages
import play.api.libs.mailer.{Email, MailerClient}

import scala.concurrent.{ExecutionContext, Future}

class Mailer @Inject()(configuration: Configuration, mailerClient: MailerClient)
                      (implicit ec: ExecutionContext) {
  val from: String = configuration.get[String]("mail.from")
  val replyTo: Option[String] = configuration.getOptional[String]("mail.reply")

  def sendEmailAsync(recipients: String*)(subject: String, bodyHtml: Option[String], bodyText: Option[String]): Future[Unit] = {
    Future {
      sendEmail(recipients: _*)(subject, bodyHtml, bodyText)
    } recover {
      case e => play.api.Logger.error("error sending email", e)
    }
  }

  def sendEmail(recipients: String*)(subject: String, bodyHtml: Option[String], bodyText: Option[String]) {
    val email = Email(subject = subject, from = from, to = recipients, bodyHtml = bodyHtml,
      bodyText = bodyText, replyTo = replyTo.toList)
    mailerClient.send(email)
    ()
  }

  def welcome(email: String, link: String)(implicit messages: Messages): Future[Unit] = {
    sendEmailAsync(email)(
      subject = Messages("mail.welcome.subject"),
      bodyHtml = Some(views.html.mails.welcome(email, link).toString),
      bodyText = Some(views.html.mails.welcomeText(email, link).toString)
    )
  }

  def resetPassword(email: String, link: String)(implicit messages: Messages): Future[Unit] = {
    sendEmailAsync(email)(
      subject = Messages("mail.reset.subject"),
      bodyHtml = Some(views.html.mails.resetPassword(email, link).toString),
      bodyText = Some(views.html.mails.resetPasswordText(email, link).toString)
    )
  }
}
