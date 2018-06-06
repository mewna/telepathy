package com.mewna.twitch

import com.mewna.Mewna
import okhttp3.{MediaType, OkHttpClient, Request, RequestBody}
import org.json.JSONObject

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
}

/**
 * @author amy
 * @since 6/5/18.
 */
final class TwitchWebhookClient(val mewna: Mewna) {
  private val client = new OkHttpClient.Builder().build()
  
  def subscribe(topic: String, userId: String, leaseSeconds: Int = 0, cache: Boolean = true): (Map[String, List[String]], JSONObject) = {
    updateHook("subscribe", topic, userId, leaseSeconds, cache)
  }
  
  def unsubscribe(topic: String, userId: String, leaseSeconds: Int = 0, cache: Boolean = true): (Map[String, List[String]], JSONObject) = {
    updateHook("unsubscribe", topic, userId, leaseSeconds, cache)
  }
  
  def updateHook(mode: String, topic: String, userId: String, leaseSeconds: Int = 0, cache: Boolean = true): (Map[String, List[String]], JSONObject) = {
    val callback = topic match {
      case TwitchWebhookClient.TOPIC_FOLLOWS => "https://telepathy.mewna.com/api/v1/twitch/follows"
      case TwitchWebhookClient.TOPIC_STREAM_UP_DOWN => "https://telepathy.mewna.com/api/v1/twitch/streams"
    }
    val data = new JSONObject()
      .put("hub.callback", callback)
      .put("hub.mode", mode)
      .put("hub.topic", topic.format(userId))
      .put("hub.lease_seconds", leaseSeconds)
      .put("hub.secret", "") // TODO
    val res = client.newCall(new Request.Builder().url(TwitchWebhookClient.WEBHOOK_HUB)
      .post(RequestBody.create(TwitchWebhookClient.JSON, data.toString()))
      //.header("Client-ID", System.getenv("TWITCH_CLIENT"))
        .header("Authorization", "Bearer " + System.getenv("TWITCH_OAUTH").replace("oauth:", ""))
      .build()).execute()
    // TODO: Process headers
    val headers: Map[String, List[String]] = res.headers().toMultimap.asScala.mapValues(_.asScala.toList).toMap
    val body = res.body().string()
    
    // If we get response length 0, it means that it worked(?).
    // Yeah I don't get it either...
    (headers, if(body.length == 0) {
      new JSONObject()
    } else {
      new JSONObject(body)
    })
  }
  
  /**
   * Example payload:
   * {{{
   * {
   *   "data": [{
   *     "from_id":"1336",
   *     "to_id":"1337",
   *     "followed_at": "2017-08-22T22:55:24Z"
   *   }]
   * }
   * }}}
   *
   * @param followData JSON data sent from the webhook
   */
  def handleFollow(followData: JSONObject): Unit = {
    // TODO
  }
  
  /**
   * Example payload:
   * {{{
   * {
   *   "data": [
   *     {
   *       "id": "0123456789", // Stream id
   *       "user_id": "5678", // Streamer's user id
   *       "game_id": "21779", // Game's Twitch id
   *       "community_ids": [], // IDs of communities the streamer is streaming with
   *       "type": "live", // Should be "live", will only be "" in case of errpr
   *       "title": "Best Stream Ever", // Duh
   *       "viewer_count": 417, // Duh
   *       "started_at": "2017-12-01T10:09:45Z", // Duh
   *       "language": "en", // Duh
   *       "thumbnail_url": "https://link/to/thumbnail.jpg" // Duh
   *     }
   *   ]
   * }
   * }}}
   *
   * @param streamData Data about the stream going up / down
   */
  def handleStream(streamData: JSONObject): Unit = {
    // TODO
  }
}
