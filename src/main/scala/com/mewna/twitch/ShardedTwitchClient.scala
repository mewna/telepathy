package com.mewna.twitch

/**
 * A single [[TwitchPubsubClient]] can only have up to 50 topics subscribed at any
 * given time. To get around this, we can spawn up to ~10 [[TwitchPubsubClient]]s
 * per node, effectively sharding the work across clients.
 *
 * @author amy
 * @since 6/5/18.
 */
class ShardedTwitchClient {
  var clients: Map[Int, TwitchPubsubClient] = Map()
  
  // TODO
}
