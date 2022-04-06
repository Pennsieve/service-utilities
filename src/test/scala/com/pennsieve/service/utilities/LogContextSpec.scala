package com.pennsieve.service.utilities

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class LogContextSpec extends AnyWordSpec with Matchers {

  "A log context" should {
    "be able to derive all values" in {

      val logContext = SampleLogContext("oh boy", 1)

      val expectedValues = Map("other" -> "oh boy", "thing" -> "1").toList

      logContext.values.toList should contain theSameElementsAs expectedValues
    }

    "where values are optional only derive values that exist" in {
      val logContext = OptionalSampleLogContext(Some("oh boy"))

      logContext.values.toList should contain only ("maybeOther" -> "oh boy")
    }
  }

  "A tier for a class should be derived from the name of the class" in {
    Tier[SampleLogContext].name shouldBe "SampleLogContext"
  }
}

case class SampleLogContext(other: String, thing: Int) extends LogContext {
  override val values: Map[String, String] = inferValues(this)
}

case class OptionalSampleLogContext(maybeOther: Option[String], maybeThing: Option[Int] = None)
    extends LogContext {
  override val values: Map[String, String] = inferValues(this)
}
