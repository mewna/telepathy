package com.mewna

import java.util.concurrent.Future

import io.vertx.core.json.JsonObject
import org.slf4j.{Logger, LoggerFactory}
import spark.Request
import spark.Spark._

/**
 * @author amy
 * @since 6/6/18.
 */
class API(val mewna: Mewna) {
  private val logger: Logger = LoggerFactory.getLogger(getClass)
  
  /*
   * Example payload:
   *
   * {
   *   "data": [{
   *     "from_id":"1336",
   *     "to_id":"1337",
   *     "followed_at": "2017-08-22T22:55:24Z"
   *   }]
   * }
   */
  private def handleFollows(req: Request): Unit = {
    val json = new JsonObject(req.body())
    // TODO: Sometimes this has no [0]?
    val jsonArray = json.getJsonArray("data")
    if(jsonArray != null) {
      if(jsonArray.size() > 0) {
        val payload = jsonArray.getJsonObject(0)
        mewna.twitchRatelimiter.queueLookupUser(payload.getString("from_id"),
          (_, fromBody) => {
            mewna.twitchRatelimiter.queueLookupUser(payload.getString("to_id"),
              (_, toBody) => {
                // Construct the follow event
                val event: JsonObject = new JsonObject().put("from", fromBody).put("to", toBody)
                mewna.eventManager.pushBackendEvent("TWITCH_FOLLOWER", event)
                logger.info("TWITCH_FOLLOWER - {} ({}) -> {} ({})",
                  Array(fromBody.getString("login"), fromBody.getString("id"),
                    toBody.getString("login"), toBody.getString("id")): _*
                )
              })
          })
      }
    }
  }
  
  /*
   * Example payload:
   *
   * Stream up:
   * {
   *   "data": [
   *     {
   *       "id": "0123456789", // Stream id
   *       "user_id": "5678", // Streamer's user id
   *       "game_id": "21779", // Game's Twitch id
   *       "community_ids": [], // IDs of communities the streamer is streaming with
   *       "type": "live", // Should be "live", will only be "" in case of error
   *       "title": "Best Stream Ever", // Duh
   *       "viewer_count": 417, // Duh
   *       "started_at": "2017-12-01T10:09:45Z", // Duh
   *       "language": "en", // Duh
   *       "thumbnail_url": "https://link/to/thumbnail.jpg" // Duh
   *     }
   *   ]
   * }
   *
   * Stream down:
   * {
   *   "data": []
   * }
   */
  private def handleStreamUpDown(req: Request): Unit = {
    if(req.body().length > 0) {
      val json = new JsonObject(req.body())
      val dataArray = json.getJsonArray("data")
      if(dataArray.size() > 0) {
        val obj = dataArray.getJsonObject(0)
        if(obj.getString("type").equals("live")) {
          logger.info("Got stream update: {}", obj)
          // Stream start
          mewna.twitchRatelimiter.queueLookupUser(obj.getString("user_id"),
            (_, streamer) => {
              val streamData = new JsonObject().put("streamData", obj).put("streamer", streamer)
              mewna.eventManager.pushBackendEvent("TWITCH_STREAM_START", streamData)
              logger.info("TWITCH_STREAM_START for {} ({})", streamer.getString("login"): String, streamer.getString("id"): Any)
            })
        }
      } else {
        // Stream end
        mewna.twitchRatelimiter.queueLookupUser(req.params(":id"),
          (_, streamer) => {
            val streamData = new JsonObject().put("streamer", streamer)
            mewna.eventManager.pushBackendEvent("TWITCH_STREAM_END", streamData)
            logger.info("TWITCH_STREAM_END for {} ({})", streamer.getString("login"): String, streamer.getString("id"): Any)
          })
      }
    }
  }
  
  def startServer(portNum: Int): Unit = {
    port(portNum)
    get("/", (_, _) => "memes")
    path("/api", () => {
      path("/v1", () => {
        path("/twitch", () => {
          post("/follows/:id", (req, _) => {
            handleFollows(req)
            new JsonObject()
          })
          post("/streams/:id", (req, _) => {
            handleStreamUpDown(req)
            new JsonObject()
          })
          
          get("/follows/:id", (req, _) => req.queryParams("hub.challenge"))
          get("/streams/:id", (req, _) => req.queryParams("hub.challenge"))
          
          get("/lookup/name/:name", (req, _) => {
            val future: Future[JsonObject] = mewna.twitchRatelimiter.queueFutureLookupUserName(req.params(":name"))
            val data: JsonObject = future.get()
            data
          })
          
          get("/lookup/id/:id", (req, _) => {
            val future: Future[JsonObject] = mewna.twitchRatelimiter.queueFutureLookupUserId(req.params(":id"))
            val data: JsonObject = future.get()
            data
          })
        })
      })
    })
  }
}
