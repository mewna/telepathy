package com.mewna

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.mewna.twitch.TwitchPubsubClient

/**
 * @author amy
 * @since 05/02/2018.
 */
object Mewna {
  val mapper = new ObjectMapper()
  
  def main(args: Array[String]): Unit = {
    mapper.registerModule(DefaultScalaModule)
    new Mewna().run()
  }
}

class Mewna {
  private def run(): Unit = {
    // NOTE: For now we only care about Twitch
    // We can do other stuff later
    val twitch = new TwitchPubsubClient(System.getenv("TWITCH_OAUTH").replaceAll("oauth:", ""))
    twitch.connect(() => {
      twitch.channelListen("136359927")
    })
  }
}
