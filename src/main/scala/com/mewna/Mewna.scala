package com.mewna

import java.util.Optional
import java.util.concurrent.{ExecutorService, Executors}

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.mewna.nats.{NatsServer, Q}
import com.mewna.twitch.TwitchWebhookClient
import com.mewna.twitch.ratelimit.TwitchRatelimiter
import com.redis.{RedisClient, RedisClientPool}
import org.slf4j.{Logger, LoggerFactory}

/**
 * @author amy
 * @since 05/02/2018.
 */
object Mewna {
  val mapper = new ObjectMapper()
  
  def main(args: Array[String]): Unit = {
    mapper.registerModule(DefaultScalaModule)
    new Mewna().run()
  }
}

class Mewna {
  val api = new API(this)
  val threadPool: ExecutorService = Executors.newCachedThreadPool()
  val twitchWebhookClient: TwitchWebhookClient = new TwitchWebhookClient(this)
  val twitchRatelimiter: TwitchRatelimiter = new TwitchRatelimiter(this)
  private val redisPool: RedisClientPool = new RedisClientPool(System.getenv("REDIS_HOST"), 6379,
    secret = Option[String](System.getenv("REDIS_PASS")))
  // val nats: NatsServer = new NatsServer(this)
  val q: Q = new Q(this)
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  
  private def run(): Unit = {
    // NOTE: For now we only care about Twitch
    // We can do other stuff later
    logger.info("Starting telepathy...")
    logger.info("Connecting to NATS...")
    // nats.connect()
    q.connect()
    logger.info("Starting API server...")
    api.startServer(Optional.ofNullable(System.getenv("API_PORT")).orElse("80").toInt)
    logger.info("Starting Twitch queue polling...")
    twitchRatelimiter.startPollingQueue()
    twitchWebhookClient.startHookRefresher()
    logger.info("Checking env...")
    val subscribes: String = System.getenv("subscribes")
    if(subscribes != null) {
      val ids = subscribes.split(",")
      ids.foreach(e => {
        twitchRatelimiter.queueSubscribe(TwitchWebhookClient.TOPIC_STREAM_UP_DOWN, e, (_, _) => {})
        twitchRatelimiter.queueSubscribe(TwitchWebhookClient.TOPIC_FOLLOWS, e, (_, _) => {})
      })
    }
    val unfollows: String = System.getenv("unfollows")
    if(unfollows != null) {
      val ids = unfollows.split(",")
      ids.foreach(e => {
        twitchRatelimiter.queueUnsubscribe(TwitchWebhookClient.TOPIC_FOLLOWS, e, (_, _) => {})
      })
    }
    logger.info("Done!")
    
    // TODO: Handle Twitch pubsub somehow
    /*
    val twitch = new TwitchPubsubClient(System.getenv("TWITCH_OAUTH").replaceAll("oauth:", ""))
    twitch.connect(() => {
      twitch.channelListen("136359927")
    })
    */
  }
  
  def redis(callback: RedisClient => Unit): Unit = {
    redisPool.withClient {
      client =>
        callback.apply(client)
    }
  }
}
