package com.josebraz.concurrent_ext.resource

class ConcurrentSet<T> : ManyReadersOneWriter<MutableSet<T>, Set<T>>,
    MutableSet<T> {

    constructor(vararg element: T) : super(mutableSetOf(*element))

    constructor(initialList: MutableSet<T> = mutableSetOf()) : super(initialList)

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

    override fun iterator(): MutableIterator<T> = writer { it.iterator() }

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
        return reader { "{" + it.joinToString(", ") + "}" }
    }
}