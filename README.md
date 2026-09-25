# Whitechapel Jack

Offline Android companion for the Jack player in a hidden-movement game inspired by
Letters from Whitechapel.

## MVP
- Jack PIN
- Secret hideout
- Night 1–4 state
- Normal / Coach / Alley move logging
- Secure-screen flag (blocks screenshots on supported Android devices)
- Detective-only clue lookup
- Detective arrest check
- No Internet permission

## Build
Open the project in a current Android Studio, let Gradle sync, then run the `app` configuration.

## Important rule note
This is an MVP rules assistant, not yet a complete digital implementation of every
Letters from Whitechapel rule. It records locations and answers clue/arrest queries
from the secret log. Map adjacency, legal Coach/Alley validation, special movement
limits, murder timing, police placement, and all optional rules still need to be
implemented/verified against the rulebook.

## Data note
Version 0.1 keeps game state in memory. Closing/killing the app clears the session.
Persistent encrypted storage is a planned next step.

This is an unofficial fan-made companion and contains no original board artwork.
