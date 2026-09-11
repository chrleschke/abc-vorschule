# Play-Console-Checkliste: erste Veröffentlichung von Silbo

Reihenfolge der Schritte für eine neue App in der Google Play Console, mit den konkreten Werten
dieser App. Stand: 2026-09-11. Play-Console-Menüs ändern gelegentlich Namen und Reihenfolge; die
Inhalte der Schritte bleiben.

Werte, die überall gleich sind:

| Feld | Wert |
|---|---|
| App-Name (Store) | Silbo – ABC Vorschule |
| Name unter dem Icon | Silbo |
| Package / applicationId | `app.silbo.abcvorschule` (nach dem ersten Upload nicht mehr änderbar, wäre eine neue App) |
| versionCode / versionName | 1 / 1.0.0 (`app/build.gradle.kts`; versionCode muss bei jedem Upload steigen) |
| minSdk / targetSdk | 26 / 36 |
| Berechtigungen | nur `VIBRATE` |
| Standardsprache | Deutsch (Deutschland), de-DE |
| Kategorie | App > Lernen |
| Preis | kostenlos |
| Datenschutz-URL | öffentlich gehostete Fassung von `docs/release/datenschutz.md` |

> **Hinweis vorab: Voraussetzung für Produktionszugang bei persönlichen Konten**
>
> Für persönliche Entwicklerkonten, die **nach dem 13. November 2023** erstellt wurden, verlangt
> Google vor dem ersten Produktions-Release einen **geschlossenen Test mit mindestens 12 Testern,
> die 14 Tage ununterbrochen angemeldet sind**. Erst danach kann in der Play Console
> „Produktionszugang beantragen" gewählt werden; dort sind Fragen zum Testverlauf zu beantworten,
> und Google prüft, ob die Tester die App tatsächlich benutzt haben. Die Anforderung gilt pro
> App; spätere Updates derselben App brauchen keinen erneuten Test. Ursprünglich waren 20 Tester
> gefordert; seit dem 11. Dezember 2024 sind es 12.
> Organisationskonten und persönliche Konten, die vor dem 13.11.2023 erstellt wurden, sind nicht
> betroffen (die Regel ist ausdrücklich auf „new personal developer accounts" beschränkt).
>
> Quelle: Google Play Console-Hilfe, „App testing requirements for new personal developer
> accounts", https://support.google.com/googleplay/android-developer/answer/14151465
> (abgerufen 2026-09-11). Wortlaut dort: „Developers with personal accounts created after
> November 13, 2023, must run a closed test for their app with a minimum of 12 testers who have
> been opted in continuously for at least 14 days."
>
> Praktisch heißt das: 14 Tage Vorlauf zwischen dem ersten geschlossenen Test und dem
> Produktions-Release einplanen, und 12 Personen mit Google-Konto (Familie, Bekannte, Kita-Eltern)
> vorher gewinnen, die die App wirklich öffnen.

## 1. Vorbereitung (außerhalb der Console)

- [ ] Google-Play-Entwicklerkonto vorhanden und verifiziert (Identität, Zahlungsprofil,
      Adressverifizierung; bei persönlichen Konten wird die Adresse nicht öffentlich angezeigt,
      der Name des Entwicklers schon).
- [ ] Datenschutzerklärung (`docs/release/datenschutz.md`) mit echten Angaben zu Name, Anschrift
      und E-Mail gefüllt und unter einer öffentlichen HTTPS-URL gehostet (z. B. GitHub Pages).
      Die URL darf nicht auf eine Datei zum Herunterladen zeigen und muss ohne Login lesbar sein.
- [ ] Kontakt-E-Mail-Adresse für den Store festgelegt (wird öffentlich angezeigt).
- [ ] Release-Build als **Android App Bundle (.aab)** erzeugt und mit dem Upload-Schlüssel
      signiert; siehe Abschnitt „Release-Signierung" im README.
- [ ] Grafiken bereit (siehe Schritt 4), Ablage unter `docs/release/store/`.

## 2. App erstellen

Play Console > „Alle Apps" > „App erstellen":

- [ ] App-Name: `Silbo – ABC Vorschule`
- [ ] Standardsprache: **Deutsch (Deutschland) – de-DE**
- [ ] App oder Spiel: **App**
- [ ] Kostenlos oder kostenpflichtig: **Kostenlos**
      Achtung: Eine kostenlose App kann später **nicht** in eine kostenpflichtige geändert werden
      (umgekehrt schon). Für Silbo ist das gewollt.
- [ ] Erklärungen bestätigen: Programmrichtlinien für Entwickler, US-Exportgesetze.
- [ ] „App erstellen".

## 3. Dashboard „App einrichten"

Die Play Console zeigt nach dem Anlegen eine Aufgabenliste. Alle Punkte müssen vor dem ersten
Release abgeschlossen sein.

- [ ] **Datenschutzerklärung**: URL aus Schritt 1 eintragen.
- [ ] **App-Zugriff**: „Alle Funktionen sind ohne besondere Zugriffsrechte verfügbar" (kein
      Login, keine PIN; Text siehe `store-listing.md`).
