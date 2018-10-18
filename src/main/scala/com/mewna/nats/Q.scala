package com.mewna.nats

import java.io.IOException

import com.mewna.Mewna
import com.mewna.twitch.TwitchWebhookClient
import org.json.JSONObject
import org.slf4j.{Logger, LoggerFactory}

/**
 * @author amy
 * @since 10/17/18.
 */
class Q(val mewna: Mewna) {
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  
  def connect(): Unit = {
    new Thread(() => {
      logger.info("Connected Q~ using {}", System.getenv("EVENT_QUEUE") + ":twitch-event-queue")
      while(true) {
        mewna.redis(redis => {
          val maybeTuple = redis.blpop(0, System.getenv("EVENT_QUEUE") + ":twitch-event-queue")
          if(maybeTuple.nonEmpty) {
            val (_, e) = maybeTuple.get
            val o = new JSONObject(e)
            val data = o.getJSONObject("d")
            mewna.threadPool.execute(() => {
              // TODO: Un/subscribe
              o.getString("t") match {
                case "TWITCH_SUBSCRIBE" =>
                  val id = data.getString("id")
                  val topic = data.getString("topic") match {
                    case "streams" => TwitchWebhookClient.TOPIC_STREAM_UP_DOWN
                    case "follows" => TwitchWebhookClient.TOPIC_FOLLOWS
                  }
                  if(mewna.twitchWebhookClient.needsResub(id)) {
                    mewna.twitchRatelimiter.queueSubscribe(topic, id, (_, _) => {
                      logger.info("Refreshed {}", id)
                    })
                  }
                case "TWITCH_UNSUBSCRIBE" =>
                  val id = data.getString("id")
                  val topic = data.getString("topic") match {
                    case "streams" => TwitchWebhookClient.TOPIC_STREAM_UP_DOWN
                    case "follows" => TwitchWebhookClient.TOPIC_FOLLOWS
                  }
                  mewna.twitchRatelimiter.queueUnsubscribe(topic, id, (_, _) => {
                    logger.info("Unsubscribed from id {} " + topic, id)
                  })
              }
            })
          } else {
            logger.warn("Pulled empty tuple from queue {}!", System.getenv("EVENT_QUEUE") + ":twitch-event-queue")
          }
        })
      }
    }).start()
  }
  
  def pushBackendEvent[T](kind: String, data: T): Unit = {
    pushEvent("backend-event-queue", kind, data)
  }
  
  private def pushEvent[T](queue: String, kind: String, data: T): Unit = {
    val event: JSONObject = new JSONObject().put("t", kind).put("ts", System.currentTimeMillis()).put("d", data)
    try {
      // connection.publish(queue, event.toString().getBytes)
      mewna.redis(redis => {
        redis.rpush(System.getenv("EVENT_QUEUE") + ":" + queue, event)
      })
    } catch {
      // Bind this pattern to variable e
      case e@(_: IOException | _: InterruptedException) => e.printStackTrace()
    }
  }
}
