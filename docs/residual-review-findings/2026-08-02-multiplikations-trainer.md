# Offene Reste — Multiplikations-Trainer (2026-08-02)

## `display`-Formen „Oy" und „Äh" tragen Sprech-Tricks

`atoms.json` liefert `letter-eu` mit `display: "Oy"` und `letter-ae` mit `display: "Äh"`
(Commit `8fec1f1` „update silben lemma"). `display` ist aber das, was das Kind **sieht** —
auf der Spur-Schablone, den Wort-Bauer-Kacheln und im Jagd-Feld — und wird an mehreren
Stellen zusätzlich gesprochen (`SentenceOrderTrainer`, `SymbolInWordTrainer`,
`SymbolHuntTrainer`). Die Fibel führt an dieser Stelle „Eu" bzw. „Ä" ein, nicht „Oy"/„Äh".

Aufgefallen ist das erst, als die Unit-Tests auf das ausgelieferte Pack umgestellt wurden:
`WordGraphemesTest` erwartete „Eu" in der Graphem-Tabelle und lief auf „Oy" auf. Der Test
prüft die Digraphen jetzt über **Atom-IDs** statt über Schreibweisen und ist damit neutral
gegenüber dieser Entscheidung.

Bewusst nicht geändert: das ist eine ausdrückliche Content-Entscheidung des Nutzers. Falls
sie nur die Aussprache treffen sollte, gehört der Trick in `lemma` (das ist das Sprechfeld)
und `display` zurück auf „Eu"/„Ä".

## Sicht-Prüfung der Matrix am Gerät nachzuholen

Die neue Matrix-Beschriftung (Aufgabe oben, Zeilennummern links) ist gebaut und per
Unit-Test abgesichert, aber noch nicht am Testgerät (`font_scale 1.3`) angesehen worden —
das Gerät war während der Session gesperrt. Rechnerisch passt das Raster weiterhin:
6 Spalten × ~34 dp + 5 × 2 dp Abstand + 24 dp Gutter ≈ 240 dp gegen 320 dp Nutzbreite auf
einem 360-dp-Gerät (Testgerät selbst: 434 dp breit).