- [ ] **Werbung**: „Nein, meine App enthält keine Werbung."
- [ ] **Inhaltseinstufung** (IARC-Fragebogen): E-Mail-Adresse eintragen, Kategorie „Lern-App"
      bzw. „Referenz, Nachrichten, Lernen oder Unterhaltung" wählen. Erwartete Antworten: alle
      Fragen zu Gewalt, Sexualität, Sprache, Drogen, Glücksspiel, Horror mit **Nein**;
      Nutzerinteraktion / Austausch von Inhalten **Nein**; Standortweitergabe **Nein**;
      digitale Käufe **Nein**. Erwartetes Ergebnis: USK 0, PEGI 3, ESRB „Everyone", „Jedes Alter".
- [ ] **Zielgruppe und Inhalte**: Altersgruppen **bis 5** und **6–8** ankreuzen. Damit wird die
      App als „für Kinder konzipiert" eingestuft und die Familienrichtlinie greift. Werbung:
      Nein. Fragen nach Datenerhebung und externen Links: Nein. Hinweis der Console beachten,
      dass der Store-Eintrag (Screenshots, Texte) keine für Kinder ungeeigneten Inhalte zeigen
      darf. Für die Zielgruppe unter 13 verlangt Google außerdem, dass die App ausschließlich
      zertifizierte Werbe-SDKs nutzt; Silbo hat keine, also nichts zu tun.
- [ ] **Nachrichten-Apps**: Nein, es handelt sich nicht um eine Nachrichten-App.
- [ ] **Apps zur Kontaktverfolgung und zum COVID-19-Status**: Nein.
- [ ] **Datensicherheit**: „Erhebt oder teilt Ihre App Nutzerdaten?" **Nein**. Alle weiteren
      Fragen entfallen bzw. „Nicht zutreffend" (Details und Begründungen in
      `store-listing.md`, Abschnitt „Datensicherheit"). Als Ergebnis zeigt der Store „Es
      werden keine Daten erhoben".
- [ ] **Behörden-Apps**: Nein.
- [ ] **Finanz-Features**: „Meine App bietet keine der genannten Finanz-Features."
- [ ] **Gesundheit**: „Meine App enthält keine Gesundheitsfunktionen."
- [ ] **App-Kategorie und Kontaktdetails**: App, Kategorie „Lernen", Tags (bis zu 5, Vorschläge
      in `store-listing.md`), E-Mail-Adresse (Pflicht), Website optional.
- [ ] **Store-Eintrag einrichten**: siehe Schritt 4.

## 4. Haupt-Store-Eintrag

Play Console > „Wachstum" > „Store-Präsenz" > „Haupt-Store-Eintrag". Texte aus
`docs/release/store-listing.md` übernehmen:

