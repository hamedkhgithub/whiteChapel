# Legacy TV variant

This variant is intentionally installable alongside the normal Whitechapel Jack app.

- applicationId: `com.hamed.whitechapeljack.legacytv`
- launcher label: `Whitechapel Jack Legacy TV`
- local TV server port: `8766` (normal build uses `8765`)
- public map JavaScript is ES5-style and uses `XMLHttpRequest` polling instead of `fetch` / `async` / `await`.
- state responses send aggressive no-cache headers for older browsers and intermediary caches.
- TV-served Coach and Alley icons are PNG rather than WebP for older browser compatibility.

The GitHub Actions workflow produces `WhitechapelJack-LegacyTV-debug.apk`.
