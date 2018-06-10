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
    mewna.twitchRatelimiter.queueLookupUser(json.getJSONArray("data").get(0).asInstanceOf[JSONObject].getString("from_id"),
      (_, fromBody) => {
        mewna.twitchRatelimiter.queueLookupUser(json.getJSONArray("data").get(0).asInstanceOf[JSONObject].getString("to_id"),
          (_, toBody) => {
            logger.info("Got webhook data: /follows => {}", json.toString(2))
            logger.info("        fromData: /follows => {}", fromBody.toString(2))
            logger.info("          toData: /follows => {}", toBody.toString(2))
          })
      })
  }
  
  /*
   * Example payload:
   *
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
   */
  private def handleStreamUpDown(req: Request): Unit = {
    if(req.body().length > 0) {
      val json = new JSONObject(req.body())
      mewna.twitchRatelimiter.queueLookupUser(json.getJSONArray("data").get(0).asInstanceOf[JSONObject].getString("user_id"),
        (_, streamer) => {
          logger.info("Got webhook data: /streams => {}", json.toString(2))
          logger.info("        streamer: /streams => {}", streamer.toString(2))
        })
    }
  }
  
  def startServer(portNum: Int): Unit = {
    port(portNum)
    get("/", (_, _) => "memes")
    path("/api", () => {
      path("/v1", () => {
        path("/twitch", () => {
          post("/follows", (req, _) => {
            handleFollows(req)
            new JSONObject()
          })
          post("/streams", (req, _) => {
            handleStreamUpDown(req)
            new JSONObject()
          })
          
          // Spark is retarded and won't show any of this for reasons that are beyond me
          get("/follows", (req, _) => req.queryParams("hub.challenge"))
          get("/streams", (req, _) => req.queryParams("hub.challenge"))
          
          get("/lookup/:name", (req, res) => {
            val future: Future[JSONObject] = mewna.twitchRatelimiter.queueFutureLookupUserName(req.params(":name"))
            val data: JSONObject = future.get()
            data
          })
        })
      })
    })
  }
}
