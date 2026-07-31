# Known Residuals — feat/lektions-finale

Source: Task-Reviews während der Umsetzung (2026-07-31), bereits als "bewusst zurückgestellt"
adjudiziert. Diese Notiz existiert, damit ein späterer Leser die Entscheidung nachvollziehen
kann, ohne die Review-Historie neu auszugraben.

## Accepted residuals

| Severity | Area | Note |
|----------|------|------|
| P2 | Testing | Kein `maxLines`-Guard testbar: `maxLines = 4` auf dem Satz sowie die reinen `dp`-Konstanten in `RewardSummaryScreen.kt` (Spalten-Padding, Blockabstände, `BackgroundStarSize`, `SentenceExtraHorizontalPadding`) sind Zahlen, die in der Composable leben — außerhalb des JVM-getesteten `FinaleLayout`. Eine Erhöhung von `maxLines` ließe alle 17 `FinaleLayoutTest`-Fälle grün, obwohl jede zusätzliche Zeile das Höhenbudget belastet. Das Repo hat keine Compose-Testinfrastruktur, diese Regressionsklasse ist aktuell nicht abfangbar. Entschärfend: `weight(1f)` auf dem Mittelblock begrenzt den Inhalt, statt den Weiter-Button zu verdrängen, und `BackgroundStarSize` fällt aus der Messung — beides nimmt der Klasse die Spitze. |
| P3 | Content | Die 4-Bilder-Stufe ist auf schmalen Geräten nicht breitengeprüft: bei 52sp und einem Emoji-Advance von rund 1.2em kommen vier Bilder plus drei 16dp-Lücken auf ca. 288dp gegen 272dp nutzbare Breite bei einem 320dp breiten Gerät. Mit dem ausgelieferten Content unerreichbar — alle 18 Finales tragen 2–3 Bilder, `FinaleLayoutTest` pinnt das fest — aber erreichbar, sobald ein Autor ein viertes Bild ergänzt. Der Validator erlaubt bis zu 4. |
| P3 | Accessibility | Font-Scale-Caps sind absolut: oberhalb `fontScale = 1.0` bleiben Satz und Bilder auf ihrer bei 1.0 gerenderten Größe fixiert, eine Eltern-Accessibility-Einstellung hat dort also gar keine Wirkung. Bei 1.0 bleiben auf einem 360×640-Gerät rund 97dp ungenutzte Vertikale übrig, Wachstum wäre also finanzierbar gewesen. Ob das erlaubt werden soll, ist eine Produktentscheidung, kein Implementierungsdefault. |
| P3 | Robustheit | Keine Lesbarkeits-Untergrenze: `sentenceSizeSp(3f)` liefert 8, `(5f)` liefert 4. `coerceAtLeast(1)` ist ein Crash-Guard, keine Lesbarkeits-Garantie. Androids eigene Font-Scale-Einstellung deckelt bei 2.0, über die normale UI also nicht erreichbar — aber OEM-Skins variieren. |
| P3 | Doku-Genauigkeit | `sp → px` ist auf API 34+ nicht linear. `capEffectiveSize` nutzt ein lineares Modell (`returned_sp × fontScale ≤ base`). Die Fehlerrichtung ist konservativ — große sp werden stärker gestaucht, als das lineare Modell annimmt, der Cap schrumpft also eher zu stark als zu schwach — aber die KDoc-Aussage, das Produkt bleibe konstant, ist etwas stärker als die Plattform tatsächlich garantiert. |
| P4 | UX-Politur | Die Reveal-Reihe verschiebt sich beim Erscheinen: `AnimatedVisibility` belegt während des Verbergens keine Breite, bereits enthüllte Emojis rutschen also seitwärts, sobald ein späteres erscheint. Würde man die finale Breite der Reihe reservieren, läse sich das Reveal als "Erscheinen" statt als "Verschieben". |

## Stand des manuellen Smoke-Tests

Am 2026-07-31 auf einem Motorola edge 60 pro (1220×2712 px) über `adb` geprüft: Lektion 1
geöffnet, per Vorwärts-Chevron auf Runde 11/11, Rechenaufgabe 4 + 3 gelöst, End-Screen
fotografiert. Damit sind sieben der zehn Punkte abgenommen.

| # | Prüfpunkt | Stand |
| --- | --- | --- |
| 1 | Nach der letzten Rechen-Runde erscheint der End-Screen | ✅ |
| 2 | „Super gemacht!" steht **oben**, nicht mittig | ✅ |
| 3 | **Keine** Punktezeile („+1 · Gesamt 134") | ✅ |
| 4 | Großer, gedämpfter Stern hinter dem Inhalt | ✅ (300 dp) |
| 5 | Bilder in Satzreihenfolge, nacheinander von links | ⚠️ Bilder und Reihenfolge ✅; die **Staffelung** ist auf einem Standbild nicht prüfbar |
| 6 | Satz wird gesprochen und steht als Text da | ⚠️ Text ✅; **TTS-Ausgabe ungeprüft** |
| 7 | Speaker-Button wiederholt den Satz | ⚠️ Button vorhanden; **Funktion ungeprüft** |
| 8 | Tippen auf ein Bild spricht sein Wort | ❌ ungeprüft |
| 9 | Weiter führt zurück auf den Pfad | ✅ |
| 10 | Abbruch mit Punkten zeigt **kein** Finale | ❌ ungeprüft, siehe unten |

**Warum Punkt 10 offen blieb.** Ein Versuch, den Abbruch-Fall zu erzeugen (Lektion öffnen, eine
Aufgabe lösen, Hardware-Back), landete stattdessen im Finale: die fortgesetzte Session stand
bereits auf Runde 11/11, die gelöste Aufgabe war also die letzte und der Abschluss echt. Das
beobachtete Verhalten war damit korrekt — es hat den Abbruch-Pfad nur nicht berührt. Für Punkt 10
braucht es eine Lektion, die nachweislich **nicht** in ihrer letzten Runde steht.

Die Punkte 6, 7 und 8 hängen an Audio und lassen sich per Screenshot grundsätzlich nicht abnehmen.
Punkt 5 braucht eine Bildschirmaufnahme statt eines Standbilds.

Was zusätzlich automatisiert geprüft ist: volle Test-Suite (256 Tests grün), voller Build,
Content-Kopien-Abgleich (`cmp`, sechsmal `OK`), sowie Install + Start der echten APK mit
Logcat-Prüfung auf `ContentValidationException`/`FATAL EXCEPTION` (siehe `task-7-report.md`).
Das deckt „lädt der Validator die **App**-Assets ohne zu werfen" ab — nicht nur die Test-Kopie
unter `app/src/test/resources/content/`, gegen die die Unit-Tests laufen.
