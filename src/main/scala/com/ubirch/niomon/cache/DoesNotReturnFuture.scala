package com.ubirch.niomon.cache

import scala.concurrent.Future


trait DoesNotReturnFuture[F]

object DoesNotReturnFuture {
  // we actually don't need any instances at runtime
  implicit def doesNotReturnFuture0[R](implicit ev: R <:!< Future[Any]): DoesNotReturnFuture[() => R] = null
  implicit def doesNotReturnFuture1[A, R](implicit ev: R <:!< Future[Any]): DoesNotReturnFuture[(A) => R] = null
  implicit def doesNotReturnFuture2[A, B, R](implicit ev: R <:!< Future[Any]): DoesNotReturnFuture[(A, B) => R] = null
  implicit def doesNotReturnFuture3[A, B, C, R](implicit ev: R <:!< Future[Any]): DoesNotReturnFuture[(A, B, C) => R] = null
  implicit def doesNotReturnFuture4[A, B, C, D, R](implicit ev: R <:!< Future[Any]): DoesNotReturnFuture[(A, B, C, D) => R] = null

  @scala.annotation.implicitNotFound("${A} must not be a subtype of ${B}")
  trait <:!<[A, B] extends Serializable

  implicit def nsub[A, B] : A <:!< B = null
  implicit def nsubAmbig1[A, B >: A] : A <:!< B = sys.error("unreachable")
  implicit def nsubAmbig2[A, B >: A] : A <:!< B = sys.error("unreachable")
}
