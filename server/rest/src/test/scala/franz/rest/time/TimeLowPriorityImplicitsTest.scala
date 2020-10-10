package franz.rest.time

import franz.rest.BaseTest

class TimeLowPriorityImplicitsTest extends BaseTest with TimeLowPriorityImplicits {

  "TimeLowPriorityImplicits.asDate" should {
    "pimp strings w/ asDate" in {
      val Some(before) = "yesterday".asDate()
      val Some(now) = "now".asDate()
      val Some(after) = "tomorrow".asDate()
      now.isBefore(after) shouldBe true
      now.isAfter(before) shouldBe true

    }
  }
}
