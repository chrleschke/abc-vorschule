# Known Residuals — feat/lektions-finale

Source: Task-Reviews während der Umsetzung (2026-07-31), bereits als "bewusst zurückgestellt"
adjudiziert. Diese Notiz existiert, damit ein späterer Leser die Entscheidung nachvollziehen
kann, ohne die Review-Historie neu auszugraben.

## Accepted residuals

| Severity | Area | Note |
|----------|------|------|
| P2 | Testing | Kein `maxLines`-Guard testbar: `maxLines = 3` auf dem Satz sowie die reinen `dp`-Konstanten in `RewardSummaryScreen.kt` sind layout-entscheidende Zahlen, die in der Composable leben — außerhalb des JVM-getesteten `FinaleLayout`. Eine Änderung von `maxLines` auf 5 ließe alle 17 `FinaleLayoutTest`-Fälle grün, obwohl sie 64dp Höhe zum Screen-Budget hinzufügt. Das Repo hat keine Compose-Testinfrastruktur, diese Regressionsklasse ist aktuell nicht abfangbar. |
| P3 | Content | Die 4-Bilder-Stufe ist auf schmalen Geräten nicht breitengeprüft: bei 52sp und einem Emoji-Advance von rund 1.2em kommen vier Bilder plus drei 16dp-Lücken auf ca. 288dp gegen 272dp nutzbare Breite bei einem 320dp breiten Gerät. Mit dem ausgelieferten Content unerreichbar — alle 18 Finales tragen 2–3 Bilder, `FinaleLayoutTest` pinnt das fest — aber erreichbar, sobald ein Autor ein viertes Bild ergänzt. Der Validator erlaubt bis zu 4. |
| P3 | Accessibility | Font-Scale-Caps sind absolut: oberhalb `fontScale = 1.0` bleiben Satz und Bilder auf ihrer bei 1.0 gerenderten Größe fixiert, eine Eltern-Accessibility-Einstellung hat dort also gar keine Wirkung. Bei 1.0 bleiben auf einem 360×640-Gerät rund 97dp ungenutzte Vertikale übrig, Wachstum wäre also finanzierbar gewesen. Ob das erlaubt werden soll, ist eine Produktentscheidung, kein Implementierungsdefault. |
| P3 | Robustheit | Keine Lesbarkeits-Untergrenze: `sentenceSizeSp(3f)` liefert 8, `(5f)` liefert 4. `coerceAtLeast(1)` ist ein Crash-Guard, keine Lesbarkeits-Garantie. Androids eigene Font-Scale-Einstellung deckelt bei 2.0, über die normale UI also nicht erreichbar — aber OEM-Skins variieren. |
| P3 | Doku-Genauigkeit | `sp → px` ist auf API 34+ nicht linear. `capEffectiveSize` nutzt ein lineares Modell (`returned_sp × fontScale ≤ base`). Die Fehlerrichtung ist konservativ — große sp werden stärker gestaucht, als das lineare Modell annimmt, der Cap schrumpft also eher zu stark als zu schwach — aber die KDoc-Aussage, das Produkt bleibe konstant, ist etwas stärker als die Plattform tatsächlich garantiert. |
| P4 | UX-Politur | Die Reveal-Reihe verschiebt sich beim Erscheinen: `AnimatedVisibility` belegt während des Verbergens keine Breite, bereits enthüllte Emojis rutschen also seitwärts, sobald ein späteres erscheint. Würde man die finale Breite der Reihe reservieren, läse sich das Reveal als "Erscheinen" statt als "Verschieben". |

## Noch ausstehend: manueller Smoke-Test

Der 10-Punkte-Walkthrough aus dem Plan (Task 7, Step 4) verlangt, eine Lektion vollständig
durchzuspielen (Drag & Drop, Fingerspuren) und danach eine zweite abzubrechen. Das kann kein
Skript per `adb`-Taps leisten — die Trainer brauchen echte Gesten, ein halb durchgeführter
Lauf auf dem persönlichen Gerät des Owners wäre schlimmer als keiner. Das bleibt für einen
Menschen offen. Die 10 Prüfpunkte an einem Ort:

1. Nach der letzten Rechen-Runde erscheint der End-Screen.
2. „Super gemacht!" steht **oben**, nicht mittig.
3. Es steht **keine** Punktezeile („+1 · Gesamt 134") auf dem Screen.
4. Ein großer, gedämpfter Stern liegt hinter dem Inhalt.
5. Drei Bilder erscheinen nacheinander von links: 👩 🐭 🍎.
6. Der Satz „Mama Maus mampft einen dicken Apfel!" wird gesprochen und steht als Text da.
7. Der Speaker-Button wiederholt den Satz.
8. Tippen auf 🍎 sagt „Apfel".
9. Weiter führt zurück auf den Pfad.
10. Lektion 2 öffnen, eine Aufgabe richtig lösen, Hardware-Back drücken: der End-Screen zeigt
    nur Header, Stern und Weiter — **kein** Satz, **keine** Bilder.

Was Task 7 stattdessen automatisiert geprüft hat: volle Test-Suite (256 Tests grün), voller
Build, Content-Kopien-Abgleich (`cmp`, sechsmal `OK`), sowie Install + Start der echten APK auf
dem verbundenen Gerät mit Logcat-Prüfung auf `ContentValidationException`/`FATAL EXCEPTION` und
einem Screenshot, der den End-Screen tatsächlich zeigt (siehe `task-7-report.md`). Das deckt
"lädt der Validator die echten Assets ohne zu werfen" ab, aber nicht die Interaktionsschritte
1–10 oben.
