package com.ubirch.niomon.cache

import scala.concurrent.Future


trait ReturnsFuture[F] {
  type FutureRes
}

object ReturnsFuture {
  implicit def returnsFuture0[R]: ReturnsFuture[() => Future[R]] = new ReturnsFuture[() => Future[R]] {
    override type FutureRes = R
  }
  implicit def returnsFuture1[R]: ReturnsFuture[(_) => Future[R]] = new ReturnsFuture[(_) => Future[R]] {
    override type FutureRes = R
  }
  implicit def returnsFuture2[R]: ReturnsFuture[(_, _) => Future[R]] = new ReturnsFuture[(_, _) => Future[R]] {
    override type FutureRes = R
  }
  implicit def returnsFuture3[R]: ReturnsFuture[(_, _, _) => Future[R]] = new ReturnsFuture[(_, _, _) => Future[R]] {
    override type FutureRes = R
  }
  implicit def returnsFuture4[R]: ReturnsFuture[(_, _, _, _) => Future[R]] = new ReturnsFuture[(_, _, _, _) => Future[R]] {
    override type FutureRes = R
  }
}



