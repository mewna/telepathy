package com.mewna.twitch

import java.util.concurrent.TimeUnit

import org.junit.Assert._
import org.junit.Test

/**
 * @author amy
 * @since 6/6/18.
 */
class TwitchWebhookClientTest {
  
  /*
   * Webhook subscribe response headers:
   * -----------------------------------
   * Map(
   *   timing-allow-origin -> List(https://www.twitch.tv),
   *   expires -> List(0),
   *   x-ctxlog-logid -> List(1-5b1833c2-67bd4c822fd8d968d67bfad0),
   *   server -> List(nginx),
   *   ratelimit-limit -> List(30),
   *   ratelimit-remaining -> List(29),
   *   access-control-allow-origin -> List(*),
   *   cache-control -> List(no-cache, no-store, must-revalidate, private),
   *   content-length -> List(0),
   *   twitch-trace-id -> List(a58c6098ed51dc719c604c4cd7ed715f),
   *   date -> List(Wed, 06 Jun 2018 19:19:31 GMT),
   *   ratelimit-reset -> List(1528312830),
   *   pragma -> List(no-cache),
   *   connection -> List(keep-alive)
   * )
   */
  
  @Test
  def testWebhookSubscribeNoLength(): Unit = {
    // Doesn't need a Mewna instance to work
    val client = new TwitchWebhookClient(null)
    val (headers, json) = client.subscribe(TwitchWebhookClient.TOPIC_STREAM_UP_DOWN, "136359927", cache = false)
    
    // Test response body
    assertEquals("{}", json.toString())
    
    // Test headers
    val ratelimitLimit = headers("ratelimit-limit").head
    val ratelimitReset = headers("ratelimit-reset").head
    
    assertEquals(120, ratelimitLimit.toInt)
    assertTrue(ratelimitReset.toLong > TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()))
  }
}
