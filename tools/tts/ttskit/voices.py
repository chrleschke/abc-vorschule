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
VOICES: tuple[Voice, ...] = (
    Voice("serena", "westlich, weiblich", True),
    Voice("vivian", "westlich, weiblich", True),
    Voice("ryan", "westlich, männlich", True),
    Voice("aiden", "westlich, männlich", True),
    Voice("sohee", "koreanisch", False),
    Voice("ono_anna", "japanisch", False),
    Voice("uncle_fu", "chinesisch", False),
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
