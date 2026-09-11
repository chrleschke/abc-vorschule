# Store-Eintrag: Silbo – ABC Vorschule

Texte und Formularantworten zum Einfügen in die Google Play Console. Stand: 2026-09-11.
Package: `app.abcvorschule`, Version 1.0.0 (versionCode 1). Alle Zeichenzahlen mit `wc -m` gezählt,
ohne abschließenden Zeilenumbruch.

## App-Name (max. 30 Zeichen)

```
Silbo – ABC Vorschule
```

21 Zeichen. Gedankenstrich (U+2013) mit Leerzeichen, kein Bindestrich. Unter dem Launcher-Icon steht
nur „Silbo" (`app_name`); der Zusatz „ABC Vorschule" lebt ausschließlich im Store-Titel.

## Kurzbeschreibung (max. 80 Zeichen)

```
Silbe für Silbe zum Lesen. Lesen und Rechnen ab 4 Jahren, offline, werbefrei.
```

78 Zeichen. Enthält den Claim „Silbe für Silbe zum Lesen."

## Vollständige Beschreibung (max. 4000 Zeichen)

```
Silbo ist eine Lern-App für Kinder im Vorschul- und Erstlesealter (etwa 4 bis 7 Jahre). Sie führt Silbe für Silbe zum Lesen: von einzelnen Lauten und Buchstaben über Silben und Wörter bis zu kurzen Sätzen. Dazu kommt in jeder Lektion eine kleine Rechenrunde mit Plus, Minus und gleichen Gruppen im Zahlenraum bis 20, in späteren Lektionen bis 30.

FÜR WEN
Für Kinder, die noch nicht lesen können. Jede Aufgabe wird vorgesprochen, jedes Bild, jeder Buchstabe und jede Silbe lässt sich antippen und anhören. Die Bedienung läuft über Bilder, Symbole und Sprache, nicht über Text. Ein Kind kann die App allein benutzen, ohne dass ein Erwachsener vorlesen muss.

WIE EINE LEKTION ABLÄUFT
Der Einstieg ist ein Lernpfad mit 34 Lektionen, angeordnet wie in einer Fibel. Jede Lektion führt einen oder mehrere neue Buchstaben ein und übt sie in fester Reihenfolge:
1. Buchstaben nachspuren: Das Kind fährt den Buchstaben mit dem Finger nach und sammelt dabei Sterne.
2. Silben verschmelzen: Zwei Laute werden zusammengeschoben und zur Silbe gelesen.
3. Wörter bauen: Silben und Buchstaben werden in die passenden Felder unter einem Bild gelegt.
4. Sätze ordnen: Wortschilder werden zu einem kurzen Satz sortiert.
5. Sätze verstehen: Ein Satz wird vorgelesen, das Kind wählt das passende Bild.
6. Rechnen: Mengen aus den Bildern der Lektion werden zusammengezählt, weggenommen oder in gleiche Gruppen geteilt.
Dazwischen gibt es kleine Suchspiele, in denen der geübte Buchstabe oder die Silbe unter anderen gefunden wird. Falsche Antworten werden freundlich vorgesprochen, es gibt keine Strafen. Wer nicht weiterkommt, kann sich die Lösung zeigen lassen. Am Ende jeder Lektion steht ein kurzer Satz mit Bildern als Belohnung. Die nächste Lektion öffnet sich, sobald die vorherige durchgespielt ist.

WAS DIE APP NICHT TUT
- Keine Werbung, keine In-App-Käufe, keine Abonnements.
- Kein Konto, keine Anmeldung, keine E-Mail-Adresse.
- Keine Internetverbindung: Die App hat keine Internet-Berechtigung und funktioniert vollständig offline.
- Keine Datenerhebung, keine Analyse-Dienste, keine Absturzberichte, keine Dienste von Drittanbietern.
- Keine Links nach außen im Bereich für Kinder.
Der Lernfortschritt wird nur auf dem Gerät gespeichert und mit dem Deinstallieren gelöscht.

SPRACHAUSGABE
Alle Ansagen sind als Audio-Clips in der App enthalten. Für Zahlen und in seltenen Fällen ohne passenden Clip nutzt die App die Sprachausgabe von Android auf dem Gerät. Eine installierte deutsche Stimme wird empfohlen.

ELTERNBEREICH
Über das Drei-Punkte-Menü auf dem Lernpfad (lange drücken) erreichen Eltern zwei Einstellungen: die Hilfestufe für die Rechen-Hilfe (Auto, Mit Hilfe, Ohne Hilfe) und die Freigabe der Lektionsreihenfolge, mit der alle Lektionen frei wählbar werden. Mehr Einstellungen gibt es nicht.

KOSTENLOS
Silbo ist vollständig kostenlos. Alle 34 Lektionen sind ohne Einschränkung enthalten.
```

