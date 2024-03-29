package com.mewna.nats

import java.io.IOException
import java.util.UUID

import com.mewna.Mewna
import com.mewna.twitch.TwitchWebhookClient
import io.nats.client.Nats
import io.nats.streaming.{Message, StreamingConnection, StreamingConnectionFactory, SubscriptionOptions}
import org.json.JSONObject
import org.slf4j.{Logger, LoggerFactory}

/**
 * @author amy
 * @since 6/6/18.
 */
class NatsServer(val mewna: Mewna) {
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  // TODO: Client ID needs to use container name; use metadata service to fetch this
  private val connectionFactory: StreamingConnectionFactory = new StreamingConnectionFactory("mewna-nats", "mewna-telepathy-server" + UUID.randomUUID())
  
  private var connection: StreamingConnection = _
  
  /*
   * Data example:
   * {{{
   * d: {
   *   id: 1234567890,
   *
   * }
   * }}}
   */
  def connect(): Unit = {
    try {
      val natsUrl = System.getenv("NATS_URL")
      if(natsUrl != null) {
        logger.info("Connecting to NATS with: {}", natsUrl)
        connectionFactory.setNatsConnection(Nats.connect(natsUrl))
        connection = connectionFactory.createConnection
        
        connection.subscribe("twitch-event-queue", "twitch-event-queue", (m: Message) => {
          val message = new String(m.getData)
          try {
            val o = new JSONObject(message)
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
          } catch {
            case e: Exception =>
              logger.error("Caught error while processing socket message:")
              e.printStackTrace()
          }
        }, new SubscriptionOptions.Builder().durableName("mewna-twitch-event-queue-durable").build)
        
        connection.subscribe("backend-event-broadcast", (m: Message) => {
          val message = new String(m.getData)
          logger.info("Got broadcast: {}", message)
        })
      } else {
        logger.warn("No NATS_URL, not connecting...")
      }
    } catch {
      case e@(_: IOException | _: InterruptedException) =>
        throw new RuntimeException(e)
    }
  }
  
  def pushBackendEvent[T](kind: String, data: T): Unit = {
    pushEvent("backend-event-queue", kind, data)
  }
  
  private def pushEvent[T](queue: String, kind: String, data: T): Unit = {
    val event: JSONObject = new JSONObject().put("t", kind).put("ts", System.currentTimeMillis()).put("d", data)
    try {
      if(System.getenv("NATS_URL") != null && connection != null) {
        connection.publish(queue, event.toString().getBytes)
      }
    } catch {
      // Bind this pattern to variable e
      case e@(_: IOException | _: InterruptedException) => e.printStackTrace()
    }
  }
}
