package com.mewna.util

/**
 * @author amy
 * @since 5/5/18.
 */
object Helpers {
  
  // https://docs.scala-lang.org/overviews/core/implicit-classes.html
  /**
   * Wraps a value to provide the |> operator
   *
   * @param value The value being wrapped
   * @tparam F Type
   */
  implicit class Pipeline[F](val value: F) extends AnyVal {
    def |>[G](f: F => G) = f(value)
  }
}
