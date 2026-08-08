# Artikel beim Vorlesen der Lösungswörter

**Status:** approved · **Datum:** 2026-08-08

## Ziel

Die Kompetenz für Artikel und Genus stärken. Wenn ein Trainer nach richtiger Lösung das
Lösungswort vorspricht, kommt der Artikel mit: „Baue das Wort Haus" → „**das** Haus".

Die Aufgabenstellung selbst bleibt artikellos — der Artikel ist Teil der *Antwort*, nicht
der Frage.

## Entscheidungen

| Frage | Entscheidung |
|---|---|
| Umfang | **Nur das Erfolgs-Vorsprechen** (`SuccessSpeech`). Antippen, Prompts und `missTts` bleiben unverändert. |
| Personenbezeichnungen | **Unbestimmter Artikel** (`eine Oma`, `ein Opa`) — Ausnahme Neutrum, siehe unten. |
| Datenmodell | **`gender` + `nounClass` mit optionalem Override** je Atom. |

## 1. Datenmodell

Drei neue optionale Felder an `Atom` (`ContentModels.kt`, `atoms.json`):

```kotlin
@Serializable
enum class Gender { m, f, n }

@Serializable
enum class NounClass {
    /** Gegenstand, Tier, Pflanze, Abstraktum — bestimmter Artikel. */
    thing,
    /** Personenbezeichnung (Oma, Opa, Clown, Pirat) — unbestimmter Artikel. */
    person,
    /** Eigenname (Tom, Mimi) — kein Artikel. */
    name,
}

data class Atom(
    // …
    val gender: Gender? = null,
    val nounClass: NounClass? = null,
    /** Fertiger Sprechtext, wenn die Ableitung nicht passt (Plural, Sonderfälle). */
    val articleSpeechOverride: String? = null,
)
```

### Ableitungsregel — `AtomArticleSpeech`

Neue Datei `content/AtomArticleSpeech.kt`, eine Funktion:

```
forAtom(atom): String?
```

| Bedingung (in dieser Reihenfolge) | Ergebnis | Beispiel |
|---|---|---|
| `articleSpeechOverride` gesetzt | der Override, wörtlich | `die Häuser` |
| `nounClass == null` | `null` — kein Substantiv, Aufrufer fällt auf sein bisheriges Verhalten zurück | `ist`, `rot`, `ma`, `M` |
| `nounClass == name` | `display` | `Tom` |
| `nounClass == person`, `gender == m` | `ein $display` | `ein Opa` |
| `nounClass == person`, `gender == f` | `eine $display` | `eine Oma` |
| `nounClass == person`, `gender == n` | `das $display` | `das Kind` |
| `nounClass == thing` | bestimmter Artikel nach `gender` (m→der, f→die, n→das) | `das Haus`, `die Maus`, `der Baum` |
| `nounClass` gesetzt, `gender == null` | `null` (der Validator verhindert diesen Zustand im Pack) | — |

**Warum Neutrum-Personen den bestimmten Artikel bekommen:** `ein Opa` und `ein Kind` klingen
identisch, obwohl das eine maskulin und das andere neutrum ist — der unbestimmte Artikel
kann m und n nicht auseinanderhalten. Beim Neutrum trägt `das` das Genus eindeutig, ohne
dass die Personenbezeichnung dadurch nach einer bestimmten Person klingt. `ein`/`eine`
bleibt für m und f, weil `der Opa`/`die Oma` im Alltag eine konkrete Person meinen, nicht
die Kategorie.

Im aktuellen Pack gibt es kein Neutrum-Personen-Atom; die Regel greift für künftige.

### Abdeckung im Pack

Alle Substantiv-Atome bekommen `gender` und `nounClass` — beide `kind`-Sorten
(`word` ≈ 100 Nomen, `other` = 53 reiner Bildwortschatz), zusammen ~150. Auch die, die heute
nirgends vorgelesen werden: **die Daten sind vollständig, das Audio ist es bewusst nicht.**

Funktionswörter (`ist`, `da`, `mein`, `den`, `und`, `einen`, …), Verben (`ruft`, `spielt`,
`gehen`, …), Adjektive/Farben (`rot`, `blau`, `groß`, …), Buchstaben und Silben bekommen
**keine** der drei Felder — `nounClass` bleibt `null`.

### Override — genau vier Atome

Plural-Atome nehmen im Deutschen „die", unabhängig vom Genus des Singulars. `gender` bleibt
trotzdem das echte Genus des Singulars, damit die Genus-Information nicht in einem String
verschwindet:

