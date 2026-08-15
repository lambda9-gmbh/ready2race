package de.lambda9.ready2race.backend.app.participantRequirement

import de.lambda9.ready2race.backend.app.eventInfo.MyEventFixture
import de.lambda9.ready2race.backend.app.participantRequirement.control.ParticipantScanScopeRepo
import de.lambda9.ready2race.testing.testComprehension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Der Bezugsrahmen, den die Scan-App an der Waage abfragt: in welchen Wettkämpfen ist die
 * gescannte Person gemeldet?
 *
 * Warum das eigene Tests bekommt: Die Antwort füllt die Auswahlliste, aus der heraus abgehakt
 * wird. Stünde dort ein Wettkampf zu viel, könnte die Waage eine Bestätigung für ein Rennen
 * eintragen, in dem die Person gar nicht startet - und die Prüfung vor dem Start hielte das
 * für erledigt. Stünde einer zu wenig, käme jemand nicht durch die Meldestelle.
 */
class ParticipantScanScopeTest {

    /**
     * Die Fixture meldet die Person in zwei Wettkämpfen an (einer mit gesetztem Lauf, einer
     * ohne). Genau die beiden - und keinen der fremden - muss die Waage anbieten.
     */
    @Test
    fun offersExactlyTheCompetitionsThePersonIsRegisteredFor() = testComprehension {
        val fixture = !MyEventFixture.create()

        val competitions =
            !ParticipantScanScopeRepo.getCompetitionsOfParticipant(fixture.eventId, fixture.participantId)

        assertEquals(2, competitions.size, "zwei Meldungen, zwei Wettkämpfe zur Auswahl")
        assertTrue(
            competitions.any { it.id == fixture.unscheduledCompetitionId },
            "auch der Wettkampf ohne gesetzten Lauf gehört dazu - gewogen wird trotzdem",
        )
    }

    /**
     * Ein Vereinsmitglied ohne eigene Meldung. Sein Band lässt sich scannen, aber eine
     * wettkampfbezogene Bedingung hat keinen Wettkampf, an dem sie hängen könnte - die App
     * bekommt eine leere Liste und sagt das, statt einen fremden Wettkampf anzubieten.
     */
    @Test
    fun offersNothingForSomebodyWithoutRegistration() = testComprehension {
        val fixture = !MyEventFixture.create()

        val competitions =
            !ParticipantScanScopeRepo.getCompetitionsOfParticipant(fixture.eventId, fixture.reserveId)

        assertEquals(emptyList(), competitions)
    }
}