- [ ] App-Name: `Silbo – ABC Vorschule` (21/30 Zeichen)
- [ ] Kurzbeschreibung (78/80 Zeichen)
- [ ] Vollständige Beschreibung (2941/4000 Zeichen)
- [ ] **App-Symbol**: 512 × 512 px, PNG (32 Bit) oder JPEG, maximal 1 MB, keine Transparenz,
      keine abgerundeten Ecken (Play maskiert selbst). Motiv identisch mit dem Adaptive Icon der
      App (Pfad-Landschaft mit „ABC"-Holzschild, `docs/PRODUCT_PRINCIPLES.md` §10).
      Datei: `docs/release/store/icon-512.png`
- [ ] **Funktionsgrafik (Feature-Grafik)**: 1024 × 500 px, PNG oder JPEG, maximal 15 MB,
      Pflichtfeld. Datei: `docs/release/store/feature-graphic-1024x500.png`
- [ ] **Smartphone-Screenshots**: mindestens 2, maximal 8; PNG oder JPEG, maximal 8 MB je Bild;
      Seitenverhältnis 16:9 oder 9:16; jede Seite zwischen 320 und 3840 px. Die App läuft nur im
      Hochformat, also 9:16 (z. B. 720 × 1280 oder 1080 × 1920; 1080 × 2400 ist 9:20 und wird
      **nicht** akzeptiert, ggf. auf 9:16 zuschneiden).
      Screenshots dürfen keine für Kinder ungeeigneten Inhalte und keine Geräte-Statusleiste mit
      persönlichen Daten zeigen.
      Vorhanden (Emulator `uxreview`, 720 × 1280, Vollbild ohne Statusleiste), in dieser
      Reihenfolge hochladen: `docs/release/store/screenshots/01-pfad.png`,
      `02-buchstabe-nachfahren.png`, `03-buchstaben-jagd.png`, `04-silben-verschmelzer.png`,
      `05-wort-bauer.png`, `06-laut-fresser.png`. Optional später durch Aufnahmen vom
      Motorola-Testgerät ersetzen (höhere Auflösung; dort vor dem Screenshot `font_scale` auf
      1.0 stellen und das Seitenverhältnis auf 9:16 zuschneiden).
- [ ] Tablet-Screenshots (7 Zoll / 10 Zoll): optional, aber empfohlen, wenn Tablets unterstützt
      werden; gleiche Formatregeln.
- [ ] Video (YouTube-URL): optional, leer lassen.
- [ ] Speichern; „Vorschau" der Store-Seite prüfen (Gedankenstrich im Titel korrekt?).

## 5. Play App Signing

Play Console > „Test und Release" > „Einrichtung" > „App-Integrität" (bzw. beim ersten Upload
im Release-Assistenten):

- [ ] **Play App Signing** akzeptieren (für neue Apps Pflicht). Google erzeugt und verwahrt den
      **App-Signaturschlüssel**, mit dem die an Nutzer ausgelieferten APKs signiert werden.
