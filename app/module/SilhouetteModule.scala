package module

import auth.SessionEnv
import com.google.inject.{AbstractModule, Provides}
import com.mohiva.play.silhouette
import com.mohiva.play.silhouette.api.actions.{SecuredAction, UnsecuredAction, UserAwareAction}
import com.mohiva.play.silhouette.api.crypto.{AuthenticatorEncoder, Base64AuthenticatorEncoder, Signer}
import com.mohiva.play.silhouette.api.services.AuthenticatorService
import com.mohiva.play.silhouette.api.util.{Clock, FingerprintGenerator, IDGenerator}
import com.mohiva.play.silhouette.api.{EventBus, Silhouette, SilhouetteProvider}
import com.mohiva.play.silhouette.crypto.{JcaSigner, JcaSignerSettings}
import com.mohiva.play.silhouette.impl.authenticators.{CookieAuthenticator, CookieAuthenticatorService, CookieAuthenticatorSettings}
import com.mohiva.play.silhouette.impl.util.{DefaultFingerprintGenerator, SecureRandomIDGenerator}
import play.api.Configuration
import play.api.mvc.CookieHeaderEncoding
import services.UserIdentityService

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

class SilhouetteModule extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[AuthenticatorEncoder]).toInstance(new Base64AuthenticatorEncoder)
    bind(classOf[Clock]).toInstance(Clock())
    bind(classOf[FingerprintGenerator]).toInstance(new DefaultFingerprintGenerator(false))
    bind(classOf[Signer]).to(classOf[JcaSigner])
    //    bind(classOf[Silhouette[SessionEnv]]).to(classOf[SilhouetteProvider[SessionEnv]])
    //    bind()
  }

  //  override def bindings(environment: play.api.Environment, configuration: Configuration) = Seq(
  //    bind[Silhouette[SessionEnv]].to[SilhouetteProvider[SessionEnv]]
  //    bind[IdentityService[User]].to[UserService]
  //    bind[UserDao].to[MongoUserDao]
  //    bind[UserTokenDao].to[MongoUserTokenDao]
  //    bind[DelegableAuthInfoDAO[PasswordInfo]].to[PasswordInfoDao]
  //    bind[DelegableAuthInfoDAO[OAuth1Info]].to[OAuth1InfoDao]

  //    bind[PasswordHasher].toInstance(new BCryptPasswordHasher)

  //    bind[EventBus].toInstance(EventBus())
  //    bind[Clock].toInstance(Clock())
  //  )

  @Provides
  def provideAuthenticatorService(cookieAuthenticatorSettings: CookieAuthenticatorSettings,
                                  configuration: Configuration,
                                  signer: Signer,
                                  cookieHeaderEncoding: CookieHeaderEncoding,
                                  authenticatorEncoder: AuthenticatorEncoder,
                                  fingerprintGenerator: FingerprintGenerator,
                                  idGenerator: IDGenerator,
                                  clock: Clock)
                                 (implicit ec: ExecutionContext): AuthenticatorService[CookieAuthenticator] = {
    new CookieAuthenticatorService(
      cookieAuthenticatorSettings,
      None,
      signer,
      cookieHeaderEncoding,
      authenticatorEncoder,
      fingerprintGenerator,
      idGenerator,
      clock
    )
  }

  @Provides
  def provideCookieAuthenticatorSettings(configuration: Configuration): CookieAuthenticatorSettings = {
    val config = configuration.get[Configuration]("authenticator")

    CookieAuthenticatorSettings(
      cookieName = config.get[String]("cookieName"),
      cookiePath = config.get[String]("cookiePath"),
      secureCookie = config.get[Boolean]("secureCookie"),
      httpOnlyCookie = config.get[Boolean]("httpOnlyCookie"),
      useFingerprinting = config.get[Boolean]("useFingerprinting"),
      authenticatorIdleTimeout = config.getOptional[FiniteDuration]("authenticatorIdleTimeout"),
      authenticatorExpiry = config.get[FiniteDuration]("authenticatorExpiry")
    )
  }

  @Provides
  def provideEnvironment(identityService: UserIdentityService,
                         authenticatorService: AuthenticatorService[CookieAuthenticator],
                         eventBus: EventBus)
                        (implicit ec: ExecutionContext): silhouette.api.Environment[SessionEnv] = {
    silhouette.api.Environment[SessionEnv](
      identityService,
      authenticatorService,
      Seq(),
      eventBus
    )
  }

  @Provides
  def provideIdGenerator(implicit ec: ExecutionContext): IDGenerator = {
    new SecureRandomIDGenerator()
  }

  @Provides
  def provideJcaSigner(configuration: Configuration): JcaSigner = {
    val secretKey = configuration.get[String]("play.http.secret.key")
    val signerSettings = JcaSignerSettings(secretKey)
    new JcaSigner(signerSettings)
  }

  @Provides
  def provideSilhouette(environment: silhouette.api.Environment[SessionEnv],
                        securedAction: SecuredAction,
                        unsecuredAction: UnsecuredAction,
                        userAwareAction: UserAwareAction
                       ): Silhouette[SessionEnv] = {
    new SilhouetteProvider[SessionEnv](environment, securedAction, unsecuredAction, userAwareAction)
  }

}
