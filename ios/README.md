# XMusic Family — iOS (isolated development branch)

This directory is the start of a dedicated iPhone implementation. **All iOS changes must remain on `ios-development`.** Do not merge into `main` without explicit owner approval.

## Architecture
- SwiftUI app lifecycle and native navigation/safe areas.
- WKWebView to reuse the existing **Stitch-authored HTML/CSS/SVG**, preserving pixel-level layout where practical. Android's JavascriptInterface must be replaced with WKScriptMessageHandler; never expose unrestricted native actions to arbitrary web origins.
- AVPlayer/AVAudioSession and Now Playing / remote commands for permitted audio playback; background audio requires the corresponding capability and review of source licenses.
- OAuth via ASWebAuthenticationSession, with tokens in Keychain. Google Drive API access must be authorized per user. Do not embed client secrets.
- Podcast RSS integration, family profiles, playlist/favorites/progress sync via a shared authenticated backend; no claim of automatic cross-device sync before backend is implemented.
- Offline media only where rights and platform rules permit; app must not rely on unauthorized extraction from third-party streaming services.

## Isolation rules
1. Keep default Android `main` untouched. No cross-branch merges.
2. Use iOS-specific `ios/` folder, bundle ID, credentials, signing and CI.
3. Never run an Android workflow with a modified iOS branch unless it is explicitly branch-safe.
4. Verify Android main HEAD and Android CI before any eventual release.
5. Distribute iPhone builds through Xcode on a signed device or TestFlight; GitHub Actions on macOS requires Apple signing credentials to produce installable IPA.

## Current status
Initial SwiftUI source skeleton; **not a buildable .xcodeproj or an IPA yet**. Next: import exact Stitch asset folders from the repository, configure Xcode project and resource bundling, implement WKWebView bridge, then native playback.

## Implementation order
1. Set up Xcode project with iOS deployment target and bundled Stitch assets.
2. Native WebView shell: navigation, safe area, state bridge, application lifecycle.
3. AVPlayer: play/pause/seek/queue, lock screen and background audio.
4. Google Drive OAuth and authorized streaming.
5. RSS podcast, persistence, family-safe controls, and authenticated sync.
6. Tests, macOS CI, Apple signing, physical iPhone validation.
