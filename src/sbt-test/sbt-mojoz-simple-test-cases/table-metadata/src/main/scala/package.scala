package sbtmojoz

import org.tresql._

package object test {
  implicit val res = new Resources {}
  val result = tresql"foo {id, name}"
  val test_f = tresql"foo {my_custom_db_function(name, id) x}"
}
