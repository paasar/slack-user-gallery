# Slack user gallery generator

A Clojure library designed to fetch Slack users from the Slack Web API and create a HTML page gallery of them.

## Usage

### Setup

You need to [create a Slack App](https://api.slack.com/apps) to your workspace.
Add following scopes to it: `channels:history`, `users.profile:read` and `users:read`.
Then you can get the generated Bot User OAuth Access Token.

Bot should be added to #general channel for it to access the join history.

Get the id of the #general channel from [history endpoint test page](https://api.slack.com/methods/channels.history/test) by clicking the `#general` link in `channel` row.

### Running

After updating token, title and channel id to `resources/properties.edn` run

```
lein run
```

You'll get the result as `gallery.html`.

Result can be saved straight to jpg when run with following command

```
lein run jpg
```

Result is saved as `gallery.jpg`.

## Using as a library

This program can be used as a library with the following steps.

   1. Set correct values to `resources/properties.edn`.
   1. Run `lein install` to create a package to your local dependency repository.
   1. In the program using this declare dependency as `[slack-user-gallery "0.1.0"]`.
   1. Require with `[slack-user-gallery.core :refer [generate-gallery-html generate-gallery-image]]`.

## TODO

  * Replace search for join dates from channel history with get it straight from user data when Slack has added it to their API.

## License

Ari Paasonen, paasar at gmail dot com

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
