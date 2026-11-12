# NEXORA IPTV

NEXORA is a native Android IPTV player for Android phones and Android TV. It is a player only: no channels, subscriptions, or copyrighted streams are bundled.

## Current features
- M3U / M3U8 playlist import over HTTP or HTTPS
- Improved EXTINF parsing: channel name, group, logo and EPG ID
- Xtream Codes login and live TV catalog
- Xtream VOD movie catalog and playback
- Series catalog with episode retrieval and playback
- Search, category filters and locally saved favorites
- Media3 playback with buffering indication and provider-supplied audio/subtitle track controls
- Short EPG lookup for live channels on Xtream providers
- Android TV launcher entry and remote-friendly focusable content rows

## Build
Requires JDK 17 and Android SDK 35. Open in Android Studio and run the app, or run:

```bash
gradle assembleDebug
```

The GitHub Actions workflow builds a debug APK and publishes it as the `NEXORA-Android-APK` artifact.

## Connect a source
Choose M3U / M3U8 and enter a playlist URL, or choose Xtream Codes and enter the server URL, username and password supplied by your provider. Some providers require HTTPS; others expose HTTP endpoints. Keep credentials private.

## Privacy and lawful use
NEXORA does not provide IPTV content. Use only playlists and provider accounts you are authorized to access. Provider credentials are used by the app to contact the provider and are not sent to NEXORA servers. Favorites are stored locally on the device; provider credentials are not saved to preferences.

## Known limitations
- EPG currently uses the provider's short-EPG endpoint; external XMLTV URL import and a full multi-day guide are not yet implemented.
- No built-in channels, subscription service, cloud sync, parental PIN, catch-up archive, or account portal.
- Playback compatibility depends on the provider's stream format, codecs, network and device.
- The project still needs broad real-device testing across Android TV remotes, phone aspect ratios and different IPTV providers before a production release.
