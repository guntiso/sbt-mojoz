package sbtmojoz

package object test {
  trait Dto {}
  trait DtoWithId extends Dto {
    def id: java.lang.Long
    def id_=(id: java.lang.Long)
  }
}
