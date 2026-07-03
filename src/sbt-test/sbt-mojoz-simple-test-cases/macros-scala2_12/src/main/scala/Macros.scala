package sbtmojoz.macros

import org.mojoz.querease.QuereaseMacros
import org.tresql.{Expr, QueryBuilder}

object Macros extends QuereaseMacros {
  def demo(b: QueryBuilder, expr: Expr): Expr = expr
}
