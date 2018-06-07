package com.mewna.twitch.ratelimit

import java.util.concurrent.TimeUnit

import com.mewna.Mewna
import org.json.JSONObject

import scala.collection.mutable

/**
 * @author amy
 * @since 6/6/18.
 */
class TwitchRatelimiter(val mewna: Mewna) {
  private val HASH_KEY = "telepathy-twitch-ratelimiter"
  private val HASH_RATELIMIT_REMAINING = "ratelimit-remaining"
  private val HASH_RATELIMIT_LIMIT = "ratelimit-limit"
  private val HASH_RATELIMIT_RESET_TIME = "ratelimit-reset-time"
  
  private var queue = new mutable.Queue[(String, String, String, (Map[String, List[String]], JSONObject) => Unit)]()
  
  def queueSubscribe(topic: String, userId: String, callback: (Map[String, List[String]], JSONObject) => Unit = (_, _) => {}): Unit = {
    queue += (("subscribe", topic, userId, callback))
  }
  
  def queueUnsubscribe(topic: String, userId: String, callback: (Map[String, List[String]], JSONObject) => Unit = (_, _) => {}): Unit = {
    queue += (("unsubscribe", topic, userId, callback))
  }
  
  def startPollingQueue(): Unit = {
    mewna.threadPool.execute(() => {
      while(true) {
        if(queue.isEmpty) {
          // If we have nothing in the queue, wait a bit and check again
          try {
            Thread.sleep(50L)
          } catch {
            case e: InterruptedException => e.printStackTrace()
          }
        } else {
          // Check if we can
          var ratelimitRemaining = 0
          mewna.redis(redis => {
            val string = redis.hget(HASH_KEY, HASH_RATELIMIT_REMAINING)
            if(string.isEmpty) {
              // We have no ratelimit data, so go
              ratelimitRemaining = 120
            } else {
              // Obey the ratelimits
              ratelimitRemaining = string.get.toInt
            }
          })
          // Just play it safe
          if(ratelimitRemaining < 5) {
            // Sleep until we're ready
            var timeSeconds = 60 // If we can't get data for w/e reason, wait a minute
            mewna.redis(redis => {
              val string = redis.hget(HASH_KEY, HASH_RATELIMIT_RESET_TIME)
              if(string.isDefined) {
                timeSeconds = string.get.toInt
              }
            })
            try {
              Thread.sleep(TimeUnit.SECONDS.toMillis(timeSeconds + 1000 - TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())))
            } catch {
              case e: InterruptedException => e.printStackTrace()
            }
          }
          // pop the next thing off the queue and go
          val (mode, topic, userId, callback) = queue.dequeue()
          mode match {
            case "subscribe" =>
              val (headers, body) = mewna.twitchWebhookClient.subscribe(topic, userId, leaseSeconds = 864000)
              handleRatelimitHeaders(headers)
              callback(headers, body)
            case "unsubscribe" =>
              val (headers, body) = mewna.twitchWebhookClient.subscribe(topic, userId, leaseSeconds = 864000)
              handleRatelimitHeaders(headers)
              callback(headers, body)
            case "lookup" =>
              val (headers, body) = mewna.twitchWebhookClient.getUserById(userId)
              handleRatelimitHeaders(headers)
              callback(headers, body)
          }
        }
      }
    })
  }
  
  private def handleRatelimitHeaders(headers: Map[String, List[String]]): Unit = {
    mewna.redis(redis => {
      // Grab the headers we care about
      val remaining = headers("ratelimit-remaining").head
      val limit = headers("ratelimit-limit").head
      val resetTime = headers("ratelimit-reset-time").head
      redis.hset(HASH_KEY, HASH_RATELIMIT_REMAINING, remaining + "")
      redis.hset(HASH_KEY, HASH_RATELIMIT_LIMIT, limit + "")
      redis.hset(HASH_KEY, HASH_RATELIMIT_RESET_TIME, resetTime + "")
    })
  }
}
