package sbtmojoz

import org.tresql._

object Test {
  implicit val res = new Resources {}

  def test: Unit = {
    tresql"foo { id, name, demo(name), demo('text') }"
  }
}
