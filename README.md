# GACA for Android

The Android client for [gaca-api](https://github.com/damont/gaca) (the server, installer and docs), with an
[iPhone app](https://github.com/damont/gaca-ios) alongside. A thin client: chats, alerts, briefings, lists and
agent-written pages all come from your own server.

## Use it

There is no sign-in and no built-in server address. You **pair** the phone with your server:

1. Set up the server first: follow the [gaca README](https://github.com/damont/gaca#readme) (Hermes, a Cloudflare address, `./install.sh`).
2. On the server, run `gaca-api pair`. It shows a QR code and a one-time code (valid for minutes).
3. Open the app and tap **Scan QR code**, then scan it. If scanning is not possible, tap **Enter a code instead** and
   type the server address and code, or open the printed `gaca://setup?...` link on the phone.

A phone talks to one server at a time. **Settings → Disconnect from this server** unpairs it (the server forgets the
phone and stops its alerts) so you can pair with another. More phones can pair with the same server; `gaca-api device list`
shows them.

## Build

You need Android Studio (or a JDK 17 and the Android SDK 35) and a `google-services.json` from a Firebase project
(Firebase console → Project settings → Your apps → Android, package `dev.monts.gaca`) saved as `app/google-services.json`.

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/
./gradlew installDebug         # onto a connected phone (USB debugging on)
./gradlew test
```

## Push notifications: the secrets involved

Alerts always show in the app when you open it. To make them buzz your phone, your **server** sends each one to Google
(Firebase Cloud Messaging) itself, so two files from the same Firebase project have to line up. Only the second is secret.

| file | what it is | where it goes |
|---|---|---|
| `google-services.json` | the app's Firebase config: Firebase console → Project settings → Your apps → Android (package `dev.monts.gaca`) | `app/google-services.json` in this repo's checkout, used when you build the app. Not committed |
| service-account key (JSON) | lets the server send messages: Firebase console → Project settings → Service accounts → **Generate new private key** | on the server only, never in this repo or the app |

On the server run `gaca-api push android` (it asks for the key file), or answer the installer's push question. The server keeps
the key in `~/.config/gaca/secrets/` with owner-only permissions; `gaca-api push status` shows what is on. Both files must come
from the **same** Firebase project: an app built with someone else's `google-services.json` cannot be pushed to by your key. So
build the app yourself with your own project. Without push, alerts still appear whenever you open the app.

More in the [gaca README](https://github.com/damont/gaca#push-notifications-optional).

## Layout

`app/src/main/java/dev/monts/gaca/`: `ui/` (Compose screens; pairing is in `ui/setup/`), `data/remote` (API client),
`data/repo`, `util/`. The app keeps no logic of its own beyond display: if it needs something new, change the API.
