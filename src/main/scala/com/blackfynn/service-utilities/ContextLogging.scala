package com.blackfynn.service.utilities

import akka.http.scaladsl.model.StatusCode
import com.typesafe.scalalogging.{ CanLog, Logger, LoggerTakingImplicit }
import org.slf4j.MDC

import scala.annotation.implicitAmbiguous
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

/**
  * The ContextLogger is used to log all additional context where available in the
  * expected log format. If no `LogContext` is available the no context logger should be
  * used to log.
  *
  * The ContextLogger should always be used in conjunction with the below logback configuration.
  *
  * <configuration>
      <appender name="Console" class="ch.qos.logback.core.ConsoleAppender">
          <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
              <providers>
                  <timestamp><fieldName>timestamp</fieldName></timestamp>
                  <message/>
                  <loggerName><fieldName>logger</fieldName></loggerName>
                  <logLevel><fieldName>logLevel</fieldName></logLevel>
                  <callerData/>
                  <throwableClassName/>
                  <throwableRootCauseClassName/>
                  <stackTrace/>
                  <mdc><fieldName>blackfynn</fieldName></mdc>
              </providers>
          </encoder>
      </appender>

      <logger name="com.blackfynn" level="DEBUG" />

      <root level="INFO">
        <appender-ref ref="Console" />
      </root>
    </configuration>
  *
  */
final class ContextLogger {
  import ContextLogger._

  val context: LoggerTakingImplicit[LogContext] =
    Logger.takingImplicit(classOf[LogContext])(CanLogLogContext)

  @implicitAmbiguous(
    """Multiples tiers are available. In order to use a Tier[SomeType], specify tierContext[SomeType]"""
  )
  def tierContext[A](implicit tier: Tier[A]): LoggerTakingImplicit[LogContext] =
    Logger.takingImplicit(classOf[LogContext])(CanLogLogContextWithTier(tier))

  val noContext: Logger =
    Logger(s"${this.getClass.getPackage.getName}.NoContext")

  @implicitAmbiguous(
    """Multiples tiers are available. In order to use a Tier[SomeType], specify tierNoContext[SomeType]"""
  )
  def tierNoContext[A](implicit tier: Tier[A]): LoggerTakingImplicit[Tier[A]] =
    Logger.takingImplicit(classOf[LogContext])(CanLogTier(tier))
}

object ContextLogger {
  implicit case object CanLogLogContext extends CanLog[LogContext] {

    override def logMessage(originalMsg: String, context: LogContext): String = {
      context.values
        .foreach {
          case (key, value) => MDC.put(key, value)
        }

      originalMsg
    }

    override def afterLog(context: LogContext): Unit =
      context.values
        .foreach {
          case (key, _) => MDC.remove(key)
        }
  }

  implicit class CanLogLogContextWithTier[A](tier: Tier[A]) extends CanLog[LogContext] {
    override def logMessage(originalMsg: String, context: LogContext): String = {
      MDC.put("tier", tier.name)
      context.values
        .foreach {
          case (key, value) => MDC.put(key, value)
        }

      originalMsg
    }

    override def afterLog(context: LogContext): Unit = {
      MDC.remove("tier")
      context.values
        .foreach {
          case (key, _) => MDC.remove(key)
        }
    }
  }

  implicit class CanLogTier[A](tier: Tier[A]) extends CanLog[Tier[A]] {
    override def logMessage(originalMsg: String, tier: Tier[A]): String = {
      MDC.put("tier", tier.name)

      originalMsg
    }

    override def afterLog(tier: Tier[A]): Unit = {
      MDC.remove("tier")
    }
  }
}

/**
  * A log context is used in conjunction with the `ContextLogger` to add additional
  * context to log messages. In order to be properly formatted for ingest in to
  * our log aggregation systems we expect a specific JSON structure.
  *
  * tier represents the application specific context in which the log message is being created.
  * An example of this would be in a SampleService we might have a an area of the codebase
  * that deals with creating samples our tier might then be "creation".
  */
trait LogContext {

  val values: Map[String, String]

  /**
    * Used to find and construct a map of values contained in the inheriting
    * case class. This function is only usable for case classes.
    *
    * NB This function will NOT work inside of nested case class. Any case class
    * that uses this function must not be inside of any wrapper.
    *
    * e.g.
    * ```
    * case class SampleLogContext(other: String, thing: Int) extends LogContext {
    *   override val values: Map[String, String] = inferValues(this)
    * }
    * ```
    *
    * @param a should always be `this` called inside a case class
    * @tparam A the type of the services log context
    * @return All the instantiated values in the log context as a map from name of the value to it's value
    */
  def inferValues[A: ClassTag: TypeTag](a: A): Map[String, String] = {
    val inferredType = typeOf[A]
    val reflectedClass = runtimeMirror(inferredType.getClass.getClassLoader)

    // Find all case class accessor methods. Extract the name of the method and the value.
    inferredType.members
      .collect {
        case methodSymbol: MethodSymbol if methodSymbol.isCaseAccessor =>
          methodSymbol
      }
      .toList
      .flatMap { methodSymbol =>
        val isOption = methodSymbol.returnType <:< typeOf[Option[_]]

        val value = reflectedClass.reflect(a).reflectMethod(methodSymbol).apply()
        val valueName = methodSymbol.name.decodedName.toString

        if (isOption) {
          value
            .asInstanceOf[Option[Any]]
            .map { actualValue =>
              valueName -> actualValue.toString
            }
        } else Some(valueName -> value.toString)
      }
      .toMap
  }
}

/**
  * A tier represents a logical area of an application. A Tier can
  * be used with the `ContextLogger.contextTier` method to allow
  * an implicit Tier to be added to the log output
  *
  * @tparam A the type associated with the area of the application
  */
trait Tier[+A] {
  val name: String
}

object Tier {
  def apply[A: TypeTag]: Tier[A] =
    new Tier[A] {
      override val name: String =
        typeOf[A].typeSymbol.name.decodedName.toString
    }

  def apply[A](_name: String): Tier[A] =
    new Tier[A] {
      override val name: String = _name
    }
}

/**
  * For logging responses primarily in generated servers ie Guardrail based servers
  *
  * @param name The name of  the Tier
  * @param failMessage The message to be used in the event that a response has a failing status code
  * @param successMessage The message to be used for a successful status code
  * @tparam A the type associated with the area of the application
  */
final case class ResponseTier[+A](name: String, failMessage: String, successMessage: String)
    extends Tier[A]

object ResponseLogger {
  type Status = { val statusCode: StatusCode }

  import scala.language.reflectiveCalls

  def logResponse[A <: Status](
    response: A
  )(implicit
    logContext: LogContext,
    log: ContextLogger,
    tier: ResponseTier[A]
  ): A = {
    if (response.statusCode.isFailure)
      log.tierContext.error(s"${tier.failMessage} ${response.statusCode}")
    else
      log.tierContext.info(s"${tier.successMessage} ${response.statusCode}")

    response
  }

  def logResponse[A <: Status](
    response: A,
    msg: String
  )(implicit
    log: ContextLogger,
    logContext: LogContext,
    tier: ResponseTier[A]
  ): A = {
    if (response.statusCode.isFailure)
      log.tierContext.error(s"${tier.failMessage} ${response.statusCode}: $msg")
    else
      log.tierContext.info(s"${tier.successMessage} ${response.statusCode}: $msg")

    response
  }
}
