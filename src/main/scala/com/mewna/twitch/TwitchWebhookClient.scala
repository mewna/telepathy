package com.mewna.twitch

import java.util.concurrent.TimeUnit

import com.mewna.Mewna
import okhttp3.{MediaType, OkHttpClient, Request, RequestBody}
import org.json.JSONObject
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConverters._

object TwitchWebhookClient {
  val WEBHOOK_HUB: String = "https://api.twitch.tv/helix/webhooks/hub"
  val JSON: MediaType = MediaType.parse("application/json; charset=utf-8")
  
  /**
   * Format string. Use like this:
   * {{{ TOPIC_FOLLOWS.format(userId) }}}
   */
  val TOPIC_FOLLOWS: String = "https://api.twitch.tv/helix/users/follows?first=1&to_id=%s"
  /**
   * Format string. Use like this:
   * {{{ TOPIC_STREAM_UP_DOWN.format(userId) }}}
   */
  val TOPIC_STREAM_UP_DOWN: String = "https://api.twitch.tv/helix/streams?user_id=%s"
  val GET_USERS = "https://api.twitch.tv/helix/users"
}

/**
 * @author amy
 * @since 6/5/18.
 */
final class TwitchWebhookClient(val mewna: Mewna) {
  private val client = new OkHttpClient.Builder().build()
  private val WEBHOOK_STORE = "telepathy:webhook:store"
  val logger: Logger = LoggerFactory.getLogger(getClass)
  
  def startHookRefresher(): Unit = {
    mewna.threadPool.execute(() => {
      while(true) {
        try {
          // Check for soon-to-die hooks
          mewna.redis(redis => {
            val hookMap = redis.hgetall1(WEBHOOK_STORE)
            if(hookMap.isDefined) {
              val map = hookMap.get
              map.toSeq.map(x => (x._1, x._2.toInt)).filter(x => System.currentTimeMillis() - x._2 <= 86400000).foreach(x => {
                val (idMode, _) = x
                val strings = idMode.split(":")
                val id = strings(0)
                val mode = strings(1)
                val topic = mode match {
                  case "follows" => TwitchWebhookClient.TOPIC_FOLLOWS
                  case "streams" => TwitchWebhookClient.TOPIC_STREAM_UP_DOWN
                }
                mewna.twitchRatelimiter.queueSubscribe(topic, id, (_, _) => {
                  logger.info("Resubscribed to {} notifications for {}", mode: Any, id: Any)
                })
              })
            }
          })
          // Wait a bit and start over
          try {
            Thread.sleep(300000)
          } catch {
            case e: InterruptedException => logger.warn("{}", e)
          }
        } catch {
          case e: Exception => e.printStackTrace()
        }
      }
    })
  }
  
  def subscribe(topic: String, userId: String, leaseSeconds: Int = 0, cache: Boolean = true): (Map[String, List[String]], JSONObject) = {
    updateHook("subscribe", topic, userId, leaseSeconds, cache)
  }
  
  def unsubscribe(topic: String, userId: String, leaseSeconds: Int = 0, cache: Boolean = true): (Map[String, List[String]], JSONObject) = {
    updateHook("unsubscribe", topic, userId, leaseSeconds, cache)
  }
  
  def updateHook(mode: String, topic: String, userId: String, leaseSeconds: Int = 0, cache: Boolean = true): (Map[String, List[String]], JSONObject) = {
    val callback = topic match {
      case TwitchWebhookClient.TOPIC_FOLLOWS => System.getenv("DOMAIN") + "/api/v1/twitch/follows/" + userId
      case TwitchWebhookClient.TOPIC_STREAM_UP_DOWN => System.getenv("DOMAIN") + "/api/v1/twitch/streams/" + userId
    }
    val hookMode = topic match {
      case TwitchWebhookClient.TOPIC_FOLLOWS => "follows"
      case TwitchWebhookClient.TOPIC_STREAM_UP_DOWN => "streams"
    }
    val data = new JSONObject()
      .put("hub.callback", callback)
      .put("hub.mode", hookMode)
      .put("hub.topic", topic.format(userId))
      .put("hub.lease_seconds", leaseSeconds)
      .put("hub.secret", "") // TODO
    val res = client.newCall(new Request.Builder().url(TwitchWebhookClient.WEBHOOK_HUB)
      .post(RequestBody.create(TwitchWebhookClient.JSON, data.toString()))
      //.header("Client-ID", System.getenv("TWITCH_CLIENT"))
      .header("Authorization", "Bearer " + System.getenv("TWITCH_OAUTH").replace("oauth:", ""))
      .build()).execute()
    val headers: Map[String, List[String]] = res.headers().toMultimap.asScala.mapValues(_.asScala.toList).toMap
    val body = res.body().string()
    logger.debug("Request headers: {}", headers)
    logger.debug("   Request body: {}", body)
    
    // If the hook is for more than a day, cache it so that we can refresh it
    if(leaseSeconds > 86400) {
      mode match {
        case "subscribe" =>
          mewna.redis(redis => {
            // Set it to be one day before the lease expires, so that the refresher can catch it
            redis.hset(WEBHOOK_STORE, userId + ":" + mode, System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(leaseSeconds - 86400))
            logger.info("Subscribed to {}", callback)
          })
        case "unsubscribe" =>
          mewna.redis(redis => {
            redis.hdel(WEBHOOK_STORE, userId + ":" + mode)
            logger.info("Unsubscribed from {}", callback)
          })
      }
    }
    
    // If we get response length 0, it means that it worked(?).
    // Yeah I don't get it either...
    //
    // Alright, after having tested this more:
    // It *looks* like a success => empty response, but *with* headers.
    // If you send a malformed request in some way, it does yell at you, so I guess
    // that it's only in the case of success that the body is empty?
    (headers, if(body.length == 0) {
      new JSONObject()
    } else {
      new JSONObject(body)
    })
  }
  
  def getUserById(id: String): (Map[String, List[String]], JSONObject) = {
    val res = client.newCall(new Request.Builder().url(TwitchWebhookClient.GET_USERS + "?id=" + id)
      .get()
      .header("Authorization", "Bearer " + System.getenv("TWITCH_OAUTH").replace("oauth:", ""))
      .build()).execute()
    val body = res.body().string()
    val headers: Map[String, List[String]] = res.headers().toMultimap.asScala.mapValues(_.asScala.toList).toMap
    (headers, if(body.length == 0) {
      new JSONObject()
    } else {
      new JSONObject(body).getJSONArray("data").get(0).asInstanceOf[JSONObject]
    })
  }
  
  def getUserByName(name: String): (Map[String, List[String]], JSONObject) = {
    val res = client.newCall(new Request.Builder().url(TwitchWebhookClient.GET_USERS + "?login=" + name)
      .get()
      .header("Authorization", "Bearer " + System.getenv("TWITCH_OAUTH").replace("oauth:", ""))
      .build()).execute()
    val body = res.body().string()
    val headers: Map[String, List[String]] = res.headers().toMultimap.asScala.mapValues(_.asScala.toList).toMap
    val nObject = new JSONObject(body)
    (headers, if(body.length == 0 || !nObject.has("data") || nObject.getJSONArray("data").length() == 0) {
      new JSONObject()
    } else {
      nObject.getJSONArray("data").get(0).asInstanceOf[JSONObject]
    })
  }
}
