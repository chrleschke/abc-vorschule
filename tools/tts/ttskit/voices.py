"""Die Stimmen und Sprachen, die das Qwen3-TTS-Modell kennt.

Beides steht in der Modell-Config (`talker_config.spk_id` bzw.
`talker_config.codec_language_id`) — aber nur als nackte Namen. Was dort
*nicht* steht und was die Oberfläche braucht, ist die Herkunft einer Stimme.

Der Unterschied ist hörbar: `language` setzt ein Sprach-Token und steuert damit
die Phonologie, das Speaker-Embedding bringt trotzdem den Akzent seiner
Kernsprache mit. Bei einem ganzen Satz gleicht der Kontext das weitgehend aus,
bei einem einzelnen Laut („M") gibt es keinen Kontext — dort schlägt der Akzent
voll durch. Deshalb steht die Herkunft im UI hinter jedem Stimmnamen, und
deshalb warnt es, wenn eine nicht-europäische Stimme deutschen Text spricht.

**Es gibt keine europäische Frauenstimme.** Von den neun Stimmen sind fünf
chinesisch, eine japanisch, eine koreanisch — europäisch sind nur `ryan` und
`aiden`, beide männlich und beide englisch. So steht es in der Modellkarte des
Checkpoints (`serena`: „Warm, gentle young female voice", Chinese; `vivian`:
„Bright, slightly edgy young female voice", Chinese).

Diese Spalte stand bis August 2026 falsch da: `serena` und `vivian` waren als
„westlich, weiblich" geführt und damit als `european=True`. Der Abgleich in
`tests/test_voices.py` konnte das nicht auffangen — die Modell-Config kennt
unter `spk_id` nur die nackten Namen, nicht die Herkunft. Wer für Deutsch eine
Frauenstimme will, wählt damit zwangsläufig einen Akzent; der Ausweg führt über
einen anderen Checkpoint (VoiceDesign oder Voice-Clone), nicht über diese
Tabelle.
"""

from __future__ import annotations

from dataclasses import dataclass

#: Sprachen aus `codec_language_id`, ohne die chinesischen Dialekte — die
#: wählt das Modell selbst über `spk_is_dialect`, sie sind keine Alternative
#: zu `german`.
LANGUAGES: tuple[str, ...] = (
    "german", "english", "french", "italian", "spanish", "portuguese",
    "russian", "chinese", "japanese", "korean",
)


@dataclass(frozen=True)
class Voice:
    name: str
    #: Herkunft, wie sie im UI in Klammern hinter dem Namen erscheint.
    origin: str
    #: True, wenn die Kernsprache der Stimme europäisch ist. Nur dann ist die
    #: Stimme für deutschen Text unauffällig.
    european: bool


#: Reihenfolge ist die Anzeigereihenfolge: erst die für Deutsch brauchbaren.
#: Das sind genau zwei, und beide sind männlich — siehe Modul-Docstring.
VOICES: tuple[Voice, ...] = (
    Voice("ryan", "englisch, männlich", True),
    Voice("aiden", "englisch, männlich (US)", True),
    Voice("serena", "chinesisch, weiblich", False),
    Voice("vivian", "chinesisch, weiblich", False),
    Voice("sohee", "koreanisch, weiblich", False),
    Voice("ono_anna", "japanisch, weiblich", False),
    Voice("uncle_fu", "chinesisch, männlich", False),
    Voice("eric", "chinesisch, Sichuan-Dialekt", False),
    Voice("dylan", "chinesisch, Peking-Dialekt", False),
)

_BY_NAME = {v.name: v for v in VOICES}


def speaker_names() -> list[str]:
    return [v.name for v in VOICES]


def voice(name: str) -> Voice | None:
    """Die Stimme zu `name`, oder None wenn unbekannt.

    Unbekannt heißt hier „nicht in dieser Tabelle", nicht „vom Modell
    abgelehnt". Ein künftiges Checkpoint kann mehr Stimmen mitbringen; dann
    gehört sie hier ergänzt, statt die Herkunft zu raten.
    """
    return _BY_NAME.get(name)


def origin_of(name: str) -> str:
    found = voice(name)
    return found.origin if found else "unbekannte Herkunft"


def accent_risk(speaker: str, language: str) -> bool:
    """Spricht hier eine nicht-europäische Stimme europäischen Text?"""
    found = voice(speaker)
    if found is None or found.european:
        return False
    return language.lower() not in ("chinese", "japanese", "korean")
