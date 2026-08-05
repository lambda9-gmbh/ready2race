package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.competitionExecution.entity.WaveName
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WaveNameTest {

    private val startTime = LocalDateTime.of(2026, 8, 17, 10, 30)

    @Test
    fun prependsTheFormattedStartTimeWhenSet() {
        assertEquals("10:30 AF1", WaveName.format("AF1", startTime))
    }

    @Test
    fun leavesTheNameUnchangedWithoutAStartTime() {
        assertEquals("AF1", WaveName.format("AF1", null))
    }

    @Test
    fun staysNullWithoutAStartTimeAndWithoutAName() {
        assertNull(WaveName.format(null, null))
    }

    @Test
    fun fallsBackToJustTheTimeWithoutAMatchName() {
        assertEquals("10:30", WaveName.format(null, startTime))
    }

    @Test
    fun padsHoursAndMinutesToTwoDigits() {
        assertEquals("09:05 TT1", WaveName.format("TT1", LocalDateTime.of(2026, 8, 17, 9, 5)))
    }
}
