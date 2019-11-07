package com.ubirch.niomon.cache

import java.util.concurrent.TimeUnit

import com.typesafe.config.{ConfigRenderOptions, Config => TConfig}
import com.typesafe.scalalogging.StrictLogging
import org.nustaq.serialization.FSTConfiguration
import org.redisson.Redisson
import org.redisson.api.{RMapCache, RedissonClient}
import org.redisson.codec.FstCodec

import scala.util.Try

class RedisCache(appConfig: TConfig) extends StrictLogging {
  import RedisCache._

  var caches: Vector[RMapCache[_, _]] = Vector()

  def purgeCaches(): Unit = {
    logger.info("purging redis caches")
    caches.foreach(_.clear())
    logger.debug(s"cache sizes after purging: [${caches.map(c => c.getName + " => " + c.size()).mkString("; ")}]")
  }

  val redisson: RedissonClient = Redisson.create({
    val conf = Try(appConfig.getConfig("redisson").root().render(ConfigRenderOptions.concise()))
      .map(org.redisson.config.Config.fromJSON)
      .getOrElse(new org.redisson.config.Config())

    // force the FST serializer to use serialize everything, because we sometimes want to store POJOs which
    // aren't `Serializable`
    if (conf.getCodec == null || conf.getCodec.isInstanceOf[FstCodec]) {
      conf.setCodec(new FstCodec(FSTConfiguration.createDefaultConfiguration().setForceSerializable(true)))
    }

    conf
  })

  //noinspection TypeAnnotation
  // This cache API is split in two steps (`cached(_).buildCache(_)`) to make type inference happy.
  // Originally it was just `cached(name)(function)`, but when `shouldCache` parameter was added after the `name`,
  // it screwed up type inference, because it was lexically before the `function`. And it is the `function` that has
  // the correct types for the type inference
  def cached[F](f: F)(implicit F: TupledFunction[F]) = new CacheBuilder[F, F.TupledInput, F.Output](f) {
    override implicit def inputIsI =
    // kinda hackish, but if I do `implicitly`, I get an infinite loop
      =:=.tpEquals[Any].asInstanceOf[tupledFunction.TupledInput =:= F.TupledInput]

    override implicit def outputIsO =
    // kinda hackish, but if I do `implicitly`, I get an infinite loop
      =:=.tpEquals[Any].asInstanceOf[tupledFunction.Output =:= F.Output]
  }

  // I and O are here just to make type inference possible. I == tupledFunction.TupledInput and O == tupledFunction.Output
  abstract class CacheBuilder[F, I, O] private[RedisCache](f: F)(implicit val tupledFunction: TupledFunction[F]) {
    private val tupledF = tupledFunction.tupled(f)

    implicit def inputIsI: tupledFunction.TupledInput =:= I

    implicit def outputIsO: tupledFunction.Output =:= O

    // for some reason, this doesn't really work with arbitrary key types, so we always use strings for keys
    def buildCache(
      name: String,
      shouldCache: O => Boolean = { _ => true }
    )(implicit
      cacheKey: CacheKey[I]
    ): F = {
      val cache = redisson.getMapCache[String, tupledFunction.Output](name)

      logger.debug("registering new cache")
      caches :+= cache
      logger.debug(s"caches in total: ${caches.size}")

      val ttl = appConfig.getDuration(s"$name.timeToLive")
      val maxIdleTime = appConfig.getDuration(s"$name.maxIdleTime")

      tupledFunction.untupled { x: tupledFunction.TupledInput =>
        val (res, time) = measureTime {
          val key = cacheKey.key(x)
          val res = cache.get(key)

          if (res != null) {
            logger.debug(s"cache hit in [$name] for key [$key]")
            res
          } else {
            logger.debug(s"cache miss in [$name] for key [$key]")
            val freshRes = tupledF(x)
            if (shouldCache(freshRes)) {
              cache.fastPut(key, freshRes, ttl.toNanos, TimeUnit.NANOSECONDS, maxIdleTime.toNanos, TimeUnit.NANOSECONDS)
            }
            freshRes
          }
        }
        logger.debug(s"cache lookup in [$name] took $time ns (~${Math.round(time / 1000000.0)} ms)")
        res
      }
    }
  }

}

object RedisCache {
  trait CacheKey[T] {
    def key(x: T): String
  }

  object CacheKey {
    implicit def toStringKey[T]: CacheKey[T] = (x: T) => x.toString
  }

  private def measureTime[R](code: => R, t: Long = System.nanoTime): (R, Long) = (code, System.nanoTime - t)
}
