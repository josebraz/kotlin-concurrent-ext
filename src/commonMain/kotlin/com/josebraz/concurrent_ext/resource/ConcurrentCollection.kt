package com.josebraz.concurrent_ext.resource

class ConcurrentCollection<T>: ManyReadersOneWriter<MutableCollection<T>, Collection<T>>,
    MutableCollection<T> {

    constructor(collection: MutableCollection<T>) : super(collection)

    override val size: Int
        get() = reader { it.size }

    override fun clear() = writer { it.clear() }

    override fun addAll(elements: Collection<T>): Boolean {
        return writer { it.addAll(elements) }
    }

    override fun add(element: T): Boolean {
        return writer { it.add(element) }
    }

    override fun isEmpty(): Boolean = reader { it.isEmpty() }

    override fun iterator(): MutableIterator<T> {
        TODO("Not yet implemented")
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        return writer { it.retainAll(elements) }
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        return writer { it.removeAll(elements) }
    }

    override fun remove(element: T): Boolean {
        return writer { it.remove(element) }
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        return reader { it.containsAll(elements) }
    }

    override fun contains(element: T): Boolean {
        return reader { it.contains(element) }
    }

    override fun toString(): String {
        return reader { "[" + it.joinToString(", ") + "]" }
    }
}