- [ ] Der Entwickler behält nur den **Upload-Schlüssel** (Keystore aus dem Abschnitt
      „Release-Signierung" im README). Mit ihm wird jedes `.aab` signiert, das in die Console
      geladen wird. Option „Von Google generierten Schlüssel verwenden" wählen, wenn kein
      bestehender Signaturschlüssel übernommen werden muss (bei einer neuen App der Normalfall).
- [ ] Upload-Keystore und Passwörter sicher aufbewahren (nicht im Repository). Geht der
      Upload-Schlüssel verloren, kann über den Play-Support ein neuer registriert werden; der
      App-Signaturschlüssel bleibt bei Google unverändert.
- [ ] Nach dem ersten Upload den SHA-256-Fingerabdruck des App-Signaturschlüssels notieren
      (für Silbo aktuell nicht nötig, da keine Google-Dienste eingebunden sind).

## 6. Interner Test

Play Console > „Test und Release" > „Tests" > „Interner Test":

- [ ] Testerliste anlegen (E-Mail-Adressen von Google-Konten, bis zu 100).
- [ ] Neuen Release erstellen, `.aab` hochladen (versionCode 1, versionName 1.0.0).
- [ ] Release-Name (z. B. `1.0.0 (1)`) und Versionshinweise `<de-DE>Erste Version.</de-DE>`.
- [ ] Prüfen und einführen. Der Opt-in-Link wird an die Tester verteilt; Installation über
      Play Store testen (Icon, Name unter dem Icon „Silbo", Sprachausgabe auf einem Gerät ohne
      deutsche TTS-Stimme, Fortschritt nach App-Neustart, Elternbereich per langem Druck).
- [ ] Pre-Launch-Report in der Console ansehen (Google testet automatisch auf mehreren Geräten
      und meldet Abstürze, Barrierefreiheits- und Sicherheitshinweise).

## 7. Geschlossener Test (Pflichtschritt bei neuen persönlichen Konten)

Play Console > „Tests" > „Geschlossener Test" > Track „Alpha" (oder eigener Name):

- [ ] Testerliste mit **mindestens 12** Google-Konten anlegen (mehr einplanen, da die Zahl der
      tatsächlich angemeldeten Tester zählt, nicht die Länge der Liste).
- [ ] Release erstellen (kann derselbe `.aab` wie im internen Test sein) und einführen. Der
      geschlossene Test durchläuft die Google-Prüfung; das kann einige Tage dauern.
- [ ] Opt-in-Link verteilen; alle Tester müssen dem Test beitreten und die App installieren.
- [ ] **14 Tage** warten, während mindestens 12 Tester durchgehend angemeldet bleiben. Rückmeldungen
      sammeln; Fehler in Zwischen-Releases beheben (versionCode erhöhen).
- [ ] Danach im Dashboard **„Produktionszugang beantragen"**: Fragen zum Testverlauf beantworten
      (wer getestet hat, was gefunden wurde, wie die App bereit für Nutzer ist). Google antwortet
      in der Regel innerhalb weniger Tage.

Konten, die vor dem 13.11.2023 erstellt wurden, und Organisationskonten können diesen Schritt
überspringen; ein geschlossener Test ist dort freiwillig.

## 8. Produktion

Play Console > „Test und Release" > „Produktion":

- [ ] **Länder/Regionen** wählen: mindestens Deutschland, Österreich, Schweiz, Liechtenstein,
      Luxemburg, Belgien, Südtirol (Italien); alle Länder sind ebenfalls möglich, da die App keine
      rechtlichen Sonderfälle hat. Der Store-Eintrag existiert nur auf Deutsch.
- [ ] Release erstellen, `.aab` hochladen (oder aus dem geschlossenen Test befördern), Versionshinweise.
- [ ] „Prüfen und veröffentlichen". Erste Veröffentlichungen werden von Google geprüft; bei
      Kinder-Apps ist die Prüfung erfahrungsgemäß gründlicher (bis zu 7 Tage einplanen).
- [ ] Optional: **Verwaltete Veröffentlichung** aktivieren, um nach bestandener Prüfung den
      Zeitpunkt selbst zu wählen.
- [ ] Nach der Freigabe: Store-Seite im Play Store aufrufen und prüfen (Titel mit Gedankenstrich,
      Kurzbeschreibung, Screenshots, Hinweis „Es werden keine Daten erhoben", Altersfreigabe).

## 9. Nach dem Release

- [ ] Datenschutz-URL dauerhaft erreichbar halten; bei Änderungen an der App (insbesondere jede
      neue Berechtigung oder jede Form von Netzwerkzugriff) Datenschutzerklärung und
      Datensicherheitsformular aktualisieren.
- [ ] Für jedes Update: versionCode erhöhen, `.aab` mit dem Upload-Schlüssel signieren, direkt in
      Produktion (kein erneuter Pflichttest).
- [ ] Bewertungen und Pre-Launch-Reports gelegentlich prüfen.

## Verweise

- Store-Texte und Formularantworten: `docs/release/store-listing.md`
- Datenschutzerklärung (zu hosten): `docs/release/datenschutz.md`
- Grafiken (Icon 512, Feature-Grafik, Screenshots): `docs/release/store/`
- Signierung, Upload-Keystore und Release-Build: README, Abschnitt „Release-Signierung"
- Produktprinzipien (Name, Icon, Zielgruppe): `docs/PRODUCT_PRINCIPLES.md` §1, §2, §10
