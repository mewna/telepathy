# telepathy

The twitch and stuff notification server for [mewna](https://mewna.com).

## NATS message format

```Javascript
{
  "t": "TWITCH_SUBSCRIBE", // or "TWITCH_UNSUBSCRIBE"
  "d": {
    "id": 1234567890
    "topic": "streams" // or "follows"
  }
}
```