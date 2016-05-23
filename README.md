# Slack user gallery generator

A Clojure library designed to fetch Slack users from the Slack Web API and create a HTML page gallery of them.

## Usage

Update token, title, channel id to `resources/properties.edn` and then run

```
lein run
```

You'll get the result as `gallery.html`.


Get a token from here: https://api.slack.com/docs/oauth-test-tokens
Get the id of the #general channel from here: https://api.slack.com/methods/channels.history/test

## TODO

  * Tests
  * Replace search for join dates from channel history with get it straight from user data when Slack has added it there.
  * Investigate how this handles users who has quit.

## License

Ari Paasonen, paasar at gmail dot com

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
