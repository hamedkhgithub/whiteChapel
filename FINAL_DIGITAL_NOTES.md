# Final dual-mode build

- Classic mode remains the original notebook/inquiry companion and starts directly in the classic Jack flow after setup.
- Digital Board mode has its own saved state and starts with Hideout setup, then Hell.
- Hell flow: women placement -> handoff -> police patrol placement -> handoff/reveal real women -> Kill/Wait -> move Wretched -> reveal one Patrol -> repeat up to Roman V -> murder -> Alarm Whistles -> Hunting.
- TV endpoint publishes only public information. Hidden Jack route/hideout and hidden token identities are not serialized to the public state.
- Phone map supports two-finger zoom/pan and double-tap reset. There is no fixed aspect-ratio frame; the map uses the available game area.
- Women/Police markers are hollow outlines so house/location numbers remain visible. TV shows hidden women as white outlines and hidden Police Patrols as black outlines.
- Hunting flow is implemented: secret Jack move, five police moves, then Search/Arrest actions. Clues and crime scenes are public on TV.

## Important map-data limitation
The supplied houses.json and polises.json contain point coordinates but no street-edge/adjacency graph. Therefore the digital UI guides the official movement limits but cannot mathematically reject an illegal route yet. To enforce adjacency automatically, a graph JSON describing house-to-house and crossing-to-crossing edges is still required.
