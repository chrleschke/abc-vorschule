# Known Residuals — feat/lektions-finale

Source: Task-Reviews während der Umsetzung (2026-07-31), bereits als "bewusst zurückgestellt"
adjudiziert. Diese Notiz existiert, damit ein späterer Leser die Entscheidung nachvollziehen
kann, ohne die Review-Historie neu auszugraben.

## Accepted residuals

| Severity | Area | Note |
|----------|------|------|
| P2 | Testing | Kein `maxLines`-Guard testbar: `maxLines = 4` auf dem Satz sowie die reinen `dp`-Konstanten in `RewardSummaryScreen.kt` (Spalten-Padding, Blockabstände, `BackgroundStarSize`, `SentenceExtraHorizontalPadding`) sind Zahlen, die in der Composable leben — außerhalb des JVM-getesteten `FinaleLayout`. Eine Erhöhung von `maxLines` ließe alle 17 `FinaleLayoutTest`-Fälle grün, obwohl jede zusätzliche Zeile das Höhenbudget belastet. Das Repo hat keine Compose-Testinfrastruktur, diese Regressionsklasse ist aktuell nicht abfangbar. Entschärfend: `weight(1f)` auf dem Mittelblock begrenzt den Inhalt, statt den Weiter-Button zu verdrängen, und `BackgroundStarSize` fällt aus der Messung — beides nimmt der Klasse die Spitze. |
| P3 | Content | **Die 4-Bilder-Stufe ist knapp und annahmeabhängig.** `FinaleLayout.pictureRowWidthDp(count, fontScale)` rechnet die Reihenbreite jetzt aus (inkl. der 16dp-Lücken, die vorher nur als Literal im Composable lagen) und ein Test pinnt sie gegen die schmalste unterstützte Inhaltsbreite: 320dp-Gerät − 2×24dp Spaltenpadding = **272dp**. Bei `fontScale = 1.0`: 2 Bilder → 144dp, 3 → 224dp, 4 → 256dp (52sp-Stufe + 3×16dp). Alle passen — aber **4 Bilder nur mit 16dp Reserve**, und das unter der ausdrücklichen Annahme, dass ein Emoji so breit ist wie seine Schriftgröße (1:1). Emoji-Fonts polstern Glyphen teils für Lesbarkeit; bei einem Faktor von 1.15–1.2× würde die 4er-Reihe die 272dp überschreiten. Ohne instrumentierte Tests (die dieses Repo nicht hat) ist die echte Glyphenbreite nicht messbar. Mit dem ausgelieferten Content unerreichbar: alle 18 Finales tragen 2–3 Bilder. **Offene Produktentscheidung:** `MaxFinalePictures` von 4 auf 3 senken (kostet nichts, kein Satz braucht 4) oder die 52sp-Stufe weiter verkleinern. **Korrektur einer früheren Fassung dieser Notiz:** sie behauptete, `FinaleLayoutTest` pinne die Bildzahl auf 2–3. Das tat kein Test — ein viertes Bild wäre mit grüner Suite durchgegangen. Die Whole-Branch-Review hat das aufgedeckt. |
| P3 | Accessibility | Font-Scale-Caps sind absolut: oberhalb `fontScale = 1.0` bleiben Satz und Bilder auf ihrer bei 1.0 gerenderten Größe fixiert, eine Eltern-Accessibility-Einstellung hat dort also gar keine Wirkung. Bei 1.0 bleiben auf einem 360×640-Gerät rund 97dp ungenutzte Vertikale übrig, Wachstum wäre also finanzierbar gewesen. Ob das erlaubt werden soll, ist eine Produktentscheidung, kein Implementierungsdefault. |
| P3 | Robustheit | Keine Lesbarkeits-Untergrenze: `sentenceSizeSp(3f)` liefert 8, `(5f)` liefert 4. `coerceAtLeast(1)` ist ein Crash-Guard, keine Lesbarkeits-Garantie. Androids eigene Font-Scale-Einstellung deckelt bei 2.0, über die normale UI also nicht erreichbar — aber OEM-Skins variieren. |
| P3 | Doku-Genauigkeit | `sp → px` ist auf API 34+ nicht linear. `capEffectiveSize` nutzt ein lineares Modell (`returned_sp × fontScale ≤ base`). Die Fehlerrichtung ist konservativ — große sp werden stärker gestaucht, als das lineare Modell annimmt, der Cap schrumpft also eher zu stark als zu schwach — aber die KDoc-Aussage, das Produkt bleibe konstant, ist etwas stärker als die Plattform tatsächlich garantiert. |
| P4 | UX-Politur | Die Reveal-Reihe verschiebt sich beim Erscheinen: `AnimatedVisibility` belegt während des Verbergens keine Breite, bereits enthüllte Emojis rutschen also seitwärts, sobald ein späteres erscheint. Würde man die finale Breite der Reihe reservieren, läse sich das Reveal als "Erscheinen" statt als "Verschieben". |
| P3 | Accessibility | Im pessimistischsten Fall reicht die Höhe um ~16dp nicht: 320×568 dp, `fontScale` 2.0, ein Satz der alle vier Zeilen braucht. Der Weiter-Button bleibt davon unberührt, weil `weight(1f)` inhaltsunabhängig ist; betroffen wäre allenfalls ein Randstreifen der Speaker-Fläche. Reale Sätze belegen zwei bis drei Zeilen. (Diese Zahl stand vorher nur in der Spec, die auf diese Datei verwies — jetzt steht sie hier.) |
| P4 | Konsistenz | Der Plan `docs/superpowers/plans/2026-07-31-lektions-finale.md` verweist an vier Stellen auf `LessonEmojis`, das auf diesem Branch nicht existiert (es liegt auf `feat/pfad-screen-kindgerecht`). Bewusst als historischer Stand belassen: Pläne sind datierte Artefakte, sie nachträglich an die Realität anzupassen zerstört mehr Information, als es repariert. |

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
| 10 | Abbruch mit Punkten zeigt **kein** Finale | ⛔ gegenstandslos — main (`664e440`) leitet den Abbruch direkt zum Pfad, der End-Screen wird dabei gar nicht mehr erreicht |

