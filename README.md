# Whitechapel Jack v4 — GitHub Auto APK

Push all files to the root of a GitHub repository. GitHub Actions builds `app-debug.apk`.

## v4
- Dark Victorian Whitechapel theme based on the approved mockup
- Splash/loading artwork included and replaceable at `app/src/main/res/drawable-nodpi/whitechapel_splash.jpg`
- Improved buttons, parchment cards, hideout/lock/search/arrest visual cues
- Hideout is fixed for the whole game
- Reaching Hideout does NOT automatically end the night
- When Jack reaches Hideout by Normal move, Jack can:
  - declare escape and end the night, or
  - keep it secret and continue playing
- Detective screen never reveals the route or Hideout
- Per-night Search/Arrest history
- Final Game Audit reveals Hideout, all movement history, and inquiries
- Map adjacency validation is intentionally disabled

APK: Actions > Build Android APK > Artifacts > WhitechapelJack-debug-apk
