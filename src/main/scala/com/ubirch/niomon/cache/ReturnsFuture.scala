package com.ubirch.niomon.cache

import scala.concurrent.Future

/**
 * Typeclass which is implemented for functions which return [[scala.concurrent.Future]]s.
 * Currently instances are provided for any function which takes up to four arguments.
 */
trait ReturnsFuture[F] {
  type FutureRes
}

//noinspection TypeAnnotation
object ReturnsFuture {
  implicit def returnsFuture0[R] = new ReturnsFuture[() => Future[R]] {
    override type FutureRes = R
  }

  implicit def returnsFuture1[A, R] = new ReturnsFuture[(A) => Future[R]] {
    override type FutureRes = R
  }

  implicit def returnsFuture2[A, B, R] = new ReturnsFuture[(A, B) => Future[R]] {
    override type FutureRes = R
  }

  implicit def returnsFuture3[A, B, C, R] = new ReturnsFuture[(A, B, C) => Future[R]] {
    override type FutureRes = R
  }

  implicit def returnsFuture4[A, B, C, D, R] = new ReturnsFuture[(A, B, C, D) => Future[R]] {
    override type FutureRes = R
  }
}



