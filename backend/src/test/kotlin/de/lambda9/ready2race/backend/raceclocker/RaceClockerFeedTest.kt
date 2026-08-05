package de.lambda9.ready2race.backend.raceclocker

import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerFeed
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerError
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerFeedRow
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrNull
import io.ktor.http.*
import java.time.LocalTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verified against the real feeds of the RaceClocker reference races from issue #94; the fixture keeps
 * their exact field shape and the deliberate DNF/DNS/DQ cases, with the registration ids added that a
 * start list exported by ready2race would carry.
 */
class RaceClockerFeedTest {

    private fun feed(): List<RaceClockerFeedRow> {
        val body = javaClass.getResourceAsStream("/raceclocker/feed.json")!!.bufferedReader().readText()
        val rows = RaceClockerFeed.parse(body).unsafeRunSync().getOrNull()
        assertNotNull(rows, "fixture feed could not be parsed")
        return rows
    }

    private fun uuidOf(bib: Int) = UUID.fromString("00000000-0000-4000-8000-%012d".format(bib))

    @Test
    fun trailingRaceInfoObjectIsNotAParticipant() {
        // The feed ends with a single RaceInfo object describing the race itself.
        assertEquals(8, feed().size, "RaceInfo must not be read as a participant row")
    }

    @Test
    fun heatRowsAreFoundByWaveName() {
        val af1 = feed().filter { it.wave == "AF1 CM1x" }
        assertEquals(listOf(4, 13), af1.map { it.bib }, "wave AF1 CM1x should hold exactly its two boats")
    }

    @Test
    fun timeTrialRowsHaveNoWave() {
        // Individual-start races have no waves; RaceClocker writes a placeholder ("None"/"Kein").
        val tt = feed().filter { it.wave == null }
        assertEquals(2, tt.size)
        assertTrue(tt.all { it.wave == null }, "the 'None' placeholder must not be read as a wave name")
    }

    @Test
    fun penaltyIsReadForDisplayWithoutTouchingTheTime() {
        // RaceClocker has already added the penalty to the result, so the time must stay as sent —
        // the penalty travels alongside it purely so referees can see why a time looks the way it does.
        val row = feed().single { it.bib == 7 }
        assertEquals(15, row.penaltySeconds)
        assertEquals("Gegner Verhindert", row.penaltyNote)
        assertEquals(row.result, row.time, "the penalty must not be applied to the time")
    }

    @Test
    fun zeroPenaltyIsReadAsNoPenalty() {
        // The feed writes "0" and an empty note for everyone without a penalty.
        val row = feed().single { it.bib == 4 }
        assertNull(row.penaltySeconds)
        assertNull(row.penaltyNote)
    }

    @Test
    fun rankIsReadAsTheListPosition() {
        // "Rank" is RaceClocker's list position, not a finishing rank: in the fixture the two boats of
        // wave AF1 have been swapped, so the DNF boat sits in front of the one with a time.
        val af1 = feed().filter { it.wave == "AF1 CM1x" }
        assertEquals(listOf(13, 4), af1.sortedBy { it.rank }.map { it.bib })
    }

    @Test
    fun lanesFollowTheListPositionWithinTheMatch() {
        // Rank counts across the whole race (1..8 here), so only its order inside one match matters.
        val af4 = feed().filter { it.wave == "AF4 CM1x" }.associateBy { it.bib!! }
        assertEquals(mapOf(7 to 1, 10 to 2), RaceClockerFeedRow.lanesByRow(af4))

        // The swapped wave: the boat that was moved to the front takes lane 1, and its bib and time
        // stay with it — that is exactly what a lane swap looks like in the feed.
        val af1 = feed().filter { it.wave == "AF1 CM1x" }.associateBy { it.bib!! }
        assertEquals(mapOf(13 to 1, 4 to 2), RaceClockerFeedRow.lanesByRow(af1))
        assertEquals("00:02:22.5", af1.getValue(4).time, "the moved entry keeps its own time")
    }

    @Test
    fun rowsWithoutRankAreNumberedLast() {
        // A feed without the field must not shuffle lanes — unranked rows keep their order at the end.
        val withRank = feed().single { it.bib == 7 }
        val withoutRank = feed().single { it.bib == 10 }.copy(rank = null)
        assertEquals(
            mapOf(7 to 1, 10 to 2),
            RaceClockerFeedRow.lanesByRow(mapOf(7 to withRank, 10 to withoutRank)),
        )
    }

