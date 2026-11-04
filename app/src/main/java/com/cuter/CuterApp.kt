package com.cuter

import android.app.Application
import com.cuter.data.MediaImporter
import com.cuter.data.ProjectRepository

class CuterApp : Application() {
    val repository: ProjectRepository by lazy { ProjectRepository(this) }
    val importer: MediaImporter by lazy { MediaImporter(this, repository) }
}
