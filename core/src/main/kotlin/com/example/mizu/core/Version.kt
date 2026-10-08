package com.example.mizu.core

object VersionComparator {
    /** "v1.2.3-beta" -> [1, 2, 3]. Non-numeric parts count as 0. */
    fun parse(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+')
            .split('.')
            .map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    fun isNewer(remote: String, local: String): Boolean {
        val r = parse(remote)
        val l = parse(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
