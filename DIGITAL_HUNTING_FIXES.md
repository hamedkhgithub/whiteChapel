# Digital Hunting fixes

- Police markers are rendered as true circles; TV CSS uses equal width/height.
- Crime Scene is a hollow red square and is published to the public board.
- Kill now transitions to Jack PIN unlock before Hunting.
- Jack Hunting screen starts automatically from the Crime Scene, supports Normal/Coach/Alley selection, map destination selection, move registration, and handoff to detectives.
- Detective Hunting shows five real police positions and five color bars for selection.
- Search miss shows a clear message and allows another search by the same officer.
- Search hit creates a translucent yellow clue circle while keeping the house number readable, then finishes that officer's action.
- Arrest reports success/failure. A failed arrest finishes/disables that officer for the current police-action phase.
- After all five police actions, the phone is handed back to Jack and PIN unlock is required again.

## Detective clue-action follow-up
- A police token that performs at least one clue Search cannot attempt Arrest during the same detective turn.
- `policeSearched` tracks that restriction independently for each of the five real police tokens.
- Finishing that police token's clue inquiries marks the token done/disabled for the rest of the turn.
- The "تحویل به جک" button is always enabled on the Police clue/arrest page, so detectives may end their action phase early.
