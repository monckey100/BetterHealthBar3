package kr.toxicity.healthbar.util

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import kotlin.collections.ArrayList

fun <T> List<T>.split(splitSize: Int): List<List<T>> {
    val result = ArrayList<List<T>>()
    var index = 0
    while (index < size) {
        val subList = subList(index, (index + splitSize).coerceAtMost(size))
        if (subList.isNotEmpty()) result.add(subList)
        index += splitSize
    }
    return result
}


fun <T> List<T>.forEachAsync(block: (T) -> Unit) {
    if (isNotEmpty()) {
        val available = Runtime.getRuntime().availableProcessors()
        val tasks = if (available >= size) {
            map {
                {
                    block(it)
                }
            }
        } else {
            val queue = ArrayList<() -> Unit>()
            var i = 0
            val add = (size.toDouble() / available).toInt()
            while (i <= size) {
                val get = subList(i, (i + add).coerceAtMost(size))
                queue.add {
                    get.forEach { t ->
                        block(t)
                    }
                }
                i += add
            }
            queue
        }
        Executors.newFixedThreadPool(tasks.size).use { pool ->
            CompletableFuture.allOf(
                *tasks.map {
                    CompletableFuture.runAsync({
                        it()
                    }, pool)
                }.toTypedArray()
            ).join()
        }
    }
}