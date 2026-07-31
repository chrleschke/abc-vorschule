package app.abcvorschule.session

import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.Lesson
import app.abcvorschule.content.TaskSpec

/**
 * Baut die Trainer-Liste einer Lektion: die autorierten Tasks in Reihenfolge, dann
 * die beiden zur Laufzeit abgeleiteten Einschübe (Jagd, Wort-Detektiv).
 *
 * Der Scheduler läuft **zum Schluss über die fertige Liste**, nicht vorher über die
 * autorierten Specs. Das ist der Unterschied, der zählt: ein synthetischer Trainer
 * entsteht erst in den Insertions und würde sonst ohne `scaffolds` bleiben. Der
 * Wort-Detektiv liest `scaffoldFor(atomId)`, und ein leeres Scaffold-Map fällt in
 * [SessionViewModel.scaffoldFor] auf `Beginner` zurück — also hätte ein Kind, dessen
 * Eltern "Ohne Hilfe" gewählt haben, nach einem Neustart mitten in der Lektion die
 * abgeschaltete Silhouette gesehen (bis [SessionViewModel.advance] neu schedult).
 *
 * Compose-frei und ohne ViewModel-Zustand, damit ein Test die Invariante "jeder
 * Trainer trägt Scaffolds für jedes gescorte Atom" über die *echte* Zusammenstellung
 * prüfen kann — auch für den nächsten synthetischen Trainer.
 *
 * @param schedule wie ein Spec zu einem [ScheduledTrainer] wird. Reine Funktion von
 * Spec und Lernstand, also idempotent: ein bereits geschedulter Spec ergibt dasselbe
 * Ergebnis noch einmal.
 */
object SessionTrainers {
    fun assemble(
        pack: ContentPack,
        lesson: Lesson,
        schedule: (TaskSpec) -> ScheduledTrainer,
    ): List<ScheduledTrainer> {
        val authored = pack.tasksOf(lesson).map { ScheduledTrainer(spec = it) }
        val withHunts = SymbolHuntInsertion.insertSymbolHunts(authored, pack, lesson.id, lesson.index)
        val withDetective = SymbolInWordInsertion.insertSymbolInWord(withHunts, pack, lesson)
        return withDetective.map { schedule(it.spec) }
    }
}
