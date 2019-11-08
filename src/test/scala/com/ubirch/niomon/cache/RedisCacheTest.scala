package com.ubirch.niomon.cache

import org.scalatest.{FlatSpec, Matchers}

class RedisCacheTest extends FlatSpec with Matchers {
  "RedisCache API" should "be type safe" in {
    assertDoesNotCompile("""
      import scala.concurrent.Future
      import scala.concurrent.ExecutionContext.Implicits.global
      val rc = new RedisCache("test", ???)
      rc.cached(() => Future.successful(1)).buildCache("test", (x: Int) => true)
    """)

    assertCompiles("""
      val rc = new RedisCache("test", ???)
      rc.cached(() => 1).buildCache("test", (x: Int) => true)
    """)

    assertCompiles("""
      import scala.concurrent.Future
      import scala.concurrent.ExecutionContext.Implicits.global
      val rc = new RedisCache("test", ???)
      rc.cachedF(() => Future.successful(1)).buildCache("test", (x: Int) => true)
    """)

    assertDoesNotCompile("""
      val rc = new RedisCache("test", ???)
      rc.cachedF(() => 1).buildCache("test", (x: Int) => true)
    """)
  }
}
