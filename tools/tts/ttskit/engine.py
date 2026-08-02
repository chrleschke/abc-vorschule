"""Thin wrapper around qwen_tts that makes generation seed-reproducible."""

from __future__ import annotations

import numpy as np

from .store import Profile, Profiles

CHECKPOINT = "Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice"


def pick_device() -> str:
    import torch

    if torch.backends.mps.is_available():
        return "mps"
    if torch.cuda.is_available():
        return "cuda"
    return "cpu"


class Engine:
    def __init__(self, checkpoint: str = CHECKPOINT, device: str | None = None) -> None:
        self.checkpoint = checkpoint
        self.device = device
        self.loaded = False
        self.load_error: str | None = None
        self._model = None

    def load(self) -> None:
        """Load the model. Failures are captured, not raised.

        The server must be able to start without a model so already rendered
        clips stay curatable.
        """
        try:
            import torch
            from qwen_tts import Qwen3TTSModel

            device = self.device or pick_device()
            self._model = Qwen3TTSModel.from_pretrained(
                self.checkpoint,
                device_map=device,
                dtype=torch.bfloat16,
                attn_implementation="sdpa",
            )
            self.device = device
            self.loaded = True
            self.load_error = None
        except Exception as exc:  # noqa: BLE001 - surfaced in the UI
            self.loaded = False
            self.load_error = f"{type(exc).__name__}: {exc}"

    def validate(self, profiles: Profiles) -> list[str]:
        if not self.loaded:
            return ["Modell nicht geladen — Speaker und Sprache ungeprüft."]
        speakers = {s.lower() for s in (self._model.get_supported_speakers() or [])}
        languages = {l.lower() for l in (self._model.get_supported_languages() or [])}
        errors: list[str] = []
        for name, profile in sorted(profiles.profiles.items()):
            if speakers and profile.speaker.lower() not in speakers:
                errors.append(
                    f"Profil {name!r}: Speaker {profile.speaker!r} wird nicht unterstützt. "
                    f"Gültig: {', '.join(sorted(speakers))}")
            if languages and profile.language.lower() not in languages:
                errors.append(
                    f"Profil {name!r}: Sprache {profile.language!r} wird nicht unterstützt. "
                    f"Gültig: {', '.join(sorted(languages))}")
        return errors

    def generate(self, text: str, profile: Profile, seed: int) -> tuple[np.ndarray, int]:
        """Synthesize one clip.

        Reproducibility rests on all three of these together: the seed set
        immediately before the call, exactly one text per call (batch size 1),
        and unchanged sampling parameters.
        """
        if not self.loaded or self._model is None:
            raise RuntimeError("Engine not loaded — call load() first.")

        import torch

        torch.manual_seed(seed)
        wavs, sample_rate = self._model.generate_custom_voice(
            text=text,
            speaker=profile.speaker,
            language=profile.language,
            instruct=profile.instruct or None,
            **profile.sampling,
        )
        return np.asarray(wavs[0], dtype=np.float32), int(sample_rate)
