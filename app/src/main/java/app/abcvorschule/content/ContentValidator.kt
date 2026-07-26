package app.abcvorschule.content

data class ValidationIssue(val message: String)

class ContentValidationException(val issues: List<ValidationIssue>) :
    IllegalArgumentException(issues.joinToString("\n") { it.message })

object ContentValidator {
    private const val MinTasksPerDomain = 3

    fun validate(pack: ContentPack): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        if (pack.manifest.schemaVersion < 1) {
            issues += ValidationIssue("schemaVersion must be >= 1")
        }
        val atomIds = pack.atoms.keys
        if (atomIds.size != pack.atoms.size) {
            issues += ValidationIssue("duplicate atom ids")
        }
        pack.atoms.values.forEach { atom ->
            atom.prerequisites.forEach { pre ->
                if (pre !in atomIds) {
                    issues += ValidationIssue("atom ${atom.id} has missing prerequisite $pre")
                }
            }
        }
        pack.sentences.values.forEach { sentence ->
            sentence.atomIds.forEach { id ->
                if (id !in atomIds) {
                    issues += ValidationIssue("sentence ${sentence.id} references missing atom $id")
                }
            }
        }
        val taskIds = mutableSetOf<String>()
        pack.tasks.forEach { task ->
            if (!taskIds.add(task.id)) {
                issues += ValidationIssue("duplicate task id ${task.id}")
            }
            task.atomId?.let {
                if (it !in atomIds) issues += ValidationIssue("task ${task.id} missing atom $it")
            }
            task.targetAtomId?.let {
                if (it !in atomIds) issues += ValidationIssue("task ${task.id} missing target $it")
            }
            task.sentenceId?.let { sid ->
                if (sid !in pack.sentences) {
                    issues += ValidationIssue("task ${task.id} missing sentence $sid")
                }
            }
            (task.slots + task.gapAtomIds + task.composeParts).forEach { id ->
                if (id !in atomIds) {
                    issues += ValidationIssue("task ${task.id} slot/gap missing atom $id")
                }
            }
            if (task.composeDisplays.isNotEmpty() &&
                task.composeDisplays.size != task.composeParts.size
            ) {
                issues += ValidationIssue(
                    "task ${task.id} composeDisplays size must match composeParts",
                )
            }
            if (task.domain == Domain.math && task.answer == null) {
                issues += ValidationIssue("math task ${task.id} needs answer")
            }
        }
        Domain.entries.forEach { domain ->
            val count = pack.tasks.count { it.domain == domain }
            if (count < MinTasksPerDomain) {
                issues += ValidationIssue(
                    "domain $domain has $count tasks; need at least $MinTasksPerDomain",
                )
            }
        }
        return issues
    }

    fun requireValid(pack: ContentPack): ContentPack {
        val issues = validate(pack)
        if (issues.isNotEmpty()) throw ContentValidationException(issues)
        return pack
    }
}
