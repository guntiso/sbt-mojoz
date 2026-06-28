package sbtmojoz

import org.tresql._

package object test {
  implicit val res = new Resources {}
  val result = tresql"foo {id, name}"
}
