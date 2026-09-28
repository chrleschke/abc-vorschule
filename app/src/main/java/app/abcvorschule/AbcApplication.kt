package app.abcvorschule

import android.app.Application
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.progress.ProgressRepository
import app.abcvorschule.ui.rewards.AbcSfx

class AbcApplication : Application() {
    lateinit var contentRepository: ContentRepository
        private set
    lateinit var progressRepository: ProgressRepository
        private set

    override fun onCreate() {
        super.onCreate()
        contentRepository = ContentRepository.fromContext(this)
        progressRepository = ProgressRepository.fromContext(this)
        AbcSfx.init(this)
    }
}
