package com.josebraz.concurrent_ext.resource

class ConcurrentMap<K, V> : ManyReadersOneWriter<MutableMap<K, V>, Map<K, V>>,
    MutableMap<K, V> {

    constructor(vararg pairs: Pair<K, V>) : super(mutableMapOf(*pairs))

    constructor(map: MutableMap<K, V> = mutableMapOf()): super(map)

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = writer { ConcurrentSet(it.entries) }
    override val keys: MutableSet<K>
        get() = writer { ConcurrentSet(it.keys) }
    override val size: Int
        get() = reader { it.size }
    override val values: MutableCollection<V>
        get() = writer { ConcurrentCollection(it.values) }

    override fun clear() = writer { it.clear() }

    override fun isEmpty(): Boolean = reader { it.isEmpty() }

    override fun remove(key: K): V? = writer { it.remove(key) }

    override fun putAll(from: Map<out K, V>) {
        writer { it.putAll(from) }
    }

    override fun put(key: K, value: V): V? = writer { it.put(key, value) }

    override fun get(key: K): V? = reader { it[key] }

    override fun containsValue(value: V): Boolean = reader { it.containsValue(value) }

    override fun containsKey(key: K): Boolean = reader { it.containsKey(key) }
}