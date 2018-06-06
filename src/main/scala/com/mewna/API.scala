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
            ""
          })
          post("/streams", (req, res) => {
            ""
          })
        })
      })
    })
  }
}
