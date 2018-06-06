package com.mewna

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.mewna.twitch.TwitchPubsubClient
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
  val api = new API()
  private var redisPool: RedisClientPool = _
  
  private def run(): Unit = {
    api.startServer(System.getenv("API_PORT").toInt)
    redisPool = new RedisClientPool(System.getenv("REDIS_HOST"), 6379)
    
    // NOTE: For now we only care about Twitch
    // We can do other stuff later
    val twitch = new TwitchPubsubClient(System.getenv("TWITCH_OAUTH").replaceAll("oauth:", ""))
    twitch.connect(() => {
      twitch.channelListen("136359927")
    })
  }
  
  def redis(callback: RedisClient => Unit): Unit = {
    redisPool.withClient {
      client => callback.apply(client)
    }
  }
}
