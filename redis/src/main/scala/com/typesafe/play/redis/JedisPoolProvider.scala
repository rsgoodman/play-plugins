package com.typesafe.play.redis

import java.net.URI

import javax.inject.{Inject, Provider, Singleton}
import org.apache.commons.lang3.builder.ReflectionToStringBuilder
import play.api.inject.ApplicationLifecycle
import play.api.{Configuration, Logger}
import redis.clients.jedis.{JedisPool, JedisPoolConfig}
import redis.clients.util.JedisURIHelper
import javax.net.ssl._
import java.security.cert.X509Certificate
import java.security.SecureRandom

import scala.concurrent.Future

@Singleton
class JedisPoolProvider @Inject()(config: Configuration, lifecycle: ApplicationLifecycle) extends Provider[JedisPool]{

  lazy val logger = Logger("redis.module")



  lazy val get: JedisPool = {
    val jedisPool = {
      val redisUri: Option[URI] = config.getString("redis.uri").map(new URI(_))

      val poolConfig = createPoolConfig(config)

      val timeout = config.getInt("redis.timeout")
        .getOrElse(2000)

      redisUri match {
        case Some(uri) =>

          // checking if SSL
          val isSSL = redisUri.map(x => JedisURIHelper.isRedisSSLScheme(x))
            .getOrElse(false)

          //Check if validating ssl
          val validateSSL = config.getBoolean("redis.validateSSL")
            .getOrElse(false)

          // Get all host validator
          val allHostsValid: HostnameVerifier = if (!validateSSL && isSSL) createHostManager else null
          // socket factory that will trust all certificates

          val sslSocketFactory: SSLSocketFactory = if (!validateSSL && isSSL) {
            val trustAllCerts = createTrustManager
            val sc = SSLContext.getInstance("SSL")
            sc.init(null, trustAllCerts, new SecureRandom)
            // Socker factory that uses Trust manager
            sc.getSocketFactory
          } else {
            null
          }

          val sslParameters = if (isSSL) new SSLParameters else null
          new JedisPool(poolConfig, uri, timeout, sslSocketFactory, sslParameters, allHostsValid)

        case None =>

          val host = config.getString("redis.host")
            .getOrElse("localhost")

          val port = config.getInt("redis.port")
            .getOrElse(6379)

          val password = config.getString("redis.password")
            .orNull

          val database = config.getInt("redis.database")
            .getOrElse(0)

          new JedisPool(poolConfig, host, port, timeout, password, database)

      }

    }

    logger.info("Starting Jedis Pool Provider")

    lifecycle.addStopHook(() => Future.successful {
      logger.info("Stopping Jedis Pool Provider")
      jedisPool.destroy()
    })

    jedisPool
  }

  private def createPoolConfig(config: Configuration): JedisPoolConfig = {
    val poolConfig: JedisPoolConfig = new JedisPoolConfig()
    config.getInt("redis.pool.maxIdle").foreach(poolConfig.setMaxIdle)
    config.getInt("redis.pool.minIdle").foreach(poolConfig.setMinIdle)
    config.getInt("redis.pool.maxTotal").foreach(poolConfig.setMaxTotal)
    config.getLong("redis.pool.maxWaitMillis").foreach(poolConfig.setMaxWaitMillis)
    config.getBoolean("redis.pool.testOnBorrow").foreach(poolConfig.setTestOnBorrow)
    config.getBoolean("redis.pool.testOnReturn").foreach(poolConfig.setTestOnReturn)
    config.getBoolean("redis.pool.testWhileIdle").foreach(poolConfig.setTestWhileIdle)
    config.getLong("redis.pool.timeBetweenEvictionRunsMillis").foreach(poolConfig.setTimeBetweenEvictionRunsMillis)
    config.getInt("redis.pool.numTestsPerEvictionRun").foreach(poolConfig.setNumTestsPerEvictionRun)
    config.getLong("redis.pool.minEvictableIdleTimeMillis").foreach(poolConfig.setMinEvictableIdleTimeMillis)
    config.getLong("redis.pool.softMinEvictableIdleTimeMillis").foreach(poolConfig.setSoftMinEvictableIdleTimeMillis)
    config.getBoolean("redis.pool.lifo").foreach(poolConfig.setLifo)
    config.getBoolean("redis.pool.blockWhenExhausted").foreach(poolConfig.setBlockWhenExhausted)
    poolConfig
  }

  private def createTrustManager(): Array[TrustManager] = {
    Array[TrustManager](new X509TrustManager() {
      def getAcceptedIssuers: Array[X509Certificate] = null

      def checkClientTrusted(certs: Array[X509Certificate], authType: String): Unit = {}

      def checkServerTrusted(certs: Array[X509Certificate], authType: String): Unit = {}
    })
  }

  private def createHostManager(): HostnameVerifier = {
    new HostnameVerifier() {
      def verify(hostname: String, session: SSLSession) = true
    }
  }
}