2941 Zeichen.

## Kategorie und Tags

- App oder Spiel: **App**
- Kategorie: **Lernen** (Education)
- Tags (Vorschläge, in der Play Console maximal 5 wählbar): Lesen lernen, Vorschule, Buchstaben,
  Rechnen lernen, Kinder-Lernspiel

## App-Zugriff

Antwort: **Alle Funktionen sind ohne besondere Zugriffsrechte verfügbar.**
Es gibt kein Konto, kein Login und keine PIN. Der Elternbereich öffnet sich über langen Druck auf das
Drei-Punkte-Menü; das ist keine Zugangsbeschränkung im Sinne des Formulars, sondern eine
Bediensperre gegen versehentliches Öffnen durch das Kind.

## Kontakt

- E-Mail-Adresse für den Store-Eintrag: **[E-Mail-Adresse]** (Pflichtfeld, öffentlich sichtbar)
- Website: optional, leer lassen oder URL der Datenschutzerklärung
- Datenschutz-URL: URL, unter der `docs/release/datenschutz.md` gehostet wird (Pflicht für Kinder-Apps)

## Formular „Datensicherheit" (Data safety)

| Frage | Antwort | Begründung |
|---|---|---|
| Erhebt oder teilt Ihre App Nutzerdaten, die unter die Richtlinie fallen? | **Nein** | Die App hat keine Internet-Berechtigung (einzige Berechtigung: VIBRATE). Es findet keine Übertragung an den Entwickler oder an Dritte statt. Der Lernfortschritt bleibt im App-Speicher des Geräts (Jetpack DataStore). |
| Werden alle Nutzerdaten bei der Übertragung verschlüsselt? | **Nicht zutreffend** | Es werden keine Daten übertragen. Das Formular blendet diese Frage nach „Nein" oben aus. |
| Können Nutzer die Löschung ihrer Daten beantragen? | **Nicht zutreffend** | Beim Entwickler liegen keine Daten. Lokale Daten löscht der Nutzer durch Deinstallieren oder über „Speicher löschen" in den Android-Einstellungen. |
| Hält sich die App an die Google-Play-Richtlinie für Familien? | **Ja** | Siehe Abschnitt „Zielgruppe und Inhalte". |
| Hat die App eine unabhängige Sicherheitsprüfung durchlaufen? | **Nein** | Freiwillig; nicht durchgeführt. |
| Datentypen (Standort, personenbezogene Daten, Finanzen, Gesundheit, Nachrichten, Fotos/Videos, Audio, Dateien, Kalender, Kontakte, App-Aktivität, Web-Browsing, App-Informationen und Leistung, Geräte-IDs) | **Keiner ausgewählt** | Keiner dieser Datentypen wird erhoben oder geteilt. Insbesondere: keine Crash-Logs, keine Diagnosedaten, kein Mikrofon, keine Kamera, kein Standort, keine Kontakte. |

Hinweise:
- Das lokal gespeicherte Datum (Fortschritt, Elterneinstellungen) gilt laut Google-Definition nicht
  als „erhoben", weil es das Gerät nicht verlässt und nur zur Laufzeit der App auf dem Gerät
  verarbeitet wird.
