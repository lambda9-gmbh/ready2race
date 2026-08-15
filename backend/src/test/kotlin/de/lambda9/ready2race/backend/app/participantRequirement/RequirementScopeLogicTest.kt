package de.lambda9.ready2race.backend.app.participantRequirement

import de.lambda9.ready2race.backend.app.participantRequirement.boundary.RequirementScopeLogic
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.RequirementScopeLogic.EventDayRef
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.RequirementScopeLogic.Fulfillment
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.RequirementScopeLogic.MatchScope
import de.lambda9.ready2race.backend.app.participantRequirement.boundary.RequirementScopeLogic.Scope
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Die vier Geltungsbereiche einer Bedingung, ohne Datenbank geprüft. Der Anlass ist ein
 * Verfahrensfehler vom ersten Regattatag 2026: Eine Bedingung galt einmal je Person und
 * Veranstaltung und lief nie aus, obwohl eine Waage oder eine Bootsabnahme an jedem Tag neu
 * fällig ist.
 */
class RequirementScopeLogicTest {

    private val tag1: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val tag2: UUID = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val wettkampfA: UUID = UUID.fromString("aaaaaaaa-1111-1111-1111-111111111111")
    private val wettkampfB: UUID = UUID.fromString("bbbbbbbb-1111-1111-1111-111111111111")

    private val laufTag1A = MatchScope(eventDay = tag1, competition = wettkampfA)
    private val laufTag2A = MatchScope(eventDay = tag2, competition = wettkampfA)
    private val laufTag1B = MatchScope(eventDay = tag1, competition = wettkampfB)

    // -------------------------------------------------------------------------------------
    // Je Veranstaltung: das Verhalten vor V202608141900 - und damit die Zusicherung, dass die
    // Migration keiner bestehenden Bedingung etwas anderes unterschiebt.
    // -------------------------------------------------------------------------------------

    @Test
    fun forWholeEventAnyRowCountsEverywhere() {
        val scope = Scope.forWholeEvent
        // Die Zeile aus der Bestandsmigration trägt den ersten Wettkampftag. Genau der darf hier
        // nichts ändern: bei ausgeschaltetem Schalter wird die Spalte nicht verglichen.
        val bestand = listOf(Fulfillment(eventDay = tag1, competition = null))

        assertTrue(RequirementScopeLogic.isFulfilled(scope, bestand, laufTag1A))
        assertTrue(RequirementScopeLogic.isFulfilled(scope, bestand, laufTag2A))
        assertTrue(RequirementScopeLogic.isFulfilled(scope, bestand, laufTag1B))
    }

    @Test
    fun withoutAnyRowNothingIsFulfilled() {
        assertFalse(RequirementScopeLogic.isFulfilled(Scope.forWholeEvent, emptyList(), laufTag1A))
    }

    // -------------------------------------------------------------------------------------
    // Je Tag
    // -------------------------------------------------------------------------------------

    @Test
    fun perEventDayExpiresWithTheDay() {
        val scope = Scope(perEventDay = true, perCompetition = false)
        val amTag1 = listOf(Fulfillment(eventDay = tag1, competition = null))

        assertTrue(RequirementScopeLogic.isFulfilled(scope, amTag1, laufTag1A))
        // Derselbe Wettkampf, anderer Tag: der Nachweis von gestern trägt nicht.
        assertFalse(RequirementScopeLogic.isFulfilled(scope, amTag1, laufTag2A))
        // Anderer Wettkampf, aber derselbe Tag: der Wettkampf interessiert hier nicht.
        assertTrue(RequirementScopeLogic.isFulfilled(scope, amTag1, laufTag1B))
    }

    @Test
    fun perEventDayIgnoresARowWithoutADay() {
        val scope = Scope(perEventDay = true, perCompetition = false)
        val ohneTag = listOf(Fulfillment(eventDay = null, competition = null))

        // Die vorsichtige Richtung: lieber ein Fehlalarm an der Meldestelle als jemand ohne
        // gültige Prüfung am Start.
        assertFalse(RequirementScopeLogic.isFulfilled(scope, ohneTag, laufTag1A))
        // Und umgekehrt: ist der Tag des Laufs nicht bestimmbar, deckt ihn keine Tageszeile ab.
        assertFalse(
            RequirementScopeLogic.isFulfilled(
                scope,
                listOf(Fulfillment(eventDay = tag1, competition = null)),
                MatchScope(eventDay = null, competition = wettkampfA),
            )
        )
    }

