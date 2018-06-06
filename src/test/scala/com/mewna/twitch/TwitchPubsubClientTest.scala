package com.mewna.twitch

import org.json.JSONObject
import org.junit.Assert._
import org.junit.Test

/**
 * @author amy
 * @since 5/5/18.
 */
class TwitchPubsubClientTest {
  @Test
  def testRecvMessage(): Unit = {
    // Shit's gonna get printed out here; that's fine
    
    val client = new TwitchPubsubClient("44h1k13746815ab1r2")
    // Should pass
    client.addPongNonce("44h1k13746815ab1r2")
    assertEquals(None, client.recvMessage(new JSONObject().put("type", "PONG").put("nonce", "44h1k13746815ab1r2")))
    assertEquals(None, client.recvMessage(new JSONObject().put("type", "RECONNECT")))
    
    // Should fail
    assertEquals(Some("ENONONCE"), client.recvMessage(new JSONObject().put("type", "RESPONSE").put("nonce", "44h1k13746815ab1r2")))
    assertEquals(Some("EBADPING"), client.recvMessage(new JSONObject().put("type", "PONG").put("nonce", "44h1k13746815ab1r2z")))
    
    // Should pass
    client.addPendingNonce("44h1k13746815ab1r2")
    assertEquals(None, client.recvMessage(new JSONObject().put("type", "RESPONSE").put("nonce", "44h1k13746815ab1r2")))
    
    // Should fail
    assertEquals(Some("EBADTYPE"), client.recvMessage(new JSONObject().put("type", "KJSDHGFKJSDHGF")))
    
    // Should pass
    assertEquals(None, client.recvMessage(new JSONObject().put("type", "MESSAGE").put("topic", "video-playback.secretlyanamy")
      .put("data", new JSONObject().put("message", "{}"))))
  }
}
