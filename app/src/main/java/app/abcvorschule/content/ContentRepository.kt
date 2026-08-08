package app.abcvorschule.content

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.InputStream

class ContentRepository(
    private val openAsset: (String) -> InputStream,
) {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        // Trainer name is the discriminator so tasks.json reads like the curriculum.
        classDiscriminator = "trainer"
    }

    @Volatile
    private var cached: ContentPack? = null

    fun load(): ContentPack {
        cached?.let { return it }
        val pack = ContentValidator.requireValid(parsePack())
        cached = pack
        return pack
    }

    fun clearCache() {
        cached = null
    }

    private fun parsePack(): ContentPack {
        val manifest = json.decodeFromString<PackManifest>(read("content/pack.manifest.json"))
        // `associateBy` is last-wins: a duplicated id would silently shadow its
        // first definition and ContentValidator could never see it — so duplicates
        // must fail right here, on the same exception path a validation issue takes
        // (SessionViewModel.bootstrap catches it via runCatching).
        val issues = mutableListOf<ValidationIssue>()
        val atoms = associateByUniqueId(
            "atoms",
            json.decodeFromString<AtomsFile>(read("content/atoms.json")).atoms,
            issues,
        ) { it.id }
        val sentences = associateByUniqueId(
            "sentences",
            json.decodeFromString<SentencesFile>(read("content/sentences.json")).sentences,
            issues,
        ) { it.id }
        val tasks = associateByUniqueId(
            "tasks",
            json.decodeFromString<TasksFile>(read("content/tasks.json")).tasks,
            issues,
        ) { it.id }
        val finales = associateByUniqueId(
            "finales",
            json.decodeFromString<FinalesFile>(read("content/finales.json")).finales,
            issues,
        ) { it.id }
        // Lessons stay a list; ContentValidator reports duplicate lesson ids itself.
        val lessons = json.decodeFromString<LessonsFile>(read("content/lessons.json")).lessons
            .sortedBy { it.index }
        if (issues.isNotEmpty()) throw ContentValidationException(issues)
        return ContentPack(manifest, atoms, sentences, tasks, finales, lessons)
    }

    private fun <T> associateByUniqueId(
        what: String,
        items: List<T>,
        issues: MutableList<ValidationIssue>,
        id: (T) -> String,
    ): Map<String, T> {
        items.groupingBy(id).eachCount().filterValues { it > 1 }.keys.forEach { duplicate ->
            issues += ValidationIssue("$what holds duplicate id $duplicate")
        }
        return items.associateBy(id)
    }

    private fun read(path: String): String =
        openAsset(path).bufferedReader().use { it.readText() }

    companion object {
        fun fromContext(context: Context): ContentRepository =
            ContentRepository { path -> context.assets.open(path) }

        fun fromClasspath(
            classLoader: ClassLoader = Thread.currentThread().contextClassLoader!!,
        ): ContentRepository = ContentRepository { path ->
            classLoader.getResourceAsStream(path)
                ?: error("Missing classpath resource: $path")
        }
    }
}