    // -------------------------------------------------------------------------------------
    // Je Wettkampf und je Wettkampf-und-Tag
    // -------------------------------------------------------------------------------------

    @Test
    fun perCompetitionIsBoundToTheCompetitionOnly() {
        val scope = Scope(perEventDay = false, perCompetition = true)
        val fuerA = listOf(Fulfillment(eventDay = null, competition = wettkampfA))

        assertTrue(RequirementScopeLogic.isFulfilled(scope, fuerA, laufTag1A))
        // Ein anderer Tag ändert nichts, solange der Wettkampf derselbe ist.
        assertTrue(RequirementScopeLogic.isFulfilled(scope, fuerA, laufTag2A))
        assertFalse(RequirementScopeLogic.isFulfilled(scope, fuerA, laufTag1B))
    }

    @Test
    fun perCompetitionAndDayNeedsBoth() {
        val scope = Scope(perEventDay = true, perCompetition = true)
        val fuerATag1 = listOf(Fulfillment(eventDay = tag1, competition = wettkampfA))

        assertTrue(RequirementScopeLogic.isFulfilled(scope, fuerATag1, laufTag1A))
        assertFalse(RequirementScopeLogic.isFulfilled(scope, fuerATag1, laufTag2A))
        assertFalse(RequirementScopeLogic.isFulfilled(scope, fuerATag1, laufTag1B))
    }

    @Test
    fun oneMatchingRowAmongManyIsEnough() {
        val scope = Scope(perEventDay = true, perCompetition = true)
        val alle = listOf(
            Fulfillment(eventDay = tag1, competition = wettkampfB),
            Fulfillment(eventDay = tag2, competition = wettkampfA),
            Fulfillment(eventDay = tag1, competition = wettkampfA),
        )
        assertTrue(RequirementScopeLogic.isFulfilled(scope, alle, laufTag1A))
    }

    // -------------------------------------------------------------------------------------
    // Was beim Abhaken geschrieben wird
    // -------------------------------------------------------------------------------------

    @Test
    fun onlySwitchedOnDimensionsAreWritten() {
        val lauf = laufTag1A

        assertEquals(Fulfillment(null, null), RequirementScopeLogic.keyFor(Scope.forWholeEvent, lauf))
        assertEquals(
            Fulfillment(tag1, null),
            RequirementScopeLogic.keyFor(Scope(perEventDay = true, perCompetition = false), lauf),
        )
        assertEquals(
            Fulfillment(null, wettkampfA),
            RequirementScopeLogic.keyFor(Scope(perEventDay = false, perCompetition = true), lauf),
        )
        assertEquals(
            Fulfillment(tag1, wettkampfA),
            RequirementScopeLogic.keyFor(Scope(perEventDay = true, perCompetition = true), lauf),
        )
    }

    /** Was geschrieben wird, muss die eigene Prüfung auch bestehen - sonst hakt niemand je ab. */
    @Test
    fun whatIsWrittenCoversTheMatchItWasWrittenFor() {
        listOf(
            Scope.forWholeEvent,
            Scope(perEventDay = true, perCompetition = false),
            Scope(perEventDay = false, perCompetition = true),
            Scope(perEventDay = true, perCompetition = true),
        ).forEach { scope ->
            val geschrieben = RequirementScopeLogic.keyFor(scope, laufTag1A)
            assertTrue(
                RequirementScopeLogic.isFulfilled(scope, listOf(geschrieben), laufTag1A),
                "Geltungsbereich $scope deckt den eigenen Lauf nicht ab",
            )
        }
    }

    // -------------------------------------------------------------------------------------
    // Der Tag eines Laufs
    // -------------------------------------------------------------------------------------

    private val samstag = LocalDate.of(2026, 8, 15)
    private val sonntag = LocalDate.of(2026, 8, 16)

