# Datenschutzerklärung für die App „Silbo – ABC Vorschule"

Stand: 11. September 2026

Diese Datenschutzerklärung gilt für die Android-App „Silbo" (Store-Titel „Silbo – ABC Vorschule",
Paketname `app.abcvorschule`), die über Google Play vertrieben wird. Sie ist unter einer
öffentlichen Adresse abrufbar, weil Google Play für Apps, die sich an Kinder richten, eine
Datenschutzerklärung verlangt, auch wenn die App keine Daten erhebt.

## 1. Verantwortlicher

Verantwortlich im Sinne der Datenschutz-Grundverordnung (DSGVO) ist:

[Vorname Nachname]
[Straße Hausnummer, PLZ Ort]
[Land]
E-Mail: [E-Mail-Adresse]

Der Verantwortliche ist eine Privatperson. Ein Datenschutzbeauftragter ist nicht bestellt, weil die
gesetzlichen Voraussetzungen dafür nicht vorliegen.

## 2. Grundsatz: keine Erhebung, keine Übertragung

Die App erhebt, speichert oder übermittelt keine personenbezogenen Daten an den Verantwortlichen
oder an Dritte. Technisch ist das so abgesichert:

- Die App fordert keine Internet-Berechtigung an (`android.permission.INTERNET` fehlt im
  Manifest). Sie kann deshalb keine Netzwerkverbindung aufbauen, weder zum Verantwortlichen noch
  zu Dritten.
- Die App enthält keine Analyse-, Werbe- oder Absturzbericht-Bibliotheken und keine Dienste von
  Drittanbietern.
- Die App hat kein Nutzerkonto, kein Login und fragt keine Angaben zur Person ab (Name, Alter,
  E-Mail-Adresse oder Ähnliches).
- Die App ist kostenlos, werbefrei und enthält keine In-App-Käufe.

Da keine Daten an den Verantwortlichen gelangen, findet durch den Verantwortlichen keine
Verarbeitung personenbezogener Daten im Sinne von Art. 4 Nr. 2 DSGVO statt. Die folgenden
Abschnitte beschreiben der Vollständigkeit halber, was auf dem Gerät selbst geschieht.

## 3. Lokal auf dem Gerät gespeicherte Daten

Die App speichert folgende Informationen ausschließlich im privaten Speicherbereich der App auf dem
Gerät (Android Jetpack DataStore):

