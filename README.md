# Whitechapel Jack – v2 Hunting Map Pilot

Pilot map mode keeps the classic/local mode and adds a single-phone Hunting workflow with a local TV/PC map server.

Map-mode pilot features:
- Public TV/PC map served locally from the Android host.
- Five police pawns shown on both the detective phone map and TV map.
- Police pawn can be selected from the toolbar or by tapping its marker on the phone map.
- Free police movement between temporary police nodes (movement legality intentionally not enforced yet).
- Pinch-to-zoom in the detective WebView map.
- Search and Arrest are performed by selecting a police pawn, selecting the action, then tapping a temporary Jack location node.
- Negative Search allows the same pawn to continue searching; positive Search ends that pawn action and creates a persistent clue marker.
- Failed Arrest ends that pawn action.
- Successful Arrest marks game over on the public display and reveals the current-night Jack route on the TV map.
- Police positions and clue markers are persisted in SharedPreferences for map mode.
- End Detective Turn hands the phone back through the existing Jack PIN screen.
- Existing Jack Hunting page and classic mode are retained.

Temporary map-node coordinates are approximate and are intended to be corrected later against the authoritative board/rulebook.
