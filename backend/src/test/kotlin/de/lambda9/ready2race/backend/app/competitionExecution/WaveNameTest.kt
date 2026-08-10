package de.lambda9.ready2race.backend.app.competitionExecution

import de.lambda9.ready2race.backend.app.competitionExecution.entity.WaveName
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WaveNameTest {

    private val startTime = LocalDateTime.of(2026, 8, 17, 10, 30)

    @Test
    fun joinsTimeCompetitionAndMatchName() {
        assertEquals("10:30 | 12 JM4x | AF1", WaveName.format("AF1", startTime, "12", "JM4x"))
    }

    @Test
    fun dropsTheShortNameWhenTheCompetitionHasNone() {
        assertEquals("10:30 | 12 | AF1", WaveName.format("AF1", startTime, "12", null))
        assertEquals("10:30 | 12 | AF1", WaveName.format("AF1", startTime, "12", "  "))
    }

    @Test
    fun dropsTheTimeBlockWithoutAStartTime() {
        assertEquals("12 JM4x | AF1", WaveName.format("AF1", null, "12", "JM4x"))
    }

    @Test
    fun dropsTheMatchNameBlockWithoutAMatchName() {
        assertEquals("10:30 | 12 JM4x", WaveName.format(null, startTime, "12", "JM4x"))
    }

    @Test
    fun dropsTheCompetitionBlockWhenNeitherIdentifierNorShortNameIsKnown() {
        assertEquals("10:30 | AF1", WaveName.format("AF1", startTime, null, null))
    }

    @Test
    fun staysNullWhenNothingIsKnown() {
        assertNull(WaveName.format(null, null, null, null))
    }

    @Test
    fun padsHoursAndMinutesToTwoDigits() {
        assertEquals("09:05 | 3 JM1x | TT1", WaveName.format("TT1", LocalDateTime.of(2026, 8, 17, 9, 5), "3", "JM1x"))
    }
}
