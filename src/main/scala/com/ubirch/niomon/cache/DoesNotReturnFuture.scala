package com.ubirch.niomon.cache

import scala.concurrent.Future

/**
 * Typeclass which is implemented for functions which DO NOT return a [[scala.concurrent.Future]].
 * Currently instances are provided for any function which takes up to four arguments.
 */
trait DoesNotReturnFuture[F]

object DoesNotReturnFuture {
  // we actually don't need any instances at runtime
  implicit def doesNotReturnFuture0[R](implicit ev: R <:!< Future[Any]): DoesNotReturnFuture[() => R] = null
  implicit def doesNotReturnFuture1[A, R](implicit ev: R <:!< Future[Any]): DoesNotReturnFuture[(A) => R] = null
  implicit def doesNotReturnFuture2[A, B, R](implicit ev: R <:!< Future[Any]): DoesNotReturnFuture[(A, B) => R] = null
  implicit def doesNotReturnFuture3[A, B, C, R](implicit ev: R <:!< Future[Any]): DoesNotReturnFuture[(A, B, C) => R] = null
  implicit def doesNotReturnFuture4[A, B, C, D, R](implicit ev: R <:!< Future[Any]): DoesNotReturnFuture[(A, B, C, D) => R] = null

  // This is a funny one. Below are defined several equally likely implicits that match when B >: A. Compiler detects
  // the ambiguity and reports an error. If B is not a supertype of A, then there's no ambiguity, because only one
  // implicit matches.
  @scala.annotation.implicitNotFound("${A} must not be a subtype of ${B}")
  trait <:!<[A, B] extends Serializable

  object <:!< {
    implicit def nsub[A, B]: A <:!< B = null

    implicit def nsubAmbig1[A, B >: A]: A <:!< B = sys.error("unreachable")

    implicit def nsubAmbig2[A, B >: A]: A <:!< B = sys.error("unreachable")
  }
}