| Atom | `gender` | `nounClass` | `articleSpeechOverride` |
|---|---|---|---|
| `haeusser` (Häuser) | `n` | `thing` | `die Häuser` |
| `baeume` (Bäume) | `m` | `thing` | `die Bäume` |
| `aepfel` (Äpfel) | `m` | `thing` | `die Äpfel` |
| `eier` (Eier) | `n` | `thing` | `die Eier` |

## 2. Verwendung — ausschließlich `SuccessSpeech`

In [`SuccessSpeech.partsForRound`](../../../app/src/main/java/app/abcvorschule/session/SuccessSpeech.kt)
bekommen genau drei Zweige den Artikel:

| Zweig | Trainer | heute | neu |
|---|---|---|---|
| `WordBuildRound` | Wort-Bauer | `atom.display` | `AtomArticleSpeech.forAtom(atom) ?: atom.display` |
| `SymbolInWordRound` | Wort-Detektiv | `atom.display` | dito |
| `SoundPositionRound` | Auditiver Finder (pausiert) | `atom.lemma` | `AtomArticleSpeech.forAtom(atom) ?: atom.lemma` |

Die bestehende Fallback-Kette auf `promptTts` bleibt unangetastet. Ein Atom ohne
`nounClass` verhält sich exakt wie heute — die Änderung ist für Nicht-Substantive ein No-Op.

**Bewusst unverändert:**

- `CountAddRound` — „zwei Ameisen": vor einer Zahl steht kein Artikel.
- `SyllableMergeRound` — alle 30 Ergebnis-Atome sind Silben (plus `am`), nie ein Nomen.
- `SentenceOrderRound` / `SentencePictureRound` — ganze Sätze, die ihre Artikel schon tragen.
- `SymbolHuntRound` — Ziel ist ein Graphem, kein Wort.
- `LetterTraceRound` — autorierter `rewardTts`.
- Antippen von Items (`SoundPositionTrainer`, `SymbolHuntTrainer`, `SymbolInWordTrainer`,
  `RewardSummaryScreen`), alle `promptTts` und alle `missTts`.

## 3. Validierung

Neue Regeln in `ContentValidator.validate`, pro Atom:

1. `nounClass` in `{thing, person}` → `gender` muss gesetzt sein.
2. `gender` gesetzt → `nounClass` muss gesetzt sein (kein halbes Paar).
3. `nounClass == name` → `gender` muss `null` sein (Namen tragen kein Artikel-Genus).
4. **Plural-Atome brauchen einen Override.** Ein Atom `p` braucht `articleSpeechOverride`,
   wenn es ein *anderes* Atom `s` gibt mit `s.pluralDisplay == p.display` und
   `s.display != p.display`. Die zweite Bedingung schließt Selbst-Plurale aus
   (`Eimer`, `Löffel`, `Tiger`, `Igel`, `Kuchen`, `Käse`, `Spiegel`, `Zucker`, `Feuer`) und
   lässt genau die vier echten Plural-Atome übrig.
5. Jedes Atom, das `SuccessSpeech` erreichen kann (siehe Reichweiten-Definition unten) und
   ein Substantiv ist, hat `nounClass` gesetzt — sonst fiele es still auf das artikellose
   Verhalten zurück.

Regel 5 kann „ist das ein Substantiv?" nicht aus den Daten ableiten. Sie prüft deshalb die
gegenläufige Richtung: die Liste der bewusst artikellosen erreichbaren Atome steht als
benannte Konstante im Validator (`ArticleFreeSpeechAtomIds` = `am`, `ich`, `rot`, `hallo`),
alles andere Erreichbare muss klassifiziert sein. Ein neu autoriertes Wort ohne
Klassifikation bricht damit den Test, statt unbemerkt ohne Artikel zu laufen.

## 4. Audio

### Reichweite — welche Atome einen Clip bekommen

`SuccessSpeech` erreicht ein Atom über genau zwei autorierte Felder:

```
reachable = { round.targetAtomId | task.trainer == "word_build" }
          ∪ { round.atomId       | task.trainer == "sound_position" }
```

Der Wort-Detektiv wird zur Laufzeit aus den `word_build`-Wörtern abgeleitet
(`SymbolInWordDerivation`) und ist damit enthalten.

Das sind 91 Atome; abzüglich der vier artikellosen (`am`, `ich`, `rot`, `Hallo`) und der
zwei Namen (`Tom`, `Mimi`) bleiben **85 neue Clips**.

Die restlichen ~65 klassifizierten Substantive erzeugen **keinen** Clip — sonst stünden sie
dauerhaft als „fehlt" in `tts status` und würden echte Lücken verdecken.