- Android-Backup (`allowBackup="true"`) überträgt die App-Daten über den Sicherungsdienst des
  Geräts, den der Nutzer selbst konfiguriert. Google stuft das nicht als Datenerhebung durch den
  Entwickler ein; die Datenschutzerklärung erwähnt es trotzdem.
- Die System-Sprachausgabe (Android-TTS) läuft auf dem Gerät. Die App übergibt den vorzulesenden
  Text an die vom Nutzer installierte TTS-Engine; die App selbst sendet nichts.

## Formular „Zielgruppe und Inhalte"

- Zielaltersgruppen: **bis 5 Jahre** und **6 bis 8 Jahre** (beide ankreuzen; Kernzielgruppe 4–7).
  Damit gilt die App als „für Kinder konzipiert" und muss die Familienrichtlinie erfüllen.
- Spricht der Store-Eintrag unbeabsichtigt Kinder an? Entfällt, die App ist ausdrücklich für Kinder.
- Werbung: **Nein**, die App enthält keine Werbung und kein Werbe-SDK.
- Enthält die App Inhalte von Drittanbietern oder Links nach außen? **Nein.**

Erklärung zur Einhaltung der Google-Play-Richtlinien für Familien:
- Keine Werbung, keine In-App-Käufe, keine Monetarisierung.
- Keine Erhebung personenbezogener Daten, keine Analyse-, Crash- oder Werbe-SDKs, keine
  Internet-Berechtigung.
- Keine externen Links, kein Browser-Aufruf, kein Store-Verweis, kein Teilen-Dialog im gesamten
  App-Code (siehe Prüfergebnis unten).
- Keine Berechtigungen außer VIBRATE; kein Mikrofon, keine Kamera, kein Standort.
- Inhalte: Buchstaben, Silben, Wörter, kurze Sätze mit Alltags- und Tier-Motiven (Emojis) und
  einfache Rechenaufgaben. Keine Gewalt, keine erschreckenden Inhalte, kein Nutzer-generierter
  Inhalt, keine Kommunikation zwischen Nutzern.
- Elternbereich: nur zwei Einstellungen (Hilfestufe, Freigabe der Lektionsreihenfolge), keine
  Links, keine Käufe, keine Datenfreigabe.

### Prüfergebnis externe Links und Intents (Stand 2026-09-11)

Geprüft wurde `app/src/main/java` (alle Kotlin-Dateien), `app/src/main/res` und das Manifest auf
`Intent(`, `startActivity`, `Uri.parse`, `ACTION_VIEW`, `UriHandler`/`openUri`, `http://`,
`https://`, `mailto:`, `market://`, `play.google`, `Settings.`.

- Ergebnis: **kein Treffer** im App-Code und in den Ressourcen. Die App startet keine fremden
  Activities, öffnet keinen Browser, keine Mail-App, keinen Store und keine Systemeinstellungen.
- Einziger Intent-Bezug ist die `<queries>`-Deklaration `android.intent.action.TTS_SERVICE` im
  Manifest. Sie erlaubt der App nur, die installierte Sprachausgabe-Engine zu sehen (nötig für
  `TextToSpeech` ab Android 11); sie öffnet nichts und verlässt die App nicht.
- Manifest: einzige Activity ist `MainActivity` (Launcher), einzige Berechtigung `VIBRATE`.
- Eine frühere Seite „TTS Debug" im Elternmenü ist entfernt (`docs/PRODUCT_PRINCIPLES.md` §6);
  die Datei `debug/TtsDebugEntry.kt` enthält nur Datenklassen ohne UI und ohne Intents.

## Inhaltseinstufung (IARC-Fragebogen)

Erwartete Antworten: Kategorie „Referenz, Nachrichten, Lernen oder Unterhaltung" bzw. „Lern-App";
alle Fragen zu Gewalt, sexuellen Inhalten, Sprache, kontrollierten Substanzen, Glücksspiel,
Nutzerinteraktion, Standortweitergabe, Käufen: **Nein**. Erwartetes Ergebnis: USK 0 / PEGI 3 /
„Jedes Alter".