    @Test
    fun theDayComesFromTheStartTimeWhenTheCompetitionRunsOnSeveral() {
        val tage = listOf(EventDayRef(tag1, samstag), EventDayRef(tag2, sonntag))

        assertEquals(tag1, RequirementScopeLogic.eventDayOf(samstag.atTime(10, 30), tage))
        assertEquals(tag2, RequirementScopeLogic.eventDayOf(sonntag.atTime(9, 0), tage))
    }

    @Test
    fun aSingleDayWinsWithoutAStartTime() {
        val tage = listOf(EventDayRef(tag1, samstag))

        // Ein noch nicht terminierter Lauf eines eintägigen Wettkampfs hat trotzdem seinen Tag.
        assertEquals(tag1, RequirementScopeLogic.eventDayOf(null, tage))
        // Und auch dann, wenn die Startzeit auf einen Tag zeigt, den es hier gar nicht gibt -
        // etwa nach einer Zeitplanänderung über Mitternacht hinaus.
        assertEquals(tag1, RequirementScopeLogic.eventDayOf(sonntag.atTime(9, 0), tage))
    }

    @Test
    fun ambiguousOrMissingDaysYieldNothing() {
        val tage = listOf(EventDayRef(tag1, samstag), EventDayRef(tag2, sonntag))

        // Zwei Tage, keine Startzeit: es gibt keinen begründbaren Tag.
        assertEquals(null, RequirementScopeLogic.eventDayOf(null, tage))
        // Startzeit an einem dritten Datum, mehrere Tage zur Auswahl: ebenfalls nichts.
        assertEquals(null, RequirementScopeLogic.eventDayOf(LocalDate.of(2026, 8, 17).atTime(9, 0), tage))
        assertEquals(null, RequirementScopeLogic.eventDayOf(samstag.atTime(9, 0), emptyList()))
    }

    @Test
    fun twoDaysOnTheSameDateResolveDeterministically() {
        // Fehlkonfiguration, aber sie darf nicht zu wechselnden Antworten führen: es gewinnt
        // dieselbe Zeile wie beim Nachtragen der Bestandsdaten in der Migration.
        val tage = listOf(EventDayRef(tag2, samstag), EventDayRef(tag1, samstag))
        assertEquals(tag1, RequirementScopeLogic.eventDayOf(samstag.atTime(10, 0), tage))
    }

    // -------------------------------------------------------------------------------------
    // Prüffenster
    // -------------------------------------------------------------------------------------

    private val start: LocalDateTime = LocalDateTime.of(2026, 8, 15, 12, 0)

    @Test
    fun theWindowIsCountedBackFromTheGivenReference() {
        val fenster = RequirementScopeLogic.window(start, earliestMinutesBefore = 120, latestMinutesBefore = 15)
        assertEquals(start.minusMinutes(120), fenster.from)
        assertEquals(start.minusMinutes(15), fenster.until)
    }

    @Test
    fun halfAWindowStaysHalf() {
        val fenster = RequirementScopeLogic.window(start, earliestMinutesBefore = null, latestMinutesBefore = 15)
        assertEquals(null, fenster.from)
        assertEquals(start.minusMinutes(15), fenster.until)

        // Ohne Bezugspunkt gibt es gar keins - ein erfundenes wäre schlimmer als keins.
        val ohneBezug = RequirementScopeLogic.window(null, earliestMinutesBefore = 120, latestMinutesBefore = 15)
        assertEquals(null, ohneBezug.from)
        assertEquals(null, ohneBezug.until)
    }

    @Test
    fun perCompetitionMeasuresAgainstTheMatchInQuestion() {
        val naechsterEigenerStart = start.minusHours(3)

        // Der springende Punkt: Wer an einem Tag in zwei Wettkämpfen startet, bekäme sonst die
        // Grenzen des falschen Rennens zu sehen.
        assertEquals(
            start,
            RequirementScopeLogic.referencePoint(
                Scope(perEventDay = false, perCompetition = true),
                matchStart = start,
                fallback = naechsterEigenerStart,
            ),
        )
        // Ohne den Schalter bleibt es beim Bezugspunkt, den "Mein Event" bisher benutzt.
        assertEquals(
            naechsterEigenerStart,
            RequirementScopeLogic.referencePoint(
                Scope.forWholeEvent,
                matchStart = start,
                fallback = naechsterEigenerStart,
            ),
        )
    }
}
