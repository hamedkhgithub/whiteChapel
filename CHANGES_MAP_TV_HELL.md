# Map / TV / Hell update

- Local TV/browser map server restored on port 8765. The app displays the LAN URL; phone and TV must be on the same Wi-Fi.
- TV page polls live public state and overlays police, Search/Arrest activity, and Hell markers.
- Secret Hell data is not included in the public `/state` payload until Reveal is enabled.
- Two-finger zoom/pan and double-tap reset were added to interactive maps.
- Map Test page: choose police color and Move/Search/Arrest, then tap map positions.
- Hell Phase: Detective privately places colored real/fake police; Jack privately places colored real/fake victims. Public TV sees only neutral positions until Reveal.
- House numbers remain a separate transparent image layer.
- `assets/houses.json` is used for house selection and `assets/polises.json` for selectable police nodes.
