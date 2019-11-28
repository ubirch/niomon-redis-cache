package com.ubirch.niomon.cache

import java.util.concurrent.TimeUnit

import com.typesafe.config.{ConfigRenderOptions, Config => TConfig}
import com.typesafe.scalalogging.StrictLogging
import org.nustaq.serialization.FSTConfiguration
import org.redisson.Redisson
import org.redisson.api.{RMapCache, RedissonClient}
import org.redisson.codec.FstCodec
import org.redisson.config.Config

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.collection.JavaConverters._

class RedisCache(appName: String, appConfig: TConfig) extends StrictLogging {

  import RedisCache._

  var caches: Vector[RMapCache[_, _]] = Vector()

  def purgeCaches(): Unit = {
    logger.info("purging redis caches")
    caches.foreach(_.clear())
    logger.debug(s"cache sizes after purging: [${caches.map(c => c.getName + " => " + c.size()).mkString("; ")}]")
  }

  val redisson: RedissonClient = {
    val redissonSection = appConfig.getConfig("redisson")
    val isNewConfigFormat = redissonSection.hasPath("main") && redissonSection.hasPath("fallbacks")

    val rawConfigsWithNames = if (isNewConfigFormat) {
      (redissonSection.getConfig("main"), "main") ::
        redissonSection.getConfigList("fallbacks").asScala.toList.zipWithIndex.map {
          case (conf, idx) => (conf, s"fallback #$idx")
        }
    } else {
      logger.warn("using legacy redis-cache config format")
      List((redissonSection, "main-legacy"))
    }

    val redissonConfigsWithNames = rawConfigsWithNames.map { case (rawConfig, name) =>
      val c = Config.fromJSON(rawConfig.root().render(ConfigRenderOptions.concise()))
      // force the FST serializer to use serialize everything, because we sometimes want to store POJOs which
      // aren't `Serializable`
      if (c.getCodec == null || c.getCodec.isInstanceOf[FstCodec]) {
        c.setCodec(new FstCodec(FSTConfiguration.createDefaultConfiguration().setForceSerializable(true)))
      }

      (c, name)
    }

    var successfullyConnectedRedisson: RedissonClient = null
    var lastError: Throwable = null
    val configIterator = redissonConfigsWithNames.iterator

    while (successfullyConnectedRedisson == null && configIterator.hasNext) {
      val (currentConfig, configName) = configIterator.next()
      val tryRedisson = Try {
        logger.debug(s"trying redisson config $configName")
        val r = Redisson.create(currentConfig)
        // ask redis about something to prove that this config works
        val keys = r.getKeys
        logger.debug(s"connected to redis instance with ${keys.count()} keys")

        r
      }

      tryRedisson match {
        case Success(r) =>
          logger.info(s"successfully connected to redis using config $configName")
          successfullyConnectedRedisson = r
        case Failure(exception) =>
          logger.debug(s"failed to connect to redis using config $configName", exception)
          lastError = exception
          logger.debug(s"retrying with next fallback config...")
      }
    }

    if (successfullyConnectedRedisson == null) {
      logger.error(s"tried all the configs, but couldn't connect to redis; last error:", lastError)
      throw new Exception("No valid config for redis", lastError)
    }

    successfullyConnectedRedisson
  }

  // This cache API is split in two steps (`cached(_).buildCache(_)`) to make type inference happy.
  // Originally it was just `cached(name)(function)`, but when `shouldCache` parameter was added after the `name`,
  // it screwed up type inference, because it was lexically before the `function`. And it is the `function` that has
  // the correct types for the type inference
  /** this DOES NOT SUPPORT functions returning futures, for that use cachedF */
  //noinspection TypeAnnotation
  def cached[F](f: F)(implicit F: TupledFunction[F], ev: DoesNotReturnFuture[F]) = new CacheBuilder[F, F.TupledInput, F.Output](f) {
    override implicit def inputIsI =
    // kinda hackish, but if I do `implicitly`, I get an infinite loop
      =:=.tpEquals[Any].asInstanceOf[tupledFunction.TupledInput =:= F.TupledInput]

    override implicit def outputIsO =
    // kinda hackish, but if I do `implicitly`, I get an infinite loop
      =:=.tpEquals[Any].asInstanceOf[tupledFunction.Output =:= F.Output]
  }

  /** like cached, but understands Futures */
  //noinspection TypeAnnotation
  def cachedF[F](f: F)(implicit F: TupledFunction[F], ev: ReturnsFuture[F]) = new FutureCacheBuilder[F, F.TupledInput, ev.FutureRes](f) {
    override implicit def inputIsI =
    // kinda hackish, but if I do `implicitly`, I get an infinite loop
      =:=.tpEquals[Any].asInstanceOf[tupledFunction.TupledInput =:= F.TupledInput]

    override implicit def outputIsFutureO =
    // kinda hackish, but if I do `implicitly`, I get an infinite loop
      =:=.tpEquals[Any].asInstanceOf[tupledFunction.Output =:= Future[ev.FutureRes]]
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

      val ttl = appConfig.getDuration(s"$appName.$name.timeToLive")
      val maxIdleTime = appConfig.getDuration(s"$appName.$name.maxIdleTime")

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

  // I and O are here just to make type inference possible. I == tupledFunction.TupledInput and O == tupledFunction.Output
  abstract class FutureCacheBuilder[F, I, O] private[RedisCache](f: F)(implicit val tupledFunction: TupledFunction[F], val returnsFuture: ReturnsFuture[F]) {
    private val tupledF = tupledFunction.tupled(f)

    implicit def inputIsI: tupledFunction.TupledInput =:= I

    implicit def outputIsFutureO: tupledFunction.Output =:= Future[O]

    implicit def futureOIsOutput: Future[O] =:= tupledFunction.Output = outputIsFutureO.asInstanceOf[Future[O] =:= tupledFunction.Output]

    // for some reason, this doesn't really work with arbitrary key types, so we always use strings for keys
    def buildCache(
      name: String,
      shouldCache: O => Boolean = { _ => true }
    )(implicit
      cacheKey: CacheKey[I],
      ec: ExecutionContext
    ): F = {
      val cache = redisson.getMapCache[String, O](name)

      logger.debug("registering new cache")
      caches :+= cache
      logger.debug(s"caches in total: ${caches.size}")

      val ttl = appConfig.getDuration(s"$appName.$name.timeToLive")
      val maxIdleTime = appConfig.getDuration(s"$appName.$name.maxIdleTime")

      tupledFunction.untupled { x: tupledFunction.TupledInput =>
        val key = cacheKey.key(x)
        val res = cache.get(key)

        if (res != null) {
          logger.debug(s"cache hit in [$name] for key [$key]")
          Future.successful(res)
        } else {
          logger.debug(s"cache miss in [$name] for key [$key]")
          val freshResF: Future[O] = tupledF(x)
          freshResF.map { freshRes =>
            if (shouldCache(freshRes)) {
              cache.fastPut(key, freshRes, ttl.toNanos, TimeUnit.NANOSECONDS, maxIdleTime.toNanos, TimeUnit.NANOSECONDS)
            }
            freshRes
          }
        }

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
