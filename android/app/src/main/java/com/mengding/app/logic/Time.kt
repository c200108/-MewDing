package com.mengding.app.logic

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 时间一律按手机当地时区（需求 R5）；手机拿不到时区时退回北京时间。 */
object Time {
    val BEIJING: ZoneId = ZoneId.of("Asia/Shanghai")

    fun zone(): ZoneId = runCatching { ZoneId.systemDefault() }.getOrDefault(BEIJING)

    fun now(): Long = System.currentTimeMillis()

    fun today(): LocalDate = LocalDate.now(zone())

    fun todayStr(): String = today().toString()

    fun parse(hhmm: String): LocalTime = runCatching { LocalTime.parse(hhmm) }.getOrDefault(LocalTime.NOON)

    fun fmt(t: LocalTime): String = t.format(DateTimeFormatter.ofPattern("HH:mm"))

    fun at(day: LocalDate, hhmm: String): Long = day.atTime(parse(hhmm)).atZone(zone()).toInstant().toEpochMilli()

    fun at(day: LocalDate, t: LocalTime): Long = day.atTime(t).atZone(zone()).toInstant().toEpochMilli()

    fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDate()

    fun dateTimeOf(ms: Long): LocalDateTime = Instant.ofEpochMilli(ms).atZone(zone()).toLocalDateTime()

    fun hm(ms: Long): String = fmt(dateTimeOf(ms).toLocalTime())

    /** t 是否落在 [start, end) 这个每天重复的时段里（可以跨午夜） */
    fun inWindow(t: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (start <= end) t >= start && t < end else t >= start || t < end

    fun inWindow(ms: Long, start: String, end: String): Boolean =
        inWindow(dateTimeOf(ms).toLocalTime(), parse(start), parse(end))
}
