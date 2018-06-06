package com.mewna

import spark.Spark._

/**
 * @author amy
 * @since 6/6/18.
 */
class API {
  def startServer(portNum: Int): Unit = {
    port(portNum)
    path("/api", () => {
      path("/v1",() => {
        path("/twitch", () => {
          post("/follows", (req, res) => {
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
            // TODO
            ""
          })
          post("/streams", (req, res) => {
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
            // TODO
            ""
          })
        })
      })
    })
  }
}
