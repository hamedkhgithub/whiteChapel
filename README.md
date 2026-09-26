# Whitechapel Jack — Near Final v1

GitHub-ready Android project with automatic debug APK build.

Implemented:
- approved 5-second splash + main menu theme
- persistent game state; Continue always resumes in Detective Mode
- system Back is consumed during play; Jack screen Back locks and switches to Detective Mode
- New Game: fixed Hideout + PIN
- Jack screen: Night number, "محل ارتکاب قتل", gray/black registration button
- Normal / Coach / Alley movement UI
- official per-night special-move limits:
  Night 1: Coach 3, Alley 2
  Night 2: Coach 2, Alley 2
  Night 3: Coach 2, Alley 1
  Night 4: Coach 1, Alley 1
- Coach requires two distinct destinations and consumes two move-track spaces
- Alley icon is red
- 15-space move-track guard
- Hideout popup: declare escape or keep secret and continue
- Detective Mode: Search, Arrest, per-night inquiry history, Jack PIN handoff
- final Audit screen
- local persistence

Not yet implemented: board adjacency/map validation and full Hell-phase setup automation.

## v1.1
- Night 3 Double Event corrected.
- Jack records two Crime Scenes in a secret order.
- The second/rightmost Crime Scene becomes Jack's Hunting start.
- The second murder consumes the first movement space of Night 3.
- Final Audit reveals both Night 3 crime scenes in recorded order.

## v1.2
- Jack can correct a mistaken move before handing the device to detectives.
- Each recorded move has an Edit/اصلاح action.
- Because later destinations depend on earlier positions, correcting a move removes that move and every later move of the current night, then Jack re-enters the route from that point.
- Special-move counters and Move Track usage are automatically recalculated after correction.
- Once handed to Detective Mode, the route cannot be edited without the Jack PIN handoff.

## v1.3 — Turn-by-turn Hunting correction
- Jack may register exactly one move per Jack turn.
- Coach is still one Jack turn, while recording two destinations / two Move Track spaces.
- After Jack registers that move, all movement-entry controls are locked.
- Only the just-recorded move can be corrected; previous moves of the Night are locked.
- Correcting the current move deletes only that move and re-opens the same Jack turn.
- "Hand to Detectives" is disabled until Jack has successfully registered the move.
- After handoff, the Detective screen runs; returning to Jack requires the Jack PIN.
- Continue Game resumes in Detective Mode, never on the secret Jack screen.

## v1.3.1 build fix
- Reworked nullable numeric range checks into explicit Kotlin-safe checks.
- Removed nullable destination ambiguity in Coach move construction.
- Gameplay behavior from v1.3 is unchanged.

## v1.3.2 compile fix
- Replaced ambiguous `sumOf` with an explicitly typed `fold`.
- Made `MoveChip` a `RowScope` composable so `Modifier.weight()` is valid.
- The reported line 258 arithmetic ambiguity was a cascading type-inference error from the ambiguous movement-track expression.

## v1.3.4
- Popup when جک is arrested: detectives win.
- Popup when the 15-space movement allowance is exhausted before reaching the hideout: detectives win.
- Popup when جک reaches and declares the hideout on Night 4: جک wins.
- Visible English `Jack` labels were changed to `جک` for cleaner RTL display.

## v2 Pilot — Local Map Mode
- Keeps the v1.3.5 classic game flow unchanged.
- Adds «شروع بازی جدید با نقشه» directly below New Game.
- Starts a tiny local HTTP server on port 8080 on the Android host.
- Shows the local URL and a copy button.
- Another device on the same Wi-Fi/hotspot can open the URL in a browser.
- Pilot web page displays «به بازی جدید خوش آمدید».
- This pilot intentionally does not yet include the real map, WebSocket sync, move validation, or role clients.

## Map display pilot update
The local map-mode HTTP server now serves `app/src/main/assets/whitechapel_map.webp` at `/map.webp` and displays it full-screen/responsively on the browser page. No board-position extraction, move validation, or live piece synchronization is included in this step.