**Punkt 10 ist entfallen.** Ein erster Versuch, den Abbruch-Fall zu erzeugen (Lektion öffnen, eine
Aufgabe lösen, Hardware-Back), landete stattdessen im Finale: die fortgesetzte Session stand
bereits auf Runde 11/11, die gelöste Aufgabe war also die letzte und der Abschluss echt. Kurz
darauf wurde `main` in diesen Branch gemergt — und `664e440` entfernt den Abbruch-Pfad zum
End-Screen vollständig (`onBackPressed()` → `exitLesson()` → direkt zum Pfad). Der Prüfpunkt hat
damit kein Objekt mehr. Die schlanke Variante bleibt als Defensivpfad im Code, ist aber nur noch
über eine nicht auflösbare `finaleId` erreichbar, was der Validator ausschließt.

Die Punkte 6, 7 und 8 hängen an Audio und lassen sich per Screenshot grundsätzlich nicht abnehmen.
Punkt 5 braucht eine Bildschirmaufnahme statt eines Standbilds.

Was zusätzlich automatisiert geprüft ist: volle Test-Suite (256 Tests grün), voller Build,
Content-Kopien-Abgleich (`cmp`, sechsmal `OK`), sowie Install + Start der echten APK mit
Logcat-Prüfung auf `ContentValidationException`/`FATAL EXCEPTION` (siehe `task-7-report.md`).
Das deckt „lädt der Validator die **App**-Assets ohne zu werfen" ab — nicht nur die Test-Kopie
unter `app/src/test/resources/content/`, gegen die die Unit-Tests laufen.
