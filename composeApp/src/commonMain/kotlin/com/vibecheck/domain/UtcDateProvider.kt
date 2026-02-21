package com.vibecheck.domain

import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

interface UtcDateProvider {
    fun currentDate(): LocalDate
}

object SystemUtcDateProvider : UtcDateProvider {
    override fun currentDate(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
}
