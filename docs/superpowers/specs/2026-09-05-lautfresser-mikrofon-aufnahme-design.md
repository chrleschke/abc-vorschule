# Laut-Fresser: echte Laute per Mikrofon statt Fake-Aussprache

**Datum:** 2026-09-05 · **Status:** approved (Nutzerentscheidungen aus der Session)
**Ersetzt** in `2026-09-04-laut-fresser-design.md` den Teil von §7, der die Laute über
`soundTts` („sss", „schhh") im Profil `monster` synthetisiert.

## 1. Problem

Der Laut-Fresser soll den *Laut* eines Graphems sprechen („sss"), nicht den
Buchstabennamen („Es"). Der bisherige Weg — eine Fake-Aussprache als `soundTts` am
Buchstaben-Atom und daraus ein Qwen-Clip im Profil `monster` — scheitert doppelt:

- Qwen macht aus „sss"/„schhh" keinen brauchbaren Laut (siehe README „Aussprache ist
  der Engpass"); die fünf bestehenden `monster`-Locks tragen Notlösungen wie
  „die Biene macht sss wie sum..".
- Auch die Android-TTS als Fallback liest „schhh" schlechter vor als „Sch".

Die Laute müssen **von Hand aufgenommen** werden. Dafür braucht das Web-Interface
eine Mikrofon-Aufnahme mit minimalem Editor. Gleichzeitig muss der Fresser genau den
Text sprechen, der auf dem Bauch steht — das Lemma, keine Fake-Schreibweise.

## 2. Entscheidungen (Nutzer, 2026-09-05)

| Frage | Entscheidung |
| --- | --- |
| Laufzeit-Tonhöhe der App (links 0.75, rechts 1.3) | **bleibt** für normale Clips und TTS. Der Editor bekommt Abhörknöpfe „Laufzeit links/rechts", die den App-Pitch auf die Bearbeitung legen, plus einen eigenen Zusatz-Pitch (Halbtöne). **Nachtrag 2026-09-05 abends:** für Aufnahmen aus `variants.monster` nur noch ±1 Halbstufe (`VoiceStyle.variantPitch`), weil ×0.75 ein S spektral auf das Sch schob; dazu ein optionaler Hochpass 120 Hz im Editor (`Edit.highpass`). |
| Umfang der Monster-Clips | **nur Grapheme aus `SoundPairs`** (heute 26), nicht alle 40 Buchstaben-Atome. |
| Wo wird gepitcht | **serverseitig** mit librosa (Tempo bleibt), gleiche Kette wie Qwen. |
| „Bäh!" / „Mmmmh!" | bleiben unverändert im Profil `monster` mit ihren Locks und Audios. |
| Alte `soundTts`-Locks (tt, kw, rrr, äää, sss) | werden verworfen. |

## 3. Datenmodell

### 3.1 Content-Pack und App

- `soundTts` verschwindet aus `atoms.json` (40 Einträge), aus `Atom` und aus
  `AtomSoundTtsTest`. Der Fresser spricht das **Lemma** des Buchstaben-Atoms
  (`SpeechClipText.forAtom`), also „S", „Sch", „S t" — in `VoiceStyle.MonsterLow/High`.
- `audio/index.json` bekommt neben `clips` einen Block **`variants`**:

  ```json
  {
    "version": 1,
    "clips":    { "S": { "file": "phoneme_…​.ogg", "profile": "phoneme", "fingerprint": "…" } },
    "variants": { "monster": { "S": { "file": "monster_…​.ogg", "profile": "monster", "fingerprint": "…" } } }
  }
  ```

  Alle Clips des Profils `monster` landen unter `variants.monster`. Zusätzlich stehen
  sie in `clips`, **wenn** dort kein anderes Profil denselben Text trägt („Bäh!",
  „Mmmmh!" — sonst wären sie für eine Ansage in Normalstimme unauffindbar). Ein
  Buchstabe „S" steht also einmal in `clips` (phoneme) und einmal in
  `variants.monster`; die Kollisionsregeln in `export._collision_winner` bleiben für
  `clips` unverändert.
- `ClipIndex.lookup(text, variant: String? = null)`: erst `variants[variant][text]`,
  dann wie bisher `clips[text]` (inkl. eindeutiger Groß-/Kleinschreibung).
- `SpeechController.playClip`: bei `voice != VoiceStyle.Normal` zuerst
  `lookup(text, "monster")`, sonst/danach `lookup(text)`. Der Clip wird weiterhin mit
  `voice.pitch` abgespielt — die Laufzeit-Verschiebung ist Absicht (siehe §2).
  Fallback bleibt Android-TTS mit dem Lemma und `setPitch`.

### 3.2 Pipeline (`tools/tts`)

- **Items:** Für jedes Buchstaben-Atom, dessen `display` in den SoundPairs-Graphemen
  liegt, erzeugt `extract_items` ein Item `atom:<id>:monsterSound` mit
  `text = lemma`, `field = "monsterSoundTts"` → Profil `monster`, Label
  `"<display> (Monster-Laut)"`. Das Feld ersetzt `soundTts` in `FIELD_TO_PROFILE`.
- **SoundPairs-Grapheme** kommen aus dem Kotlin-Quelltext
  (`app/src/main/java/app/abcvorschule/content/SoundPairs.kt`): `extract.sound_pair_graphemes(path)`
  liest per Regex alle `SoundPair("X", "Y", …)`-Aufrufe. Bewusst kein zweites
  Pflegeort im Content — die Paar-Tabelle ist kuratiertes Wissen und bleibt im Code.
  Ein Test prüft, dass die Datei ≥ 20 Paare liefert und jedes Graphem ein
  Buchstaben-Atom im ausgelieferten Pack hat; bricht das Format, fällt der Test, nicht
  still der Umfang. `extract_items` nimmt die Menge als Parameter (`monster_graphemes`),
  damit Tests sie explizit setzen; `cli.load_context` liest sie aus der Datei.
- **Clip-Keys** bleiben `clip_key("monster", lemma)`, d. h. `monster:<sha(lemma)>` —
  unabhängig vom phoneme-Clip desselben Textes.
- **Kollisionstest** `test_no_text_is_rendered_under_two_profiles_by_accident`: Paare
  `{phoneme, monster}` sind gewollte Varianten und werden herausgefiltert; die
  Ausnahme „Ei" bleibt.
- **Profil** bekommt zwei Felder (Default, wenn abwesend):
  `"source": "tts" | "mic"` (Default `tts`) und `"micPitchSemitones": int` (Default 0,
  Bereich −12…+12). `monster` steht auf `mic` / `-4`. Beide gehen **nicht** in
  `plan.fingerprint` — sie ändern keine Qwen-Bytes; Mikrofon-Clips haben einen eigenen
  Fingerprint (§3.3). Editierbar in der Profilkarte und in „⚙️ TTS-Parameter".
- **Locks:** Die fünf verwaisten `soundTts`-Locks werden aus `locks.json` entfernt
  (`monster:0e07cf830957`, `103c54b6c5b1`, `12b0f0dcaefb`, `1cf0cca6ed0c`,
  `a871c47a7f48`). `monster:64f570f6b0e3` (Bäh!) und `monster:c7497ed84146` (Mmmmh!)
  bleiben. Kandidaten-Ordner unter `out/` (gitignored) werden nicht angefasst.

### 3.3 Mikrofon-Aufnahmen als Kandidaten

Eine Aufnahme ist ein **Kandidat** wie jede Probeaufnahme, damit Radio-Button
„Produktion", 👍/👎, „Alle löschen", Export und Lock-Semantik unverändert greifen.

- **Identität:** Kandidaten heißen nach ihrem Seed; eine Aufnahme bekommt einen
  Pseudo-Seed aus dem reservierten Bereich `MIC_SEED_MIN = 1_900_000_000 … 2^31−1`,
  zufällig, ohne Kollision im Ordner. Ein Lock auf eine Aufnahme ist damit ein
  gewöhnlicher Lock mit `seed`. Qwen-Seeds aus diesem Bereich sind praktisch
  ausgeschlossen (`random_seeds` schließt ihn künftig aus).
- **Dateien** unter `out/candidates/<key>/`:
  - `<seed>.raw.wav` — die Rohaufnahme, 24 kHz mono PCM16, **ungeschnitten**, bleibt
    dauerhaft, damit Schnitt und Pitch nachträglich änderbar sind.
  - `<seed>.wav` — die bearbeitete Fassung (das, was Kandidat und Produktion ist).
  - `<seed>.json` — Sidecar:

    ```json
    { "source": "mic", "createdAt": "…", "speaker": "mic", "text": "S",
      "edit": { "start": 0.12, "end": 0.87, "pitchSemitones": -4, "normalize": true },
      "autoTrim": { "start": 0.12, "end": 0.87 },
      "fingerprint": "mic:3f9de54b075bc96f" }
    ```

  - `fingerprint` = `"mic:" + sha256(bearbeitetes PCM16)[:16]`. Damit encodiert der
    Export nach einer neuen Bearbeitung neu und bleibt sonst unverändert.
- **Löschen** eines Mikrofon-Kandidaten (👎, „Alle löschen") entfernt auch `.raw.wav`.
- **Produktion folgt der Bearbeitung:** Ist die bearbeitete Aufnahme gerade Produktion
  und wird erneut geschnitten/gepitcht, wird `out/audio/<key>.wav` mit aktualisiert.
  Der Nutzer bearbeitet seine gewählte Aufnahme, nicht eine Kopie.

## 4. Verarbeitungskette (`ttskit/mic.py`)

Reine Funktionen über numpy-Arrays, ohne Modell, testbar mit synthetischen Signalen:

1. **`load_upload(data: bytes) -> (wav, 24000)`** — liest WAV (beliebige Rate, 1–2 Kanäle,
   float/int), mischt auf mono, resampelt mit `scipy.signal.resample_poly` auf 24 kHz.
   Unlesbare Daten → `ValueError` mit Grund (HTTP 422).
2. **`auto_trim(wav, sr) -> (start_s, end_s)`** — RMS in 10-ms-Fenstern; Rauschboden =
   Median der leisesten 20 % Fenster; Schwelle = `max(0.01, 4 × Rauschboden)`; erstes
   und letztes Fenster über der Schwelle, ±40 ms Polster, an die Aufnahme geklemmt.
   Kein Fenster über der Schwelle → ganze Aufnahme. Anders als `audio.trim_silence`
   relativ zum Rauschboden, weil ein Mikrofon nie digital still ist.
3. **`peaks(wav, buckets=600) -> list[float]`** — Maximalamplitude je Bucket für die
   Wellenform-Anzeige.
4. **`render(raw, sr, edit, extra_pitch_semitones=0.0) -> wav`** — Ausschnitt
   `[start, end)`; Pitch-Shift um `pitchSemitones + extra` mit
   `librosa.effects.pitch_shift` (Tempo bleibt; 0 → kein Aufruf); 5 ms Ein-/Ausblende
   gegen Klicks; `normalize` → `audio.normalize_peak` (−1 dBFS). Ausgabe float32.
5. **`fingerprint_of(wav) -> "mic:…"`** — Hash des PCM16-Inhalts.
6. **`APP_MONSTER_PITCH = {"left": 0.75, "right": 1.3}`** — Spiegel von
   `VoiceStyle.MonsterLow/High`; Halbtöne = `12·log2(faktor)`. Kommentar in beiden
   Dateien verweist aufeinander; `/api/state` liefert die Werte an die UI.

## 5. Server-API (kein Modell nötig, läuft im Request-Thread)

| Route | Zweck |
| --- | --- |
| `POST /api/clips/{key}/recordings` (Body: WAV-Bytes) | Rohaufnahme annehmen, `.raw.wav` schreiben, Auto-Trim rechnen, Sidecar mit Default-Edit (Auto-Trim, Profil-Pitch, normalize=true) anlegen **und** `<seed>.wav` sofort rendern. Antwort: `{seed, analysis}`. |
| `GET /api/clips/{key}/recordings/{seed}` | `{peaks, duration, sampleRate, edit, autoTrim}` für den Editor. |
| `POST /api/clips/{key}/recordings/{seed}/preview` (Body: `edit`, optional `appPitch`) | Bearbeitung ohne Speichern rendern, WAV-Bytes zurück. `appPitch` (Faktor, z. B. 0.75) legt den Laufzeit-Pitch der App obendrauf. |
| `PUT /api/clips/{key}/recordings/{seed}` (Body: `edit`) | Bearbeitung speichern: `<seed>.wav` und Sidecar neu, Produktion mitziehen (§3.3). |
| `PUT /api/profiles/{name}` | akzeptiert zusätzlich `source`, `micPitchSemitones` (validiert). |
| `GET /api/state` | Kandidaten tragen `mic: true` und für Mikrofon-Aufnahmen `fresh: true` (nie „⚠️ alt"); Profile tragen die neuen Felder; `appMonsterPitch`. |

Validierung: `edit.start < edit.end` innerhalb der Rohdauer, `pitchSemitones` −12…+12,
sonst 422 mit Bereich in der Meldung. Upload-Größe gedeckelt (30 s bei 48 kHz ≈ 6 MB
float32 → `MAX_UPLOAD_BYTES = 16 MB`).

`api_promote` und `export` benutzen für Kandidaten mit `source == "mic"` den Sidecar-
Fingerprint statt `plan.fingerprint` (`plan.clip_fingerprint(clip, profile, paths)` als
eine Stelle dafür; `verified` ist bei Aufnahmen immer wahr).

## 6. Web-UI

### 6.1 Quelle wählen

In der Karte „Aufnahmen erzeugen & bestätigen" steht über der Generate-Zeile ein
Umschalter **Quelle: 🎲 TTS | 🎙 Mikrofon**. Vorbelegt aus `profile.source`; eine
Umschaltung gilt für diesen Clip und wird im Browser gemerkt (`localStorage`,
`ttsSource:<key>`). Im TTS-Modus bleibt alles wie heute. Im Mikrofon-Modus ersetzt
das Aufnahme-Panel die Generate-Zeile (Textfeld, Profil, Stimme bleiben — die Stimme ist
für Aufnahmen bedeutungslos und wird ausgegraut).

### 6.2 Aufnehmen

- `getUserMedia({audio: {channelCount: 1, echoCancellation: false, noiseSuppression: false, autoGainControl: false}})`;
  Aufnahme über ein **AudioWorklet** (`/recorder-worklet.js`, eigene statische Datei),
  das Float32-PCM sammelt. Kein `MediaRecorder`: der liefert Opus/WebM, das der Server
  nur mit ffmpeg lesen könnte.
- Knopf **● Aufnehmen** → **■ Stopp**; daneben ein Pegelbalken. Bei Stopp wird
  client-seitig ein WAV (float32, Geräterate) gebaut und hochgeladen; die Antwort
  öffnet den Editor für die neue Aufnahme. Maximal 30 s, dann automatischer Stopp.
- Verweigert der Browser das Mikrofon, erscheint die Meldung im Banner; der Umschalter
  bleibt auf Mikrofon.

### 6.3 Editor

Ein Panel unter dem Aufnahme-Knopf, offen für genau eine Aufnahme (`seed`):

- **Wellenform** (`<canvas>`, 600 Buckets) der ganzen Rohaufnahme. Bereiche außerhalb
  `[start, end]` grau hinterlegt, der Auto-Trim-Vorschlag als dünne Linien markiert,
  damit sichtbar bleibt, wo die Automatik geschnitten hätte.
- **Zwei Griffe** (Start/Ende) per Pointer ziehbar; dazu Zahlenfelder in Sekunden
  (3 Nachkommastellen) und ein Knopf **„Automatisch"**, der auf den Auto-Trim zurückgeht.
- **Tonhöhe:** Auswahl in Halbtönen −12…+12 (Default aus dem Profil).
- **Normalisieren:** Häkchen (Default an).
- **Abhören:** „▶ Anhören" (nur die Bearbeitung); nur für Profil `monster` zusätzlich
  „▶ Laufzeit links" (×0.75) und „▶ Laufzeit rechts" (×1.3) — so klingt es in der App.
  Jeder Knopf holt eine Vorschau vom Server (`/preview`) und spielt sie ab.
- **Übernehmen** speichert (`PUT`), schließt den Editor und lädt die Kandidaten-Tabelle
  nach. **Verwerfen** schließt ohne Speichern; die Aufnahme bleibt als Kandidat mit
  dem zuletzt gespeicherten Edit.

### 6.4 Kandidaten-Tabelle

Mikrofon-Aufnahmen erscheinen wie alle Kandidaten: Radio „Produktion", Player, 👍/👎,
Zeitpunkt. Statt der Stimme steht **🎙**, statt „⚠️ alt" nichts; zusätzlich ein Knopf
**✂** in der Zeile, der den Editor für diese Aufnahme öffnet. In der Clip-Liste links
zeigt ein 🎙 hinter dem Status, dass die Produktion eine Aufnahme ist.

## 7. Fehlerfälle

- Upload nicht lesbar / leer / länger als 30 s → 422 mit Grund, Banner in der UI.
- Edit außerhalb der Dauer oder `start ≥ end` → 422; das UI klemmt die Griffe vorher.
- Rohdatei fehlt (Kandidat von Hand gelöscht) → 404, Zeile zeigt „Rohaufnahme fehlt",
  ✂ ist deaktiviert; Abspielen und Produktion funktionieren weiter.
- Kein Mikrofon-Zugriff → Banner, keine Aufnahme.
- `librosa` fehlt → 500 mit klarer Meldung beim ersten Pitch ≠ 0; Aufnahmen ohne Pitch
  funktionieren trotzdem.

## 8. Tests

**Python (`tools/tts/tests`):**
- `test_mic.py`: `load_upload` (Stereo 48 kHz → mono 24 kHz, Länge stimmt), `auto_trim`
  (Signal mit Rauschboden, Stille vorn/hinten, Polster, ganze Aufnahme bei Stille),
  `peaks` (Länge, Maxima), `render` (Ausschnitt, Normalisierung, Pitch −12 verändert
  Grundfrequenz eines Sinus messbar, Dauer bleibt ±1 %), `fingerprint_of` (stabil,
  ändert sich mit dem Edit).
- `test_server.py`: Upload → Kandidat mit Sidecar `source: mic` und gerenderter
  `.wav`; GET liefert Peaks/Edit; PUT rendert neu und zieht die Produktion mit; Preview
  liefert WAV; Löschen entfernt `.raw.wav`; Promote einer Aufnahme setzt Lock und
  `verified: true`; `/api/state` trägt `mic`, `appMonsterPitch`, Profilfelder.
- `test_extract.py`: Monster-Items nur für SoundPairs-Grapheme, Text = Lemma, Feld
  `monsterSoundTts`; Regex-Parser gegen die echte `SoundPairs.kt` (≥ 20 Paare, alle mit
  Buchstaben-Atom); Kollisionstest lässt `{phoneme, monster}` durch.
- `test_export.py`: `monster`-Clips landen in `variants.monster`; „Bäh!" auch in
  `clips`; ein Buchstabe steht in `clips` als phoneme und in `variants.monster`;
  Mikrofon-Fingerprint steuert das Re-Encoding.
- `test_store.py`: `source`/`micPitchSemitones` Defaults, Validierung, Roundtrip.
- `test_render.py`: `random_seeds` liefert nie einen Seed ≥ `MIC_SEED_MIN`.

**Kotlin (`app/src/test`):**
- `ClipIndexTest`: `lookup(text, "monster")` trifft die Variante, fällt ohne Variante
  auf `clips` zurück; unbekannte Variante ist kein Fehler.
- `SoundFeederSpeech`-Tests: `soundPart` liefert das Lemma mit Monster-Stimme.
- `AtomSoundTtsTest` entfällt; ersetzt durch einen Test, dass jedes SoundPairs-Graphem
  ein Buchstaben-Atom hat (falls nicht schon in `SoundPairsTest`).
- Bestehender Index-Konsistenz-Test (`ClipIndex.entries()`) deckt auch `variants` ab.

## 9. Doku-Folgeänderungen

- `tools/tts/README.md`: Abschnitt „Mikrofon-Aufnahmen" (Quelle, Editor, Dateien,
  Pseudo-Seeds, `variants` im Index); Absatz „Profil `monster`" umschreiben.
- `docs/PRODUCT_PRINCIPLES.md` (Laut-Fresser-Absatz um Z. 433) und `AGENTS.md`
  (Laut-Fresser-Kernpunkt): `soundTts` → Lemma mit Variante `monster`, Aufnahme per
  Mikrofon.
- `2026-09-04-laut-fresser-design.md` §7: Verweis auf dieses Dokument.
- `2026-08-02-qwen-tts-app-integration-design.md`: Index-Format um `variants` ergänzen.

## 10. Bewusst nicht drin

- Kein Rauschfilter, kein Kompressor, kein Equalizer — Schnitt, Pitch, Normalisierung
  reichen für Ein-Laut-Clips; alles weitere macht man vor dem Mikrofon.
- Keine Mikrofon-Aufnahme für den Batch-Lauf; Aufnahmen sind immer Einzelarbeit.
- Keine Migration alter Kandidaten; `out/` ist derivat.
- Kein Umbau der App-Tonhöhe (Entscheidung §2).