    @Test
    fun formattedTimeIsReadAsTime() {
        val row = feed().single { it.bib == 4 }
        assertEquals("00:02:22.5", row.time)
        assertNull(row.noResultReason)
        assertTrue(row.isTime)
    }

    @Test
    fun statusTextIsReadAsNoResultReason() {
        // RaceClocker has no status field: DNS/DNF/DQ arrive as text in the result column.
        val byBib = feed().associateBy { it.bib }

        listOf(13 to "DNF", 10 to "DNS", 6 to "DQ").forEach { (bib, expected) ->
            val row = byBib[bib]!!
            assertEquals(expected, row.noResultReason, "bib $bib should carry a no-result reason")
            assertNull(row.time, "bib $bib must not yield a time")
            assertTrue(!row.isTime)
        }
    }

    @Test
    fun startTimeIsParsedFromFeed() {
        // The fixture carries "Start": "11:00:00.0" for this participant.
        val row = feed().single { it.bib == 4 }
        assertEquals(LocalTime.of(11, 0), row.start)
    }

    @Test
    fun startTimeParsesEveryFixtureRowWithARealResult() {
        // None of the fixture's "H:mm:ss.d" values should be rejected as unparsable.
        val rowsWithResult = feed().filter { it.isTime }
        assertTrue(rowsWithResult.isNotEmpty())
        assertTrue(rowsWithResult.all { it.start != null }, "every timed fixture row carries a parsable Start value")
    }

    @Test
    fun startTimeIsMissingForFixtureRowsWithTheMidnightSentinel() {
        // The DNF/DNS/DQ rows (bibs 13, 10, 6) carry RaceClocker's "00:00:00.0" placeholder as Start.
        val noResultRows = feed().filter { it.noResultReason != null }
        assertEquals(3, noResultRows.size)
        assertTrue(noResultRows.all { it.start == null }, "the midnight sentinel must not be read as a real start")
    }

    @Test
    fun midnightStartIsTreatedAsMissing() {
        // RaceClocker writes "00:00:00.0" as a placeholder for boats without a real start
        // (DNS/DNF/DQ) - midnight is never an actual start time at a regatta.
        val body = """[{"Name":"Test","Bib number":"1","Wave":"None","Result":"DNS","Start":"00:00:00.0"}]"""
        val row = RaceClockerFeed.parse(body).unsafeRunSync().getOrNull()!!.single()
        assertNull(row.start, "the midnight sentinel must not be read as a real start")
    }

    @Test
    fun earliestStartIgnoresTheMidnightSentinel() {
        val body = """
            [
              {"Name":"Sentinel","Bib number":"1","Wave":"None","Result":"DNS","Start":"00:00:00.0"},
              {"Name":"Real","Bib number":"2","Wave":"None","Result":"00:01:00.0","Start":"11:00:00.0"}
            ]
        """.trimIndent()
        val rows = RaceClockerFeed.parse(body).unsafeRunSync().getOrNull()!!
        assertEquals(LocalTime.of(11, 0), RaceClockerFeedRow.earliestStart(rows))
    }

    @Test
    fun earliestStartPicksTheMinimumAcrossRows() {
        val rows = listOf(
            rowWithStart(LocalTime.of(11, 5)),
            rowWithStart(LocalTime.of(11, 0)),
            rowWithStart(LocalTime.of(11, 30)),
        )
        assertEquals(LocalTime.of(11, 0), RaceClockerFeedRow.earliestStart(rows))
    }

    @Test
    fun earliestStartIgnoresRowsWithoutAStart() {
        val rows = listOf(
            rowWithStart(null),
            rowWithStart(LocalTime.of(11, 15)),
            rowWithStart(null),
        )
        assertEquals(LocalTime.of(11, 15), RaceClockerFeedRow.earliestStart(rows))
    }

    @Test
    fun earliestStartIsNullWhenNoRowHasOne() {
        val rows = listOf(rowWithStart(null), rowWithStart(null))
        assertNull(RaceClockerFeedRow.earliestStart(rows))
    }

