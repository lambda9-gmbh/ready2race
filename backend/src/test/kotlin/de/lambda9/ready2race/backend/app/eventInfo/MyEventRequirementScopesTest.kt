package de.lambda9.ready2race.backend.app.eventInfo

import de.lambda9.ready2race.backend.app.eventInfo.boundary.MyEventLogic
import de.lambda9.ready2race.backend.app.eventInfo.control.MyEventRepo
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.RequirementScopeLogic
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Was das persönliche Dashboard einer Athletin über ihre Bedingungen sagt.
 *
 * Bis zum 15.08.2026 fragte es nur, OB eine Bestätigung existiert. Eine Steuerfrau, die für einen
 * von zwei Wettkämpfen gewogen war, sah deshalb beide als erledigt - und wer für den zweiten zur
 * Waage geschickt wurde, fand auf dem Telefon keinen Grund dafür. Die Regel lautet "je Tag und je
 * Wettkampf", nicht "je Lauf": Vorlauf, Viertelfinale, Halbfinale und Finale sind Runden desselben
 * Wettkampfs und teilen sich eine Bestätigung.
 */
class MyEventRequirementScopesTest {

    private val tagHeute = UUID.randomUUID()
    private val tagMorgen = UUID.randomUUID()
    private val heute = LocalDate.of(2026, 8, 16)
    private val morgen = LocalDate.of(2026, 8, 17)
    private val wettkampfA = UUID.randomUUID()
    private val wettkampfB = UUID.randomUUID()

    private val tage = listOf(
        RequirementScopeLogic.EventDayRef(tagHeute, heute),
        RequirementScopeLogic.EventDayRef(tagMorgen, morgen),
    )

    private fun lauf(competition: UUID, tag: LocalDate, stunde: Int, name: String) =
        MyEventLogic.RawMatch(
            matchId = UUID.randomUUID(),
            competitionId = competition,
            competitionIdentifier = if (competition == wettkampfA) "8" else "12",
            competitionShortName = if (competition == wettkampfA) "CMix4x+" else "CM2x",
            teamId = null,
            competitionName = "Wettkampf",
            categoryName = null,
            roundName = name,
            matchName = name,
            startTime = LocalDateTime.of(tag, java.time.LocalTime.of(stunde, 0)),
            actualStartTime = null,
            finishedAt = null,
            allTeamsScored = false,
            currentlyRunning = false,
            lane = null,
            teamName = null,
            clubName = null,
            teamMembers = emptyList(),
            place = null,
            timeString = null,
            penaltySeconds = null,
            penaltyNote = null,
            failed = false,
            failedReason = null,
            deregistered = false,
            deregisteredReason = null,
        )

    private val jeTagUndWettkampf =
        RequirementScopeLogic.Scope(perEventDay = true, perCompetition = true)

    private fun waage(requirementId: UUID, tag: UUID?, competition: UUID?) =
        MyEventRepo.Fulfillment(requirementId, tag, competition)

    /**
     * Vier Runden desselben Wettkampfs an einem Tag sind EIN Rahmen - und das Fenster rechnet
     * gegen den ersten davon, nicht gegen den zuletzt gefahrenen.
     */
    @Test
    fun allRoundsOfOneCompetitionShareOneScope() {
        val requirementId = UUID.randomUUID()
        val scopes = MyEventLogic.requirementScopes(
            scope = jeTagUndWettkampf,
            matches = listOf(
                lauf(wettkampfA, heute, 12, "Vorlauf"),
                lauf(wettkampfA, heute, 14, "Viertelfinale"),
                lauf(wettkampfA, heute, 16, "Halbfinale"),
                lauf(wettkampfA, heute, 18, "Finale"),
            ),
            eventDays = tage,
            fulfillments = listOf(waage(requirementId, tagHeute, wettkampfA)),
            fallbackStart = null,
            earliestMinutesBefore = 120,
            latestMinutesBefore = 60,
        )

        assertEquals(1, scopes.size, "vier Runden, ein Rahmen")
        assertTrue(scopes.single().fulfilled, "einmal gewogen deckt alle Runden ab")
        assertEquals(
            LocalDateTime.of(heute, java.time.LocalTime.of(10, 0)),
            scopes.single().checkFrom,
            "zwei Stunden vor dem ERSTEN Lauf, nicht vor dem gerade gezeigten",
        )
        assertEquals(LocalDateTime.of(heute, java.time.LocalTime.of(11, 0)), scopes.single().checkUntil)
    }

    /** Zwei gesteuerte Wettkämpfe: zweimal wiegen - und die Anzeige sagt, welcher noch fehlt. */
    @Test
    fun twoCompetitionsNeedTwoApprovals() {
        val requirementId = UUID.randomUUID()
        val scopes = MyEventLogic.requirementScopes(
            scope = jeTagUndWettkampf,
            matches = listOf(
                lauf(wettkampfA, heute, 12, "Vorlauf A"),
                lauf(wettkampfB, heute, 15, "Vorlauf B"),
            ),
            eventDays = tage,
            fulfillments = listOf(waage(requirementId, tagHeute, wettkampfA)),
            fallbackStart = null,
            earliestMinutesBefore = null,
            latestMinutesBefore = null,
        )

        assertEquals(
            listOf("8 CMix4x+" to true, "12 CM2x" to false),
            scopes.map { it.competitionName to it.fulfilled },
        )
    }

    /** Derselbe Wettkampf am nächsten Tag ist ein eigener Rahmen - die Waage von gestern zählt nicht. */
    @Test
    fun thenextDayIsItsOwnScope() {
        val requirementId = UUID.randomUUID()
        val scopes = MyEventLogic.requirementScopes(
            scope = jeTagUndWettkampf,
            matches = listOf(
                lauf(wettkampfA, heute, 12, "Vorlauf"),
                lauf(wettkampfA, morgen, 11, "Finale"),
            ),
            eventDays = tage,
            fulfillments = listOf(waage(requirementId, tagHeute, wettkampfA)),
            fallbackStart = null,
            earliestMinutesBefore = null,
            latestMinutesBefore = null,
        )

        assertEquals(2, scopes.size)
        assertEquals(listOf(true, false), scopes.map { it.fulfilled })
        assertEquals(listOf(heute, morgen), scopes.map { it.eventDayDate })
    }

    /**
     * Eine Bedingung ohne Schalter verhält sich wie vor V202608141900: ein Rahmen ohne Namen, das
     * Fenster am nächsten künftigen Start der Person.
     */
    @Test
    fun aRequirementWithoutSwitchesKeepsTheOldBehaviour() {
        val requirementId = UUID.randomUUID()
        val naechsterStart = LocalDateTime.of(heute, java.time.LocalTime.of(12, 0))
        val scopes = MyEventLogic.requirementScopes(
            scope = RequirementScopeLogic.Scope.forWholeEvent,
            matches = listOf(lauf(wettkampfA, heute, 12, "Vorlauf")),
            eventDays = tage,
            fulfillments = listOf(waage(requirementId, null, null)),
            fallbackStart = naechsterStart,
            earliestMinutesBefore = 120,
            latestMinutesBefore = 60,
        )

        assertEquals(1, scopes.size)
        assertTrue(scopes.single().fulfilled)
        assertEquals(null, scopes.single().competitionName, "ohne Wettkampfbezug kein Wettkampfname")
        assertEquals(
            LocalDateTime.of(heute, java.time.LocalTime.of(10, 0)),
            scopes.single().checkFrom,
        )
    }
}
