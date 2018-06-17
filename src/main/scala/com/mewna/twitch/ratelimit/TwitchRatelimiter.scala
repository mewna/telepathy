package com.mewna.twitch.ratelimit

import java.util.concurrent.{Future, TimeUnit}

import com.mewna.Mewna
import org.json.JSONObject
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable

/**
 * @author amy
 * @since 6/6/18.
 */
class TwitchRatelimiter(val mewna: Mewna) {
  private val HASH_KEY = "telepathy:twitch:ratelimiter"
  private val HASH_RATELIMIT_REMAINING = "ratelimit-remaining"
  private val HASH_RATELIMIT_LIMIT = "ratelimit-limit"
  private val HASH_RATELIMIT_RESET_TIME = "ratelimit-reset-time"
  
  private val USER_ID_CACHE_FORMAT = "telepathy:twitch:cache:user:%s"
  private val USER_NAME_CACHE_FORMAT = "telepathy:twitch:cache:user:name:%s"
  
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  
  private var queue = new mutable.Queue[(String, String, String, (Map[String, List[String]], JSONObject) => Unit)]()
  
  def queueSubscribe(topic: String, userId: String, callback: (Map[String, List[String]], JSONObject) => Unit = (_, _) => {}): Unit = {
    queue += (("subscribe", topic, userId, callback))
  }
  
  def queueUnsubscribe(topic: String, userId: String, callback: (Map[String, List[String]], JSONObject) => Unit = (_, _) => {}): Unit = {
    queue += (("unsubscribe", topic, userId, callback))
  }
  
  def queueLookupUser(userId: String, callback: (Map[String, List[String]], JSONObject) => Unit = (_, _) => {}): Unit = {
    // If we already have the user, we should just return them immediately rather than waiting in the queue
    mewna.redis(redis => {
      if(redis.exists(USER_ID_CACHE_FORMAT.format(userId))) {
        val res = new JSONObject(redis.get(USER_ID_CACHE_FORMAT.format(userId)).get)
        val outerHeaders: Map[String, List[String]] = null
        callback(outerHeaders, res)
      } else {
        queue += (("lookup", "", userId, callback))
      }
    })
  }
  
  def queueLookupUserName(userId: String, callback: (Map[String, List[String]], JSONObject) => Unit = (_, _) => {}): Unit = {
    // If we already have the user, we should just return them immediately rather than waiting in the queue
    mewna.redis(redis => {
      if(redis.exists(USER_NAME_CACHE_FORMAT.format(userId))) {
        val res = new JSONObject(redis.get(USER_NAME_CACHE_FORMAT.format(userId)).get)
        val outerHeaders: Map[String, List[String]] = null
        callback(outerHeaders, res)
      } else {
        queue += (("lookup:name", "", userId, callback))
      }
    })
  }
  
  def queueFutureLookupUserId(name: String): Future[JSONObject] = {
    mewna.threadPool.submit(() => {
      var (headers, data): (Map[String, List[String]], JSONObject) = (Map(), new JSONObject())
      queueLookupUser(name, (_headers, _data) => {
        headers = _headers
        data = _data
      })
      while(data.toString.equals("{}")) {
        Thread.sleep(50L)
      }
      data
    })
  }
  
  def queueFutureLookupUserName(name: String): Future[JSONObject] = {
    mewna.threadPool.submit(() => {
      var (headers, data): (Map[String, List[String]], JSONObject) = (Map(), new JSONObject())
      queueLookupUserName(name, (_headers, _data) => {
        headers = _headers
        data = _data
      })
      while(data.toString.equals("{}")) {
        Thread.sleep(50L)
      }
      data
    })
  }
  
  def startPollingQueue(): Unit = {
    mewna.threadPool.execute(() => {
      while(true) {
        try {
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
              logger.warn("Hit ratelimit ({} remaining), waiting until it expires...", ratelimitRemaining)
              // Sleep until we're ready
              try {
                Thread.sleep(60000L)
                mewna.redis(redis => redis.hset(HASH_KEY, HASH_RATELIMIT_REMAINING, 120))
                logger.info("Finished waiting!")
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
                mewna.redis(redis => {
                  val (headers, body) = mewna.twitchWebhookClient.getUserById(userId)
                  handleRatelimitHeaders(headers)
                  // Expire cache after a day
                  redis.set(USER_ID_CACHE_FORMAT.format(userId), body.toString())
                  redis.expire(USER_ID_CACHE_FORMAT.format(userId), 86400)
                  callback(headers, body)
                })
              case "lookup:name" =>
                mewna.redis(redis => {
                  val (headers, body) = mewna.twitchWebhookClient.getUserByName(userId)
                  handleRatelimitHeaders(headers)
                  if(body.toString().equals("{}") || (body.has("status") && body.getInt("status") == 400)) {
                    // :fire: :blobcatfireeyes:
                    callback(null: Map[String, List[String]], new JSONObject().put("status", "400"))
                  } else {
                    // Expire cache after a day
                    redis.set(USER_NAME_CACHE_FORMAT.format(userId), body.toString())
                    redis.expire(USER_NAME_CACHE_FORMAT.format(userId), 86400)
                    callback(headers, body)
                  }
                })
            }
          }
        } catch {
          case e: Exception => e.printStackTrace()
        }
      }
    })
  }
  
  private def handleRatelimitHeaders(headers: Map[String, List[String]]): Unit = {
    mewna.redis(redis => {
      // Grab the headers we care about
      val remaining = headers("ratelimit-remaining").head
      val limit = headers("ratelimit-limit").head
      val resetTime = if(headers.contains("ratelimit-reset-time")) {
        headers("ratelimit-reset-time").head
      } else {
        TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()) + 60
      }
      redis.hset(HASH_KEY, HASH_RATELIMIT_REMAINING, remaining + "")
      redis.hset(HASH_KEY, HASH_RATELIMIT_LIMIT, limit + "")
      redis.hset(HASH_KEY, HASH_RATELIMIT_RESET_TIME, resetTime + "")
    })
  }
}
