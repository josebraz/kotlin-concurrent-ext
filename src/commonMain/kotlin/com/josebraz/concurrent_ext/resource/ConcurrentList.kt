package com.josebraz.concurrent_ext.resource

class ConcurrentList<T>(
    initialList: MutableList<T>? = null
): ManyReadersOneWriter<MutableList<T>, List<T>>(
    initialList?.toMutableList() ?: mutableListOf()
), MutableList<T> {
    override val size: Int
        get() = reader { it.size }

    override fun clear() = writer { it.clear() }

    override fun addAll(elements: Collection<T>): Boolean {
        return writer { it.addAll(elements) }
    }

    override fun addAll(index: Int, elements: Collection<T>): Boolean {
        return writer { it.addAll(index, elements) }
    }

    override fun add(index: Int, element: T) {
        return writer { it.add(index, element) }
    }

    override fun add(element: T): Boolean {
        return writer { it.add(element) }
    }

    override fun get(index: Int): T = reader { it[index] }

    override fun isEmpty(): Boolean = reader { it.isEmpty() }

    override fun iterator(): MutableIterator<T> = IteratorImpl()

    override fun listIterator(): MutableListIterator<T> = IteratorImpl()

    override fun listIterator(index: Int): MutableListIterator<T> {
        return IteratorImpl(index)
    }

    override fun removeAt(index: Int): T = writer { it.removeAt(index) }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> {
        return writer { it.subList(fromIndex, toIndex) }
    }

    override fun set(index: Int, element: T): T {
        return writer { it.set(index, element) }
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

    override fun lastIndexOf(element: T): Int {
        return reader { it.lastIndexOf(element) }
    }

    override fun indexOf(element: T): Int {
        return reader { it.indexOf(element) }
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        return reader { it.containsAll(elements) }
    }

    override fun contains(element: T): Boolean {
        return reader { it.contains(element) }
    }

    private inner class IteratorImpl(
        private var index: Int = 0
    ): AbstractIterator<T>(), MutableListIterator<T> {
        override fun computeNext() {
            reader {
                if (index < it.size) {
                    setNext(it[index++])
                } else {
                    done()
                }
            }
        }

        override fun add(element: T) = add(index++, element)

        override fun hasPrevious(): Boolean = index > 0

        override fun nextIndex(): Int = index + 1

        override fun previous(): T = get(--index)

        override fun previousIndex(): Int = index - 1

        override fun remove() {
            removeAt(index)
        }

        override fun set(element: T) {
            set(index, element)
        }
    }

    override fun toString(): String {
        return reader { "[" + it.joinToString(", ") + "]" }
    }
}