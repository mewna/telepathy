package com.mewna

import java.util.concurrent.{ExecutorService, Executors}

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.mewna.nats.NatsServer
import com.mewna.twitch.TwitchWebhookClient
import com.mewna.twitch.ratelimit.TwitchRatelimiter
import com.redis.{RedisClient, RedisClientPool}

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
  private val redisPool: RedisClientPool = new RedisClientPool(System.getenv("REDIS_HOST"), 6379)
  private val nats: NatsServer = new NatsServer(this)
  
  private def run(): Unit = {
    // NOTE: For now we only care about Twitch
    // We can do other stuff later
    
    nats.connect()
    api.startServer(System.getenv("API_PORT").toInt)
    
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
      client => callback.apply(client)
    }
  }
}
