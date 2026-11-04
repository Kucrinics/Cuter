package com.cuter.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cuter.CuterApp
import com.cuter.data.CanvasRatio
import com.cuter.data.ProjectSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as CuterApp).repository

    private val _projects = MutableStateFlow<List<ProjectSummary>>(emptyList())
    val projects: StateFlow<List<ProjectSummary>> = _projects

    val message = MutableStateFlow<String?>(null)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _projects.value = withContext(Dispatchers.IO) { repository.list() }
        }
    }

    fun create(name: String, ratio: CanvasRatio, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val project = withContext(Dispatchers.IO) { repository.create(name, ratio) }
            refresh()
            onReady(project.id)
        }
    }

    fun delete(projectId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.delete(projectId) }
            refresh()
        }
    }

    fun duplicate(projectId: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repository.load(projectId)?.let { repository.duplicate(it) }
            }
            refresh()
        }
    }

    fun rename(projectId: String, name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.rename(projectId, name) }
            refresh()
        }
    }

    fun dismissMessage() {
        message.value = null
    }
}
