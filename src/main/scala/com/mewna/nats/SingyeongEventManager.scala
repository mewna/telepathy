package com.mewna.nats

import java.io.IOException

import com.mewna.Mewna
import com.mewna.twitch.TwitchWebhookClient
import gg.amy.singyeong.{Dispatch, QueryBuilder}
import io.vertx.core.json.JsonObject
import org.slf4j.{Logger, LoggerFactory}

/**
 * @author amy
 * @since 12/7/18.
 */
class SingyeongEventManager(val mewna: Mewna) {
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  
  def onEvent(event: Dispatch): Unit = {
    val o = event.data()
    val data = o.getJsonObject("d")
    mewna.threadPool.execute(() => {
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
  }
  
  def pushBackendEvent[T](kind: String, data: T): Unit = {
    pushEvent(kind, data)
  }
  
  private def pushEvent[T](kind: String, data: T): Unit = {
    val event: JsonObject = new JsonObject().put("type", kind).put("ts", System.currentTimeMillis()).put("data", data)
    try {
      mewna.singyeong.send("backend", new QueryBuilder().build(), event)
    } catch {
      // Bind this pattern to variable e
      case e@(_: IOException | _: InterruptedException) => e.printStackTrace()
    }
  }
}
