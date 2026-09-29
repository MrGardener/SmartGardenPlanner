package com.example.smartgardenplanner.core

/** Undo / redo stack of canvas snapshots, keeping at most [limit] steps (the oldest are dropped first). */
class BoundedHistoryStack<T>(private var limit: Int) {
    private val deque = ArrayDeque<T>()

    fun push(element: T) {
        // Discard the oldest steps; a limit of 0 or less keeps nothing (a single "if" kept one entry over the limit).
        while (deque.isNotEmpty() && deque.size >= limit) deque.removeFirst()
        if (limit > 0) deque.addLast(element)
    }

    /** Lets the configured depth (Settings) apply without recreating the stack / losing history. */
    fun updateLimit(newLimit: Int) {
        limit = newLimit
        while (deque.size > maxOf(limit, 0)) deque.removeFirst()
    }

    fun pop(): T? = deque.removeLastOrNull()

    fun clear() = deque.clear()

    fun size(): Int = deque.size

    fun toList(): List<T> = deque.toList()
}
