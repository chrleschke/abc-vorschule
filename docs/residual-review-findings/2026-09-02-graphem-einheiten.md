# Graphem-Einheiten im Wort-Bauer — bewusst offene Reste

**Datum:** 2026-09-02 · **Status:** offen
**Betrifft:** Audio-Paket, l12, l16, l24

Umgesetzt ist der Design-Entscheid
[`2026-09-02-graphem-einheiten-im-wort-bauer-design.md`](../superpowers/specs/2026-09-02-graphem-einheiten-im-wort-bauer-design.md):
`Äu` ist das 40. Graphem, neun Wort-Bauer-Runden schneiden keine Graphem-Einheit
mehr durch, `ContentValidator` erzwingt es, und l16 hat keinen Verschmelzer mehr.
Was liegen bleibt:

## 1. Drei fehlende Sprachclips

- `Äu` (Profil `phoneme`, Lemma des neuen Atoms) — gesprochen im Spurensucher,
  in der Buchstaben-Jagd, im Wort-Detektiv („Finde den Laut - Äu - im Wort -
  Häuser.") und auf der Kachel des Wort-Bauers.
- `Zeichne den Laut - Äu - nach und sammle dabei alle Sterne.` (Profil `prompt`)
- `Äu - wie in Häuser.` (Profil `reward`)

Braucht einen Lauf von `tools/tts/` (lokales Qwen3-TTS, eigene venv, siehe
`tools/tts/README.md`) und ein Durchhören. Bis dahin sprechen alle drei über
Android-TTS. Falls Qwen die Schreibung „Äu" nicht trägt, ist die
Aussprache-Umschrift im `lemma` der etablierte Ausweg — der Pack nutzt sie
zehnmal (`se`→„seh", `ro`→„roh", `vo`→„fo", `do`→„dough", `ste`→„steh").

Der Clip `fa` ist verwaist: er war das Lemma des gelöschten Atoms `pfa`.

## 2. l12 lehrt `ä` und `ö`, zeigt sie aber in keinem Wort

Küken trägt das `ü`, Häuser und Bäume tragen das `äu`. Ein einzelnes `ä` oder ein
`ö` kommt in keinem Wort der Lektion vor — bisher tauchte „ä" nur auf, weil der
Diphthong falsch zerschnitten war (`Hä + u`), also war der Mangel verdeckt. Der
Wort-Detektiv jagt in l12 jetzt zweimal `Äu` und einmal `Ü`, nie `Ä` oder `Ö`.

Zu schließen wäre das mit Wörtern, die l12 aus bereits gebauten Wörtern
umlauten kann: **Dach → Dächer** (sauberes `ä`) und **Hut → Hüte** (sauberes
`ü`), beide seit l05/l10 gebaut. Für `ö` gibt es vor l25 (Löwe) nichts. Das ist
ein Content-Durchgang mit neuen Atomen, Bildern und Aufnahmen — deshalb nicht im
selben Zug.

## 3. l24 nennt einen Laut eine Silbe

`l24-t5` verschmilzt `s + t = st` und `l24-t6` `s + p = sp`, beide mit dem Prompt
„Welche Silbe entsteht." Dabei entsteht keine Silbe, sondern ein Graphem — genau
die Unterscheidung, die die Prinzipien für Prompts sonst durchziehen („den Laut"
statt „den Buchstaben"). Ein Nachbar des `pfa`-Befunds, aber anders als dieser
inhaltlich nicht falsch, nur falsch benannt: `st` steht in Nest, Stern, Stuhl.
Fix wäre der Prompt („Welcher Laut entsteht.") plus zwei neue Clips.

## 4. `cke` bleibt ein Schnipsel

„cke" (Socke l16, Jacke l27) hängt am Graphem-Atom `ck`. Ein eigenes Silben-Atom
würde beide Kacheln richtig ankern und dem Wort-Detektiv in l27 „Finde die Silbe
- cke - im Wort - Jacke." erlauben — aber als *lesbare* Silbe taugt `cke` nicht:
keine deutsche Silbe beginnt mit `ck`. Der Anker wäre richtig, die Silbe nicht.
