# telepathy

The twitch and stuff notification server for [mewna](https://mewna.com).

## NATS message format

Inbound:
```Javascript
{
  "t": "TWITCH_SUBSCRIBE", // or "TWITCH_UNSUBSCRIBE"
  "d": {
    "id": 1234567890
    "topic": "streams" // or "follows"
  }
}
```
Outbound:
```Javascript
{
  "t": "TWITCH_FOLLOWER",
  "d": {
    "from": {
      "broadcaster_type": "",
      "offline_image_url": "",
      "description": "",
      "profile_image_url": "https://static-cdn.jtvnw.net/user-default-pictures/0ecbb6c3-fecb-4016-8115-aa467b7c36ed-profile_image-300x300.jpg",
      "id": "232248334",
      "login": "isillyspider",
      "display_name": "isillyspider",
      "type": "",
      "view_count": 0
    },
    "to": {
      "broadcaster_type": "partner",
      "offline_image_url": "https://static-cdn.jtvnw.net/jtv_user_pictures/ninja-channel_offline_image-bb607ec9e64184fa-1920x1080.png",
      "description": "Professional Battle Royale player. Follow my twitter @Ninja and for more content subscribe to my Youtube.com/Ninja",
      "profile_image_url": "https://static-cdn.jtvnw.net/jtv_user_pictures/6d942669-203f-464d-8623-db376ff971e0-profile_image-300x300.png",
      "id": "19571641",
      "login": "ninja",
      "display_name": "Ninja",
      "type": "",
      "view_count": 221856674
    }
  }
}

{
  "t": "TWITCH_STREAM_START",
  "d": {
    "streamer": {
      "broadcaster_type": "affiliate",
      "offline_image_url": "",
      "description": "I am a new full-time streamer! I am having such a blast meeting new people through my stream, being interactive is the best part! I hope you'll drop in and say hi &lt;3",
      "profile_image_url": "https://static-cdn.jtvnw.net/jtv_user_pictures/66f69f2de183a28d-profile_image-300x300.jpeg",
      "id": "168348708",
      "login": "morrigansky",
      "display_name": "MorriganSky",
      "type": "",
      "view_count": 7646
    },
    "streamData": {
      "user_id": "168348708",
      "community_ids": [
        "434e0896-4c27-4c87-9275-cbfba2b323f5",
        "dbec2d31-b2ad-49d0-9803-a752a0ef67fe",
        "ff1e77af-551d-4993-945c-f8ceaa2a2829"
      ],
      "started_at": "2018-06-18T12:56:20Z",
      "language": "en",
      "id": "29136964864",
      "viewer_count": 0,
      "thumbnail_url": "https://static-cdn.jtvnw.net/previews-ttv/live_user_morrigansky-{width}x{height}.jpg",
      "title": "Gotta Git Gud! ❤ Come Say Hi? ❤ Birthday Week Stream!! ❤",
      "type": "live",
      "game_id": "30921"
    }
  }
}

{
  "t": "TWITCH_STREAM_END",
  "streamer": "streamer": {
    "broadcaster_type": "affiliate",
    "offline_image_url": "",
    "description": "I am a new full-time streamer! I am having such a blast meeting new people through my stream, being interactive is the best part! I hope you'll drop in and say hi &lt;3",
    "profile_image_url": "https://static-cdn.jtvnw.net/jtv_user_pictures/66f69f2de183a28d-profile_image-300x300.jpeg",
    "id": "168348708",
    "login": "morrigansky",
    "display_name": "MorriganSky",
    "type": "",
    "view_count": 7646
  }
}
```