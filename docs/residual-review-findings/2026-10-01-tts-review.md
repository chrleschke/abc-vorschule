# Offene Reste — Code- und UX-Review TTS-Tooling (2026-10-01)

Behoben ist der Großteil (siehe Git-History des Branches `claude/tts-review` und
`tools/tts/README.md`). Bewusst offen:

## Aktionen bauen die Detailsicht neu auf

👍, 👎, Produktion und Profil-/Stimmwechsel rufen `refresh()` und zeichnen die
Kandidaten-Tabelle neu; ein gerade spielendes `<audio>` verstummt dabei. Für ein
ruhiges Nachladen bräuchte es ein Zeilen-Diff statt `innerHTML` — zu groß für diesen
Durchgang.

## Warteschlange ohne Einzel-Abbruch

„Abbrechen" trifft nur den laufenden Job; eingereihte Jobs laufen danach weiter, und
es gibt keine Möglichkeit, einen eingereihten Job herauszunehmen.

## Hash-Seeds im Mikrofon-Bereich

`plan.resolve_seed` fällt ohne Seed-Pool auf `sha256 % 2**31` zurück und überlappt
damit den Pseudo-Seed-Bereich der Mikrofon-Aufnahmen (`mic.MIC_SEED_MIN`). Eine
Korrektur änderte die Seeds aller ungelockten Clips. `render_protection` behandelt
einen gelockten Seed in diesem Bereich deshalb nur dann als Mikrofon, wenn keine lokale
Produktions-WAV da ist.

## Altbestand: Probetexte als Produktionstext (bereinigt)

Vor der Trennung `textOverride` (Produktion) / `draftText` (Entwurf) schrieb jedes
Tippen im TTS-Feld den Produktionstext. Die zwei bekannten Probetexte sind auf Wunsch
der Nutzerin bzw. des Nutzers aus `locks.json` entfernt (`sentence:027d7fa9791a`
„Der Pirat hat die Kekse geklaut.", `word:7253bc3fb39a` „30 Rüben", verwaist). Die
ausgelieferte Pirat-Aufnahme sprach schon vorher den Originalsatz.
