package com.mewna.twitch

import java.util
import java.util.UUID
import java.util.concurrent.TimeUnit

import com.mewna.Mewna
import com.mewna.util.Helpers._
import com.neovisionaries.ws.client._
import org.json.{JSONArray, JSONObject}
import org.slf4j.LoggerFactory

/**
 * Create with ex. System.getenv("TWITCH_OAUTH").replaceAll("oauth:", "")
 *
 * TODO: HTTP/WS proxy settings to dodge the ~10 WS/IP limit
 *
 * @author amy
 * @since 5/5/18.
 */
//noinspection ScalaUnusedSymbol
class TwitchPubsubClient(val mewna: Mewna, val TOKEN: String) {
  val URL = "wss://pubsub-edge.twitch.tv/v1"
  
  private var socket: WebSocket = _
  
  private var lastPingNonce = ""
  private var lastPongTime: Long = -1L
  
  private val logger = LoggerFactory.getLogger(getClass)
  
  private var pendingNonces = Set[String]()
  private var acceptedNonces = Set[String]()
  
  //////////////////
  // SOCKET STATE //
  //////////////////
  
  def connect(onConnect: () => Unit): Unit = {
    socket = new WebSocketFactory().createSocket(URL)
    socket.addListener(new WebSocketAdapter {
      override def onConnected(websocket: WebSocket, headers: util.Map[String, util.List[String]]): Unit = {
        // Schedule PING task
        mewna.threadPool.execute(() => {
          while(true) {
            if(socket != null && socket.isOpen) {
              // Send a PING payload once a minute. Technically it only needs to be every 5 minutes,
              // but let's play it safe here.
              ping() // Only sends if connected
              try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(1))
              } catch {
                case e: InterruptedException => e.printStackTrace()
              }
            }
          }
        })
        // Schedule reconnect task
        mewna.threadPool.execute(() => {
          while(true) {
            if(socket != null && socket.isOpen) {
              // Check if we need to reconnect every 10 seconds
              if(lastPongTime - System.currentTimeMillis() > TimeUnit.SECONDS.toMillis(10)) {
                // It's been more than 10 seconds, reconnect
                reconnect()
              }
              try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(10))
              } catch {
                case e: InterruptedException => e.printStackTrace()
              }
            }
          }
        })
        onConnect()
        logger.info("Connected to pubsub-edge")
      }
      
      override def onDisconnected(websocket: WebSocket, serverCloseFrame: WebSocketFrame, clientCloseFrame: WebSocketFrame,
                                  closedByServer: Boolean): Unit = {
        // TODO: Reconnect
        logger.warn("Disconnected from socket: (server? = {})", closedByServer)
        reconnect()
      }
      
      override def onTextMessage(websocket: WebSocket, text: String): Unit = {
        logger.info("Text message: " + new JSONObject(text))
        recvMessage(new JSONObject(text))
      }
    })
    socket.connect()
  }
  
  private def reconnect(): Unit = {
    if(socket != null) {
      if(socket.getState == WebSocketState.OPEN) {
        socket.disconnect()
        
      }
      socket = socket.recreate().connect()
    }
  }
  
  //////////////////
  // MESSAGE RECV //
  //////////////////
  
  def recvMessage(data: JSONObject): Option[String] = {
    
    val `type` = data.getString("type")
    `type` match {
      case "PONG" => handlePong(data)
      case "RECONNECT" => handleReconnect(data)
      case "MESSAGE" => handleMessage(data)
      case "RESPONSE" => handleResponse(data)
      case kind => handleUnknownType(kind)
    }
  }
  
  private def handlePong(data: JSONObject): Option[String] = {
    val nonce = data.getString("nonce")
    if(nonce.equals(this.lastPingNonce)) {
      lastPongTime = System.currentTimeMillis()
      None
    } else {
      Some("EBADPING")
    }
  }
  
  private def handleReconnect(data: JSONObject): Option[String] = {
    reconnect()
    None
  }
  
  private def handleMessage(data: JSONObject): Option[String] = {
    val topic = data.getString("topic")
    val message = new JSONObject(data.getJSONObject("data").getString("message"))
    logger.info("Got message for topic {}: '{}'", topic: Any, message: Any)
    None
  }
  
  private def handleResponse(data: JSONObject): Option[String] = {
    val nonce = data.getString("nonce")
    if(data.has("error") && !data.isNull("error") && !data.getString("error").isEmpty) {
      logger.warn("Invalid pending nonce {} (pending: {})", nonce: Any, pendingNonces.size: Any)
      logger.warn("Reason: {}", data.getString("Error"): Any)
      pendingNonces = pendingNonces - nonce
      Some(data.getString("error"))
    } else {
      if(pendingNonces.contains(nonce)) {
        pendingNonces = pendingNonces - nonce
        acceptedNonces = acceptedNonces + nonce
        logger.info("Accepted nonce: {} ({} pending)", nonce: Any, pendingNonces.size: Any)
        None
      } else {
        logger.warn("Got nonce '{}', but it wasn't pending!?", nonce)
        Some("ENONONCE")
      }
    }
  }
  
  private def handleUnknownType(kind: String): Option[String] = {
    logger.warn("Got unknown message type: {}", kind)
    Some("EBADTYPE")
  }
  
  //////////////////
  // MESSAGE SEND //
  //////////////////
  
  private def ping(): Unit = {
    val ping = createMessage("PING", new JSONObject())
    lastPingNonce = ping.getString("nonce")
    ping |> stringify |> sendText
  }
  
  def listen(topic: String, id: String): Unit = {
    val message = createMessage("LISTEN", new JSONObject().put("topics", new JSONArray(util.Arrays.asList(topic + "." + id))))
    val nonce = message.getString("nonce")
    logger.info("LISTEN -> {}.{} with nonce {}", topic: String, id: String, nonce: String)
    pendingNonces = pendingNonces + nonce
    message |> stringify |> sendText
  }
  
  def channelListen(id: String): Unit = {
    listen("video-playback-by-id", id)
  }
  
  private def stringify[X](x: X): String = {
    x.toString
  }
  
  private def sendText(text: String): Unit = {
    if(socket != null) {
      if(socket.getState == WebSocketState.OPEN) {
        logger.info("Sending message: {}", text)
        socket.sendText(text)
      }
    }
  }
  
  /**
   * Message format:
   * {{{
   * {
   *   "type": "LISTEN",
   *   "nonce": "your nonce goes here",
   *   "data": {
   *     "topics": ["your topics", "go here"],
   *     "auth_token": "your oauth token"
   *   }
   * }
   * }}}
   *
   * @return The message as a JSON object
   */
  private def createMessage(`type`: String, data: JSONObject): JSONObject = {
    if(!data.has("auth_token") || data.isNull("auth_token") || data.getString("auth_token").isEmpty) {
      // Fill in token if not present
      data.put("auth_token", TOKEN)
    }
    new JSONObject().put("type", `type`).put("nonce", UUID.randomUUID().toString)
      .put("data", data)
  }
  
  /////////////
  // UTILITY //
  /////////////
  
  def getSize: Int = {
    pendingNonces.size + acceptedNonces.size
  }
  
  def isAtCapacity: Boolean = {
    getSize == 50
  }
  
  /**
   * Test helper method
   */
  private[twitch] def addPendingNonce(nonce: String): Unit = {
    pendingNonces = pendingNonces + nonce
  }
  
  /**
   * Test helper method
   */
  private[twitch] def addPongNonce(nonce: String): Unit = {
    lastPingNonce = nonce
    lastPongTime = System.currentTimeMillis()
  }
}