    @Test
    fun earliestStartOfEmptyListIsNull() {
        assertNull(RaceClockerFeedRow.earliestStart(emptyList()))
    }

    private fun rowWithStart(start: LocalTime?) = RaceClockerFeedRow(
        name = "Test",
        rank = null,
        bib = null,
        wave = null,
        ids = emptyList(),
        result = "00:01:00.0",
        start = start,
        penaltySeconds = null,
        penaltyNote = null,
    )

    @Test
    fun identifiersAreReadFromExtraInfo() {
        assertEquals(listOf(uuidOf(4)), feed().single { it.bib == 4 }.ids)
        assertEquals(listOf(uuidOf(13)), feed().single { it.bib == 13 }.ids)
    }

    @Test
    fun missingExtraInfoMappingYieldsNoIdentifier() {
        // The column was not mapped in RaceClocker's importer - such rows cannot be assigned.
        assertTrue(feed().single { it.bib == 2 }.ids.isEmpty())
    }

    @Test
    fun bibIsParsedFromString() {
        // The feed returns bib numbers as strings, not numbers.
        assertTrue(feed().all { it.bib != null || it.ids.isEmpty() })
        assertEquals(13, feed().single { it.noResultReason == "DNF" }.bib)
    }

    @Test
    fun validUrlsAreAccepted() {
        listOf(
            "https://www.raceclocker.com/7c854955",
            "https://raceclocker.com/7c854955",
            "  https://www.raceclocker.com/7c854955  ",
        ).forEach { raw ->
            val url = RaceClockerFeed.normalizeUrl(raw).unsafeRunSync().getOrNull()
            assertNotNull(url, "should be accepted: $raw")
            assertEquals("1", RaceClockerFeed.feedUrl(url).parameters["json"], "json=1 must be appended: $raw")
        }
    }

    @Test
    fun missingSchemeIsFilledIn() {
        // How the URL looks when copied out of a browser address bar.
        listOf(
            "www.raceclocker.com/7c854955",
            "raceclocker.com/7c854955",
        ).forEach { raw ->
            val url = RaceClockerFeed.normalizeUrl(raw).unsafeRunSync().getOrNull()
            assertNotNull(url, "should be accepted: $raw")
            assertEquals(URLProtocol.HTTPS, url.protocol, "scheme must default to https: $raw")
            assertEquals("/7c854955", url.encodedPath)
        }
    }

    @Test
    fun plainHttpIsLiftedToHttps() {
        // What keeps this endpoint from being an SSRF lever is the host allowlist, not the scheme.
        val url = RaceClockerFeed.normalizeUrl("http://www.raceclocker.com/7c854955").unsafeRunSync().getOrNull()
        assertNotNull(url)
        assertEquals("https://www.raceclocker.com/7c854955", url.toString())
    }

    @Test
    fun longFormUrlKeepsItsExistingQuery() {
        val url = RaceClockerFeed
            .normalizeUrl("https://www.raceclocker.com/Event_Result.php?EIDK=e73d5ce6")
            .unsafeRunSync().getOrNull()
        assertNotNull(url)
        assertEquals("e73d5ce6", url.parameters["EIDK"], "existing query parameters must survive")
        assertEquals("1", RaceClockerFeed.feedUrl(url).parameters["json"])
    }

    @Test
    fun foreignHostsAreRejected() {
        // The URL is operator-supplied, so this check is what keeps the endpoint from being an SSRF lever.
        listOf(
            "https://evil.example.com/7c854955",
            "https://raceclocker.com.evil.example.com/7c854955",
            "http://169.254.169.254/latest/meta-data/",
            "169.254.169.254/latest/meta-data/",
            "file:///etc/passwd",
            "not a url at all",
        ).forEach { raw ->
            val result = RaceClockerFeed.normalizeUrl(raw).unsafeRunSync()
            assertTrue(
                result.getOrNull() == null,
                "should be rejected: $raw",
            )
        }
    }

    @Test
    fun malformedBodyIsReportedAsMalformedFeed() {
        listOf("not json", "{\"RaceInfo\":{}}").forEach { body ->
            val exit = RaceClockerFeed.parse(body).unsafeRunSync()
            assertNull(exit.getOrNull(), "should be rejected: $body")
        }
    }
}
