package com.ubirch.niomon.cache

import org.scalatest.{FlatSpec, Matchers}

class RedisCacheTest extends FlatSpec with Matchers {
  "RedisCache API" should "be type safe" in {
    assertDoesNotCompile("""
      import scala.concurrent.Future
      val rc = new RedisCache("test", ???)
      rc.cached(() => Future.successful(1))
    """)

    assertCompiles("""
      val rc = new RedisCache("test", ???)
      rc.cached(() => 1)
    """)

    assertCompiles("""
      import scala.concurrent.Future
      val rc = new RedisCache("test", ???)
      rc.cachedF(() => Future.successful(1))
    """)

    assertDoesNotCompile("""
      val rc = new RedisCache("test", ???)
      rc.cachedF(() => 1)
    """)
  }
}