- **Lernfortschritt**: welche Lektionen und Übungen begonnen oder abgeschlossen wurden, Punkte
  und Sterne je Lektion, ob eine Hilfe („Zeig mir") genutzt wurde.
- **Einstellungen aus dem Elternbereich**: die gewählte Hilfestufe (Auto / Mit Hilfe / Ohne Hilfe)
  und ob die Lektionsreihenfolge frei wählbar ist.

Zweck dieser Speicherung ist allein, dass die App beim nächsten Start dort weitermacht, wo das
Kind aufgehört hat, und die Elterneinstellungen behält. Die Daten enthalten keinen Namen, kein
Profil und keine Kennung, die eine Person identifiziert. Sie werden nicht ausgewertet und nicht
übertragen.

**Löschung:** Die Daten werden vollständig gelöscht, wenn die App deinstalliert wird oder wenn
in den Android-Einstellungen unter „Apps > Silbo > Speicher" die Daten der App gelöscht werden.
Eine Löschfunktion innerhalb der App gibt es nicht.

**Android-Sicherung (Backup):** Die App erlaubt Android, ihre lokalen Daten in die
Geräte-Sicherung einzubeziehen (`android:allowBackup="true"`). Ob und wohin eine Sicherung
erfolgt, bestimmt der Nutzer in den Einstellungen seines Geräts (z. B. „Google One-Sicherung"
unter dem Google-Konto des Nutzers). Diese Sicherung ist ein Dienst des Geräteherstellers bzw.
von Google, nicht der App; der Verantwortliche hat darauf keinen Zugriff. Wer das nicht möchte,
deaktiviert die App-Datensicherung in den Geräteeinstellungen. Für Google-Backups gilt die
Datenschutzerklärung von Google (https://policies.google.com/privacy).

## 4. Sprachausgabe

Die App spricht alle Aufgaben, Buchstaben, Silben, Wörter und Sätze vor. Dafür nutzt sie zwei
Wege, beide ohne Netzwerk:

1. **Mitgelieferte Audio-Clips**: Der größte Teil der Sprachausgabe besteht aus Audiodateien, die
   in der App enthalten sind und direkt auf dem Gerät abgespielt werden.
2. **Sprachausgabe von Android (System-TTS)**: Für Texte ohne passenden Clip (z. B. gesprochene
   Zahlen) übergibt die App den Text an die auf dem Gerät installierte Text-zu-Sprache-Engine
   des Betriebssystems. Dabei wird nur der vorzulesende Text übergeben, keine Angaben zur
   Person. Welche TTS-Engine installiert ist und wie diese arbeitet, bestimmt der Nutzer über
   die Android-Einstellungen; für die Engine gilt die Datenschutzerklärung ihres jeweiligen
   Anbieters. Die App selbst verarbeitet die Sprache vollständig auf dem Gerät.

Die App nimmt keine Sprache auf: Sie hat keine Mikrofon-Berechtigung und keine Spracherkennung.

## 5. Berechtigungen

Die App fordert genau eine Berechtigung an:

- `android.permission.VIBRATE`: kurze Vibrationen als Rückmeldung beim Tippen und bei Erfolgen
  (Haptik). Diese Berechtigung gibt keinen Zugriff auf Daten.

Die App fordert **nicht** an: Internet, Netzwerkstatus, Mikrofon, Kamera, Standort, Kontakte,
Speicherzugriff außerhalb des eigenen App-Bereichs, Telefonstatus, Benachrichtigungen.

## 6. Google Play als Vertriebsplattform

Die App wird über den Google Play Store der Google Ireland Limited (Gordon House, Barrow Street,
Dublin 4, Irland) verteilt. Beim Suchen, Herunterladen, Installieren und Aktualisieren der App
über Google Play verarbeitet Google Daten (z. B. Google-Konto, Gerätekennungen, Installations-
und Aktualisierungsvorgänge, gegebenenfalls Bewertungen). Diese Verarbeitung erfolgt in
Verantwortung von Google und nach dessen Datenschutzerklärung:
https://policies.google.com/privacy

Der Verantwortliche erhält über die Play Console von Google nur aggregierte, nicht auf Personen
rückführbare Statistiken (z. B. Anzahl der Installationen nach Land) sowie von Nutzern freiwillig
verfasste Bewertungen. Nutzer, die eine Bewertung im Store abgeben, tun dies gegenüber Google;
der angezeigte Name richtet sich nach den Einstellungen des Google-Kontos des Nutzers.

## 7. Kinder

Die App richtet sich an Kinder im Alter von etwa 4 bis 7 Jahren. Gerade deshalb verzichtet sie
vollständig auf die Erhebung von Daten: Es werden weder von Kindern noch von Eltern
personenbezogene Daten erhoben, gespeichert oder übertragen. Es gibt keine Werbung, keine
externen Links im Kinderbereich, keine Kaufmöglichkeiten und keine Kommunikationsfunktionen.
Damit sind keine Einwilligungen nach Art. 8 DSGVO erforderlich.

Der Elternbereich (erreichbar durch langes Drücken des Drei-Punkte-Menüs) enthält nur die zwei in
Abschnitt 3 genannten Einstellungen.

## 8. Rechte der Betroffenen

Nach der DSGVO stehen Betroffenen grundsätzlich folgende Rechte zu: Auskunft (Art. 15),
Berichtigung (Art. 16), Löschung (Art. 17), Einschränkung der Verarbeitung (Art. 18),
Datenübertragbarkeit (Art. 20), Widerspruch (Art. 21) sowie das Recht auf Beschwerde bei einer
Datenschutz-Aufsichtsbehörde (Art. 77).

Da der Verantwortliche keine personenbezogenen Daten von Nutzern der App besitzt, kann er zu
einzelnen Personen weder Auskunft erteilen noch Daten berichtigen, löschen oder herausgeben.
Die einzigen von der App verarbeiteten Daten liegen ausschließlich auf dem Gerät des Nutzers und
stehen dort unter dessen alleiniger Kontrolle (siehe Abschnitt 3). Fragen zu dieser
Datenschutzerklärung beantwortet der Verantwortliche unter der in Abschnitt 1 genannten
E-Mail-Adresse.

Für Daten, die Google im Zusammenhang mit Google Play verarbeitet, sind Anfragen an Google zu
richten (siehe Abschnitt 6).

## 9. Änderungen dieser Datenschutzerklärung

Diese Erklärung wird angepasst, wenn sich die App oder die rechtlichen Anforderungen ändern,
insbesondere falls eine künftige Version der App Daten erheben oder übertragen sollte. Die jeweils
gültige Fassung ist unter der im Store-Eintrag angegebenen Adresse abrufbar; das Datum oben zeigt
den Stand.

---

## Privacy Policy (English summary)

Silbo – ABC Vorschule (package `app.abcvorschule`) is a free, ad-free German literacy and
arithmetic app for children aged about 4 to 7, developed by a private individual (see section 1
for name and contact).
The app does not collect, store or transmit any personal data. It does not request the Internet
permission and therefore cannot connect to any server; it contains no analytics, crash reporting
or advertising SDKs, no account and no in-app purchases.
Learning progress and two parent settings are stored only in the app's private storage on the
device and are deleted when the app is uninstalled. Android device backup may include this data
under the user's own device settings.
Speech output uses bundled audio clips and, as a fallback, the device's own Android text-to-speech
engine; nothing is recorded and no microphone permission exists. The only permission is VIBRATE.
Downloading the app through Google Play is subject to Google's privacy policy
(https://policies.google.com/privacy). For questions, contact [E-Mail-Adresse]. Last updated:
2026-09-11.
