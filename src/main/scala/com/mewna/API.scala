package com.mewna

import java.util.concurrent.Future

import org.json.JSONObject
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
    val json = new JSONObject(req.body())
    // TODO: Sometimes this has no [0]?
    val jsonArray = json.getJSONArray("data")
    if(jsonArray != null) {
      if(jsonArray.length() > 0) {
        val payload = jsonArray.get(0).asInstanceOf[JSONObject]
        mewna.twitchRatelimiter.queueLookupUser(payload.getString("from_id"),
          (_, fromBody) => {
            mewna.twitchRatelimiter.queueLookupUser(payload.getString("to_id"),
              (_, toBody) => {
                // Construct the follow event
                val event: JSONObject = new JSONObject().put("from", fromBody).put("to", toBody)
                mewna.q.pushBackendEvent("TWITCH_FOLLOWER", event)
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
   *       "type": "live", // Should be "live", will only be "" in case of errpr
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
      val json = new JSONObject(req.body())
      val dataArray = json.getJSONArray("data")
      if(dataArray.length() > 0) {
        // Stream start
        mewna.twitchRatelimiter.queueLookupUser(dataArray.get(0).asInstanceOf[JSONObject].getString("user_id"),
          (_, streamer) => {
            val streamData = new JSONObject().put("streamData", dataArray.get(0).asInstanceOf[JSONObject]).put("streamer", streamer)
            mewna.q.pushBackendEvent("TWITCH_STREAM_START", streamData)
            logger.info("TWITCH_STREAM_START for {} ({})", streamer.getString("login"): String, streamer.getString("id"): Any)
          })
      } else {
        // Stream end
        mewna.twitchRatelimiter.queueLookupUser(req.params(":id"),
          (_, streamer) => {
            val streamData = new JSONObject().put("streamer", streamer)
            mewna.q.pushBackendEvent("TWITCH_STREAM_END", streamData)
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
            new JSONObject()
          })
          post("/streams/:id", (req, _) => {
            handleStreamUpDown(req)
            new JSONObject()
          })
          
          get("/follows/:id", (req, _) => req.queryParams("hub.challenge"))
          get("/streams/:id", (req, _) => req.queryParams("hub.challenge"))
          
          get("/lookup/name/:name", (req, _) => {
            val future: Future[JSONObject] = mewna.twitchRatelimiter.queueFutureLookupUserName(req.params(":name"))
            val data: JSONObject = future.get()
            data
          })
          
          get("/lookup/id/:id", (req, _) => {
            val future: Future[JSONObject] = mewna.twitchRatelimiter.queueFutureLookupUserId(req.params(":id"))
            val data: JSONObject = future.get()
            data
          })
        })
      })
    })
  }
}
