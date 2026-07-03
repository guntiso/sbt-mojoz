package sbtmojoz

import org.tresql._

object Test {
  implicit val res: Resources = new Resources {}

  def test: Unit = {
    tresql"foo { id, name, demo(name), demo('text') }"
    val a = tresql"foo {my_custom_macro(id, id)}"                         // macro from this project
    val b = tresql"foo {if_not(true, false)}"                             // macro from querease
    val c = tresql"foo {if_defined_or_else(:v?, 'd', 'e')}"               // macro from tresql
    val x = tresql"foo {my_custom_db_function(name, id)}"                 // signature from this project
    val y = tresql"foo {checked_resolve('foo-name', (foo {name}), 'wt')}" // signature from querease
    val z = tresql"foo {upper(name)}"                                     // signature from tresql
  }
}
