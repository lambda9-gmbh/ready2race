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

}