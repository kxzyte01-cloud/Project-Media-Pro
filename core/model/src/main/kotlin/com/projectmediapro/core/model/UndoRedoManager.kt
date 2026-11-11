package com.projectmediapro.core.model

class UndoRedoManager<T>(
    initialState: T,
    private val maxHistorySize: Int = 50
) {
    init {
        require(maxHistorySize > 0) { "maxHistorySize must be positive" }
    }

    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()
    var currentState: T = initialState
        private set

    fun pushState(newState: T) {
        if (newState == currentState) return
        undoStack.addLast(currentState)
        if (undoStack.size > maxHistorySize) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        currentState = newState
    }

    fun undo(): T? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(currentState)
        currentState = undoStack.removeLast()
        return currentState
    }

    fun redo(): T? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(currentState)
        currentState = redoStack.removeLast()
        return currentState
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
}