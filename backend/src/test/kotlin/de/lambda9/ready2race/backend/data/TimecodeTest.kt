package de.lambda9.ready2race.backend.data

import de.lambda9.ready2race.backend.parsing.Parser
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrNull
import de.lambda9.tailwind.core.extensions.kio.orDie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TimecodeTest {

    @Test
    fun stringToTimecodeWithLeadingZerosTest() {
        var time = "00000014070:34.802"
        var code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNotNull(code, "the String could not be parsed to a Timecode: $time")
        assertEquals(code.millis , 844234802, "milliseconds is incorrect")
        assertEquals(code.baseUnit, Timecode.BaseUnit.MINUTES, "base time unit is incorrect")
        assertEquals(code.millisecondPrecision, Timecode.MillisecondPrecision.THREE,"millisecondPrecision is incorrect")

        time = "+00014070:34.802"
        code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNotNull(code, "the String could not be parsed to a Timecode: $time")
        assertEquals(code.millis , 844234802, "milliseconds is incorrect")
        assertEquals(code.baseUnit, Timecode.BaseUnit.MINUTES, "base time unit is incorrect")
        assertEquals(code.millisecondPrecision, Timecode.MillisecondPrecision.THREE,"millisecondPrecision is incorrect")

        time = "-00014070:34.802"
        code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNotNull(code, "the String could not be parsed to a Timecode: $time")
        assertEquals(code.millis , -844234802, "milliseconds is incorrect")
        assertEquals(code.baseUnit, Timecode.BaseUnit.MINUTES, "base time unit is incorrect")
        assertEquals(code.millisecondPrecision, Timecode.MillisecondPrecision.THREE,"millisecondPrecision is incorrect")

        time = "+07:04"
        code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNotNull(code, "the String could not be parsed to a Timecode: $time")
        assertEquals(code.millis , 424000, "milliseconds is incorrect")
        assertEquals(code.baseUnit, Timecode.BaseUnit.MINUTES, "base time unit is incorrect")
        assertEquals(code.millisecondPrecision, Timecode.MillisecondPrecision.NONE,"millisecondPrecision is incorrect")

        time = "-01:07:00.042"
        code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNotNull(code, "the String could not be parsed to a Timecode: $time")
        assertEquals(code.millis , -4020042, "milliseconds is incorrect")
        assertEquals(code.baseUnit, Timecode.BaseUnit.HOURS, "base time unit is incorrect")
        assertEquals(code.millisecondPrecision, Timecode.MillisecondPrecision.THREE,"millisecondPrecision is incorrect")

        time = "14070:034"
        code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNull(code, "the String should not be parsed to a Timecode: $time")

    }

    @Test
    fun stringToTimecodeBaseUnitTests() {
        var time = "+234:30:34.802"
        var code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNotNull(code, "the String could not be parsed to a Timecode: $time")
        assertEquals(code.millis , 844234802, "milliseconds is incorrect")
        assertEquals(code.baseUnit, Timecode.BaseUnit.HOURS, "base time unit is incorrect")
        assertEquals(code.millisecondPrecision, Timecode.MillisecondPrecision.THREE,"millisecondPrecision is incorrect")

        time = "14070:34.802"
        code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNotNull(code, "the String could not be parsed to a Timecode: $time")
        assertEquals(code.millis , 844234802, "milliseconds is incorrect")
        assertEquals(code.baseUnit, Timecode.BaseUnit.MINUTES, "base time unit is incorrect")
        assertEquals(code.millisecondPrecision, Timecode.MillisecondPrecision.THREE,"millisecondPrecision is incorrect")

        time = "844234.802"
        code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNotNull(code, "the String could not be parsed to a Timecode: $time")
        assertEquals(code.millis , 844234802, "milliseconds is incorrect")
        assertEquals(code.baseUnit, Timecode.BaseUnit.SECONDS, "base time unit is incorrect")
        assertEquals(code.millisecondPrecision, Timecode.MillisecondPrecision.THREE,"millisecondPrecision is incorrect")
    }

    @Test
    fun stringToTimecodeMillisecondPrecisionTest() {
        var time = "234:30:34.802"
        var code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNotNull(code, "the String could not be parsed to a Timecode: $time")
        assertEquals(844234802, code.millis , "milliseconds is incorrect")
        assertEquals(Timecode.BaseUnit.HOURS, code.baseUnit, "base time unit is incorrect")
        assertEquals(Timecode.MillisecondPrecision.THREE,code.millisecondPrecision, "millisecondPrecision is incorrect")

        time = "14070:34.80"
        code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNotNull(code, "the String could not be parsed to a Timecode: $time")
        assertEquals(844234800, code.millis , "milliseconds is incorrect")
        assertEquals(Timecode.BaseUnit.MINUTES, code.baseUnit, "base time unit is incorrect")
        assertEquals(Timecode.MillisecondPrecision.TWO,code.millisecondPrecision, "millisecondPrecision is incorrect")

        time = "-14070:34.8"
        code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNotNull(code, "the String could not be parsed to a Timecode: $time")
        assertEquals(-844234800,code.millis ,  "milliseconds is incorrect")
        assertEquals(Timecode.BaseUnit.MINUTES, code.baseUnit, "base time unit is incorrect")
        assertEquals(Timecode.MillisecondPrecision.ONE,code.millisecondPrecision, "millisecondPrecision is incorrect")

        time = "844234"
        code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNotNull(code, "the String could not be parsed to a Timecode: $time")
        assertEquals(844234000, code.millis , "milliseconds is incorrect")
        assertEquals(Timecode.BaseUnit.SECONDS, code.baseUnit, "base time unit is incorrect")
        assertEquals(Timecode.MillisecondPrecision.NONE,code.millisecondPrecision, "millisecondPrecision is incorrect")

        time = "+14070:34."
        code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNull(code, "the String should not be parsed to a Timecode: $time")
    }

    @Test
    fun stringToTimecodeOver59Test() {
        var time = "234:70:34.802"
        var code = Parser.timecode(time) { it }.orDie().unsafeRunSync().getOrNull()
        assertNull(code, "the String should not be parsed to a Timecode: $time")

        time = "1:87.7"
        code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNull(code, "the String should not be parsed to a Timecode: $time")

        time = "+168:66:45"
        code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNull(code, "the String should not be parsed to a Timecode: $time")

        time = "+1:87"
        code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNull(code, "the String should not be parsed to a Timecode: $time")

        time = "66:66:66.666"
        code = Parser.timecode(time) {it}.orDie().unsafeRunSync().getOrNull()
        assertNull(code, "the String should not be parsed to a Timecode: $time")
    }

    @Test
    fun timecodesToStringsTest(){
        var timecode = Timecode(
            millis = 844234802,
            baseUnit = Timecode.BaseUnit.MINUTES,
            millisecondPrecision = Timecode.MillisecondPrecision.THREE
        )
        assertEquals("14070:34.802", timecode.toString(), "timecode string representation is incorrect")

        timecode = Timecode(
            millis = -844234802,
            baseUnit = Timecode.BaseUnit.HOURS,
            millisecondPrecision = Timecode.MillisecondPrecision.TWO
        )
        assertEquals("-234:30:34.80",timecode.toString(),"timecode string representation is incorrect")

        timecode = Timecode(
            millis = -84234802,
            baseUnit = Timecode.BaseUnit.SECONDS,
            millisecondPrecision = Timecode.MillisecondPrecision.ONE
        )
        assertEquals("-84234.8",timecode.toString(),"timecode string representation is incorrect")

        timecode = Timecode(
            millis = 2234802,
            baseUnit = Timecode.BaseUnit.MINUTES,
            millisecondPrecision = Timecode.MillisecondPrecision.NONE
        )
        assertEquals("37:14", timecode.toString(), "timecode string representation is incorrect")
    }

    @Test
    fun displayPrecisionStaysCoarseWithoutCollisionTest() {
        // 22:00.0, 22:17.0, 22:34.5 - bei einer Nachkommastelle klar unterscheidbar
        val times = listOf(1320000L, 1337000L, 1354500L)
        assertEquals(
            Timecode.MillisecondPrecision.ONE,
            Timecode.displayPrecision(times),
            "distinct times must keep the coarse default precision",
        )
    }

    @Test
    fun displayPrecisionEscalatesOnCollisionTest() {
        // 22:00.04 und 22:00.09 fallen bei einer Nachkommastelle beide auf "22:00.0"
        val two = listOf(1320040L, 1320090L)
        assertEquals(
            Timecode.MillisecondPrecision.TWO,
            Timecode.displayPrecision(two),
            "times colliding at ONE must escalate to TWO",
        )

        // 22:00.004 und 22:00.009 kollidieren auch bei zwei Nachkommastellen
        val three = listOf(1320004L, 1320009L)
        assertEquals(
            Timecode.MillisecondPrecision.THREE,
            Timecode.displayPrecision(three),
            "times colliding at TWO must escalate to THREE",
        )
    }

    @Test
    fun displayPrecisionIgnoresDeadHeatsAndEmptyTest() {
        // Totes Rennen: identische Zeiten sind kein Kollisionsgrund
        val deadHeat = listOf(1320000L, 1320000L, 1337000L)
        assertEquals(
            Timecode.MillisecondPrecision.ONE,
            Timecode.displayPrecision(deadHeat),
            "identical times must not force finer precision",
        )

        assertEquals(
            Timecode.MillisecondPrecision.ONE,
            Timecode.displayPrecision(emptyList()),
            "no times must fall back to the default precision",
        )
    }

}