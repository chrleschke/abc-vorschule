# Kurze Wort-/Satz-Prompts Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Shorten Wort-Bauer and Mehrwort-Satz-Architekt `promptTts` and guard the forms in `ContentValidator`.

**Architecture:** Content is source of truth (`tasks.json`). Validator rejects old instruction suffixes. Speak path stays on `promptTts`. No audio/lock changes.

**Tech Stack:** Kotlin, ContentPack JSON, JUnit unit tests, Gradle.

## Global Constraints

- Wort-Bauer prompt: `Baue das Wort {Wort}.`
- Satz Mehrwort: sentence only; no `Ordne die Wörter`
- Einwort-Bild prompts unchanged
- Do not touch `audio/`, `locks.json`, `profiles.json`

---

### Task 1: Shorten `tasks.json` prompts

**Files:**
- Modify: `app/src/main/assets/content/tasks.json`

- [x] Strip Wort-Bauer suffix after first sentence → `Baue das Wort X.`
- [x] Strip ` - Ordne die Wörter in die richtige Reihenfolge.` from Mehrwort sentences
- [x] Leave four `Ordne das Wort … dem Bild zu.` rounds unchanged
- [x] Verify counts: 64 word_build short, 21 sentence short, 4 bild unchanged

### Task 2: Validator + tests

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/content/ContentValidator.kt`
- Modify: `app/src/test/java/app/abcvorschule/content/ContentValidatorTest.kt` (or create)
- Modify: `app/src/test/java/app/abcvorschule/speech/ClipIndexTest.kt` if it asserts long prompts

- [x] Add word_build / sentence_order prompt rules per design
- [x] Pack-mutation tests for reject/allow cases
- [x] Fix ClipIndexTest fixture strings

### Task 3: Product principles

**Files:**
- Modify: `docs/PRODUCT_PRINCIPLES.md`

- [x] `Bilde` → `Baue`; note Satz prompt = sentence text; Einwort-Bild exception

### Task 4: Verify

- [x] `./gradlew :app:testDebugUnitTest`
- [x] Spot-check pack pattern counts