### Neues Profil `article_word`

Nicht das bestehende `word`-Profil, aus zwei Gründen:

- `word` ist auf `max_new_tokens: 25` (≈ 2,0 s Rohaudio) gedeckelt. „eine Erdbeere" läuft
  dagegen und würde hart abgeschnitten.
- Die Instruktion muss ausdrücklich verlangen, Artikel und Nomen **als eine Einheit** zu
  sprechen. Ein Absetzen nach dem Artikel klingt wie zwei aneinandergehängte Clips — genau
  das Flickwerk, das vermieden werden soll.

Der kuratierte Seed-Pool von `word` bleibt dadurch unberührt.

```json
"article_word": {
  "label": "Artikel + Wort",
  "speaker": "sohee",
  "language": "german",
  "instruct": "Sprich Artikel und Nomen als eine einzige Einheit, ohne Pause nach dem Artikel. Klar und freundlich, ruhiges Tempo, keine Frage-Melodie, keine Übertreibung. Kein Englisch, kein Koreanisch. Sprich mit tiefer Stimme.",
  "sampling": { "…wie word…", "max_new_tokens": 35 },
  "seedPool": []
}
```

Startwerte für Sampling wie `word`; `max_new_tokens: 35` (≈ 2,8 s Rohaudio). Der Seed-Pool
startet leer und füllt sich über die 👍-Kuratierung.

### Pipeline-Anbindung

- `FIELD_TO_PROFILE["articleTts"] = "article_word"` in `ttskit/extract.py`.
- `extract_items` gibt pro erreichbarem Atom mit Artikel-Sprechtext ein Item aus:
  `id = "atom:{atomId}:articleTts"`, `text` = abgeleiteter Sprechtext,
  `label = "{display} (Artikel)"`.
- Die Ableitung wird in `extract.py` gespiegelt — dasselbe Muster wie `_spoken_answer`,
  das `CountAddRound.spokenAnswer` spiegelt. Ein Test hält beide Seiten zusammen.
- Ein Item wird nur ausgegeben, wenn der Sprechtext sich vom `display` unterscheidet.
  Namen (`Tom`) erzeugen damit keinen zweiten, textgleichen Clip zum bestehenden
  `word`-Clip.

Die bestehenden artikellosen `word`-Clips bleiben **alle** erhalten — sie tragen weiterhin
Antippen, Prompts und die Wort-Detektiv-Sequenzen.

### Reihenfolge und Zwischenzustand

Der Content- und Code-Teil ist ohne Audio auslieferbar: fehlt ein Clip, spricht Android-TTS
„das Haus" (`ClipIndex.lookup` liefert `null`, `SpeechController` fällt zurück). Die
Kuratierung der 85 Clips im TTS-Web-Interface ist ein eigener, nachgelagerter Schritt.

## 5. Tests

| Test | Prüft |
|---|---|
| `AtomArticleSpeechTest` (neu) | Ableitungstabelle vollständig: Override, `name`, `person` m/f/n, `thing` m/f/n, `nounClass == null`, `gender == null` |
| `SuccessSpeechTest` | Die drei Zweige liefern die Artikel-Form; `count_add`, `syllable_merge`, `sentence_order`, `symbol_hunt` liefern nachweislich **keine** |
| `ContentValidatorTest` | Die fünf neuen Regeln, jede mit einem mutierten Pack (`pack.copy(...)`, keine Fixture-Kopie) |
| `ContentValidatorTest` gegen das ausgelieferte Pack | Alle ~150 Substantive klassifiziert, alle vier Plural-Overrides gesetzt |
| `tools/tts/tests/test_extract.py` | Artikel-Items nur für erreichbare Atome; Ableitung stimmt mit der Kotlin-Regel überein; Namen erzeugen kein Item |

## 6. Doku

- `docs/PRODUCT_PRINCIPLES.md` §4 (Content-Graph): Genus-/Nomenklassen-Felder am Atom.
- `docs/PRODUCT_PRINCIPLES.md` §7 (Sprache & Audio): Regel „Erfolgs-Vorsprechen nennt bei
  Substantiven den Artikel; Prompts, Antippen und `missTts` nicht."
- `tools/tts/README.md`: Profil `article_word` in der Umfangstabelle und der Profilliste.

## Nicht in diesem Schritt

- Artikel beim Antippen von Items oder Bildern.
- Artikel in `missTts`-Feedback-Texten.
- Ein eigener Genus-Trainer („Ist das der, die oder das?"). Die Daten tragen ihn, gebaut
  wird er hier nicht.
