package com.knightlsy.douyin.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.knightlsy.douyin.data.ContentType
import com.knightlsy.douyin.data.DownloadHistoryItem
import com.knightlsy.douyin.data.HistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = HistoryRepository(application)

    private val _items = MutableStateFlow<List<DownloadHistoryItem>>(emptyList())
    val items: StateFlow<List<DownloadHistoryItem>> = _items.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode: StateFlow<Boolean> = _isSelectMode.asStateFlow()

    fun loadHistory() {
        viewModelScope.launch {
            _items.value = withContext(Dispatchers.IO) { repository.getAll() }
        }
    }

    fun toggleSelectMode() {
        _isSelectMode.value = !_isSelectMode.value
        if (!_isSelectMode.value) _selectedIds.value = emptySet()
    }

    fun toggleSelect(id: String) {
        _selectedIds.value = if (id in _selectedIds.value) _selectedIds.value - id
        else _selectedIds.value + id
    }

    fun toggleSelectAll() {
        val allIds = _items.value.map { it.id }.toSet()
        _selectedIds.value = if (_selectedIds.value == allIds) emptySet() else allIds
    }

    fun deleteSelected() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.delete(_selectedIds.value) }
            _selectedIds.value = emptySet()
            loadHistory()
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.clear() }
            loadHistory()
        }
    }

    fun createHistoryItem(
        contentId: String, title: String, author: String, coverUrl: String,
        contentType: ContentType, originalUrl: String, downloadedFiles: List<String> = emptyList()
    ): DownloadHistoryItem {
        return DownloadHistoryItem(
            id = UUID.randomUUID().toString(),
            contentId = contentId,
            title = title,
            author = author,
            coverUrl = coverUrl,
            contentType = contentType,
            originalUrl = originalUrl,
            downloadTime = System.currentTimeMillis(),
            downloadedFiles = downloadedFiles
        )
    }

    fun saveHistory(item: DownloadHistoryItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.add(item) }
        }
    }
}
