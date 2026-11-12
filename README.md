# NEXORA IPTV

Native Android IPTV player for phones and Android TV. Player only: users add playlists or credentials they are authorized to use. No channels or subscriptions are bundled.

## Included
- Kotlin + Jetpack Compose UI
- HTTPS M3U/M3U8 loading and EXTINF metadata parsing
- Xtream Codes-compatible live-stream API integration
- HLS/MP4 playback with AndroidX Media3 ExoPlayer
- Search and basic connection/error states

## Build
Open in Android Studio, install Android SDK 35 and JDK 17, sync Gradle, then run the app configuration. CLI: ./gradlew assembleDebug.

## Usage
Select M3U and provide an HTTPS playlist URL, or select Xtream and enter the HTTPS server, username and password from your provider. Keep credentials private.

## Planned
Secure source persistence, favorites, EPG, VOD/series, parental controls, subtitle/audio selection, TV remote polish and automated tests.

## Privacy and lawful use
NEXORA does not provide IPTV content. Use only sources for which you have permission. Credentials are processed on-device and are not sent to NEXORA servers.
