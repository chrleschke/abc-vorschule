# Kindgerechte Quick Wins — Plan

**Status:** implementation-ready (Nutzerauftrag 2026-09-28, nach Design-Review)
**Branch:** `claude/kid-quickwins`

## Anlass

Ein Review aus Motion-/Game-Design-Sicht (Zielgruppe 4–7) fand: die Trainer wirken
wie ein Werkzeug, nicht wie ein Spiel — Systemschrift, Emojis je nach Gerät, kaum
Sound, Rückmeldung an vielen Stellen ohne Bewegung. Der Nutzer hat neun Quick Wins
freigegeben. Leitplanken aus der Session:

- **Offline und klein.** Jede neue Datei wird gemessen; zusammen < 1,5 MB.
- **Kein Überladen (Cognitive Load).** Bewegung und Ton antworten auf eine Tat des
  Kindes. Nichts Neues läuft dauerhaft, keine Musik.
- **Jagd mischt weiter neu** — Absicht gegen Raten (Kinder tippen sonst alles an).
- **Spurensucher bleibt**, bis auf das Nachziehen der Glyphen an die neue Schrift.
- Maskottchen und Trainer-Welten sind **nicht** Teil dieses Plans.

## Die neun Punkte (Reihenfolge = Umsetzung)

1. **Motion-Tokens** — `ui/theme/Motion.kt` (`AbcMotion`): wenige benannte Dauern,
   Easings und Federn. Bestehende Animationen ziehen um, ohne ihr Verhalten zu ändern;
   Trainer-eigene Choreografie (Fresser kauen, Jagd-Druckmorph) behält ihre Werte, nutzt
   aber die Federn der Palette, wo sie schon gleich sind.
2. **Federnder Rückflug** — `DragCard`: eine daneben (oder falsch) abgelegte Karte fliegt
   mit einer Feder an ihren Platz zurück statt zu springen; Aufheben/Absetzen federn.
   Wort-Bauer und Satz-Architekt geben beim Fehlgriff `nudge`-Haptik wie alle übrigen
   Trainer.
3. **Jagd-Mischen animieren** — nach einem Fehltipp hüpfen die Kugeln auf ihre neuen
   Plätze (~400 ms), Eingabe gesperrt, solange sie fliegen. Mischen bleibt, die Bewegung
   bremst Durchtippen, ohne zu strafen.
4. **Stern fliegt in den Zähler** — der `SuccessBurst`-Stern landet im Punktestand, der
   Zähler hüpft beim Einschlag und die Zahl springt erst dann.
5. **Zurück per Gedrückthalten** — der Zurück-Pfeil der Lektion verlangt ~0,8 s Halten,
   ein Ring füllt sich; ein kurzer Tipp wackelt nur. Kein versehentliches Verlassen mehr.
6. **Kinder-Ziffernblock** — ersetzt die System-Zahlentastatur im Rechnen: große
   Ziffernknöpfe 0–9, Löschen und Absenden als Icons. Revidiert PRODUCT_PRINCIPLES §8
   („System-Tastatur im Zahlenmodus").
7. **SFX-Paket** — ~10 kurze, leise Clips (pop, snap, boing, klack, whoosh, Fanfare …),
   lokal mit `sox` synthetisiert (keine Lizenzfrage), OGG, gemeinsam < 300 KB. Ersetzt
   die Sinus-Töne dort, wo sie heute sitzen, und ergänzt Einrasten, Rückflug,
   Lektions-Ende.
8. **Schrift** — zwei gebündelte, auf Latin gekürzte Schriften:
   - **Silbo Fibel**: Ableitung von Andika (SIL OFL, umbenannt wegen Reserved Font Name),
     einstöckiges a/g, l mit Bogen, großes I als schlichter Strich wie in der Fibel. Für
     alles, was Lerninhalt ist: Buchstaben, Silben, Wörter, Ziffern.
   - **Baloo 2** (OFL) für UI-Text und Punktestand, passend zu Store-Grafik und Icon.
   Ein Build-Skript unter `tools/fonts/` erzeugt beide reproduzierbar. Die
   Spurensucher-Glyphen werden gegen die neue Schrift geprüft und, wo sie abweichen,
   nachgezogen.
9. **Emoji-Schrift** — Noto Color Emoji (OFL, CBDT, läuft ab API 26), gekürzt auf die
   Emojis des Packs (~750 KB). Einheitliches Bild auf allen Geräten, das Altersproblem
   der Redaktionsregel entfällt. Ein Test hält Pack und Subset synchron.

## Verifikation

- Je Punkt Unit-Tests für die rechenbaren Teile (Tokens, Geometrie, Ziffernblock-Eingabe,
  Emoji-Abdeckung), `./gradlew :app:testDebugUnitTest` grün.
- Sichtprüfung am Motorola (ZY22MCCHWS) nach den Punkten 3, 6, 8, 9.
- PRODUCT_PRINCIPLES §7–§10, README und AGENTS.md werden mitgezogen.
