package sbtmojoz

import org.tresql._

package object test {
  implicit val res: Resources = new Resources {}
  val result = tresql"foo {id, name}"
}
