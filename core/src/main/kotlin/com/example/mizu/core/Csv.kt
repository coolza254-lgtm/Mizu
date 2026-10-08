package com.example.mizu.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object CsvExporter {
    /** UTF-8 byte order mark so Excel opens Thai/Japanese text correctly. */
    const val BOM = "﻿"
    const val HEADER = "date,time,amount_ml,source,bottle_name,weight_before_g,weight_after_g"
    private const val EOL = "\r\n"
    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** Logs on [from]..[to] (inclusive), oldest first, as BOM + header + rows. */
    fun build(logs: List<DrinkLog>, bottleNames: Map<Long, String>, from: LocalDate, to: LocalDate): String {
        val sb = StringBuilder(BOM).append(HEADER).append(EOL)
        logs.filter { val d = it.timestamp.toLocalDate(); !d.isBefore(from) && !d.isAfter(to) }
            .sortedBy { it.timestamp }
            .forEach { log ->
                sb.append(
                    listOf(
                        log.timestamp.format(DATE),
                        log.timestamp.format(TIME),
                        log.amountMl.toString(),
                        log.source.name,
                        escape(log.bottleId?.let { bottleNames[it] } ?: ""),
                        log.weightBeforeG?.toString() ?: "",
                        log.weightAfterG?.toString() ?: "",
                    ).joinToString(","),
                ).append(EOL)
            }
        return sb.toString()
    }

    fun escape(field: String): String {
        // Neutralise spreadsheet formula injection from user-typed names.
        val safe = if (field.isNotEmpty() && field[0] in "=+-@\t\r") "'$field" else field
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + safe.replace("\"", "\"\"") + "\""
        } else {
            safe
        }
    }
}
