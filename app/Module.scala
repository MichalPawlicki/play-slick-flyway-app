import javax.inject.{Inject, Provider, Singleton}

import com.example.user.slick.{SlickUserDAO, SlickUserTokenDAO}
import com.example.user.{UserDAO, UserTokenDAO}
import com.google.inject.{AbstractModule, Provides}
import com.mohiva.play.silhouette.api.repositories.AuthInfoRepository
import com.mohiva.play.silhouette.api.util.{PasswordHasher, PasswordHasherRegistry}
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import com.mohiva.play.silhouette.password.BCryptPasswordHasher
import com.mohiva.play.silhouette.persistence.repositories.DelegableAuthInfoRepository
import com.typesafe.config.Config
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Environment}
import services.PasswordInfoService
import slick.jdbc.JdbcBackend.Database

import scala.concurrent.{ExecutionContext, Future}

/**
  * This module handles the bindings for the API to the Slick implementation.
  *
  * https://www.playframework.com/documentation/latest/ScalaDependencyInjection#Programmatic-bindings
  */
class Module(environment: Environment,
             configuration: Configuration) extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[Database]).toProvider(classOf[DatabaseProvider])
    bind(classOf[PasswordHasher]).toInstance(new BCryptPasswordHasher)
    //    bind(classOf[DelegableAuthInfoDAO[PasswordInfo]]).to(classOf[PasswordInfoService])
    bind(classOf[UserDAO]).to(classOf[SlickUserDAO])
    bind(classOf[UserTokenDAO]).to(classOf[SlickUserTokenDAO])
    bind(classOf[DAOCloseHook]).asEagerSingleton()
  }

  @Provides
  def provideCredentialsProvider(authInfoRepository: AuthInfoRepository,
                                 passwordHasher: PasswordHasher)
                                (implicit ec: ExecutionContext): CredentialsProvider = {
    val passwordHasherRegistry = PasswordHasherRegistry(passwordHasher)
    new CredentialsProvider(authInfoRepository, passwordHasherRegistry)
  }

  @Provides
  def provideAuthInfoRepository(passwordInfoService: PasswordInfoService)
                               (implicit ec: ExecutionContext): AuthInfoRepository = {
    new DelegableAuthInfoRepository(passwordInfoService)
  }
}

@Singleton
class DatabaseProvider @Inject()(config: Config) extends Provider[Database] {
  lazy val get = Database.forConfig("myapp.database", config)
}

/** Closes database connections safely.  Important on dev restart. */
class DAOCloseHook @Inject()(userDAO: UserDAO, userTokenDAO: UserTokenDAO, lifecycle: ApplicationLifecycle) {
  lifecycle.addStopHook { () =>
    Future.successful {
      userDAO.close()
      userTokenDAO.close()
    }
  }
}
