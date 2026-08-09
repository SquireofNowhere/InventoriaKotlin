package com.inventoria.app.ui.screens.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventoria.app.data.TodoRepository
import com.inventoria.app.data.model.TaskKind
import com.inventoria.app.data.model.Todo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val todoRepository: TodoRepository
) : ViewModel() {

    val todos: StateFlow<List<Todo>> = todoRepository.getVisibleTodos()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isAddingNew = MutableStateFlow(false)
    val isAddingNew: StateFlow<Boolean> = _isAddingNew.asStateFlow()

    private val _pendingEditTodo = MutableStateFlow<Todo?>(null)
    val pendingEditTodo: StateFlow<Todo?> = _pendingEditTodo.asStateFlow()

    init {
        startPeriodicCleanup()
    }

    private fun startPeriodicCleanup() {
        viewModelScope.launch {
            while (isActive) {
                todoRepository.purgeOldDeletedTodos(System.currentTimeMillis() - 86_400_000)
                delay(60_000)
            }
        }
    }

    fun startAddingTodo() {
        _isAddingNew.value = true
    }

    fun startEditingTodo(todo: Todo) {
        _pendingEditTodo.value = todo
    }

    fun dismissDialog() {
        _isAddingNew.value = false
        _pendingEditTodo.value = null
    }

    fun addTodo(title: String, kind: TaskKind) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            todoRepository.insertTodo(Todo(id = UUID.randomUUID().toString(), title = trimmed, kind = kind))
        }
        _isAddingNew.value = false
    }

    fun saveEditedTodo(todo: Todo, title: String, kind: TaskKind) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            todoRepository.updateTodo(todo.copy(title = trimmed, kind = kind))
        }
        _pendingEditTodo.value = null
    }

    fun setCompleted(todo: Todo, completed: Boolean) {
        viewModelScope.launch { todoRepository.setCompleted(todo.id, completed) }
    }

    fun deleteTodo(todo: Todo) {
        viewModelScope.launch { todoRepository.softDeleteTodo(todo.id) }
    }
}
