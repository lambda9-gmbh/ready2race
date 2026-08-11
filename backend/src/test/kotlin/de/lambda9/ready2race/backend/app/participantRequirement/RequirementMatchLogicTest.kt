package de.lambda9.ready2race.backend.app.participantRequirement

import de.lambda9.ready2race.backend.app.participantRequirement.boundary.RequirementMatchLogic
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequirementMatchLogicTest {

    private fun matches(
        listFirstname: String? = "Leander",
        listLastname: String? = "Spalek",
        listYear: Int? = 2002,
        listClub: String? = null,
        registeredFirstname: String = "Leander",
        registeredLastname: String = "Spalek",
        registeredYear: Int? = 2002,
        registeredClub: String? = "Frankfurter RG Germania 1869 e.V.",
        namedParticipantId: UUID? = null,
        registeredRoles: List<UUID>? = null,
    ) = RequirementMatchLogic.matches(
        listFirstname = listFirstname,
        listLastname = listLastname,
        listYear = listYear,
        listClub = listClub,
        registeredFirstname = registeredFirstname,
        registeredLastname = registeredLastname,
        registeredYear = registeredYear,
        registeredClub = registeredClub,
        namedParticipantId = namedParticipantId,
        registeredRoles = registeredRoles,
    )

    @Test
    fun plainNamesMatch() {
        assertTrue(matches())
    }

    @Test
    fun caseIsIgnored() {
        assertTrue(matches(listFirstname = "LEANDER", listLastname = "spalek"))
    }

    @Test
    fun surroundingWhitespaceInTheRegistrationIsIgnored() {
        // Der Fund vom 09.08.2026: 33 der 189 Gemeldeten der Coastal-Regatta tragen
        // Leerzeichen am Namensrand ("Leander ", "Amelie Katharina ", " Herrmann "), weil sie
        // so gemeldet wurden. Verglichen wurde bisher ohne trim - dadurch fielen 16 Personen
        // aus dem Abgleich mit der DRV-Aktivenpassliste heraus.
        assertTrue(matches(registeredFirstname = "Leander "))
        assertTrue(matches(registeredLastname = " Spalek "))
        assertTrue(matches(registeredFirstname = " Leander ", registeredLastname = " Spalek "))
    }

    @Test
    fun surroundingWhitespaceInTheUploadedListIsIgnored() {
        assertTrue(matches(listFirstname = "Leander "))
        assertTrue(matches(listLastname = " Spalek"))
    }

    @Test
    fun whitespaceInsideTheNameStillCounts() {
        // Nur der Rand wird getrimmt - "Amelie Katharina" und "AmelieKatharina" bleiben
        // verschiedene Personen.
        assertFalse(matches(listFirstname = "AmelieKatharina", registeredFirstname = "Amelie Katharina"))
    }

    @Test
    fun differentNamesDoNotMatch() {
        assertFalse(matches(listLastname = "Spalekowski"))
    }

    @Test
    fun clubIsOnlyCheckedWhenTheListCarriesOne() {
        assertTrue(matches(listClub = null, registeredClub = "Irgendein anderer Verein"))
        assertTrue(matches(listClub = " Frankfurter RG Germania 1869 e.V. "))
        assertFalse(matches(listClub = "Ruderklub Flensburg e.V."))
    }

    @Test
    fun yearIsOnlyCheckedWhenTheListCarriesOne() {
        assertTrue(matches(listYear = null, registeredYear = 1999))
        assertFalse(matches(listYear = 2002, registeredYear = 2004))
    }

    @Test
    fun severalAcceptedValuesAreAllowed() {
        // Beim DRV heißt startberechtigt entweder "ja" oder "erweitert" - beides zählt.
        val akzeptiert = listOf("ja", "erweitert")

        assertTrue(RequirementMatchLogic.isAccepted("ja", akzeptiert))
        assertTrue(RequirementMatchLogic.isAccepted("erweitert", akzeptiert))
        assertFalse(RequirementMatchLogic.isAccepted("nein", akzeptiert))
    }

    @Test
    fun acceptedValuesIgnoreCaseAndSurroundingWhitespace() {
        val akzeptiert = listOf(" Ja ", "erweitert")

        assertTrue(RequirementMatchLogic.isAccepted("JA", akzeptiert))
        assertTrue(RequirementMatchLogic.isAccepted(" ja", akzeptiert))
        assertTrue(RequirementMatchLogic.isAccepted("Erweitert ", akzeptiert))
    }

    @Test
    fun noSelectionMeansEveryRowCounts() {
        // Nichts ausgewählt verhält sich wie "Spalte gar nicht zugeordnet". So kann ein Import
        // nicht stillschweigend auf null Treffer laufen, nur weil die Auswahl vergessen wurde.
        assertTrue(RequirementMatchLogic.isAccepted("nein", null))
        assertTrue(RequirementMatchLogic.isAccepted("nein", emptyList()))
        assertTrue(RequirementMatchLogic.isAccepted(null, null))
    }

    @Test
    fun anEmptyCellIsNotAccepted() {
        // Steht in der Spalte nichts, ist die Bedingung nicht belegt.
        assertFalse(RequirementMatchLogic.isAccepted(null, listOf("ja")))
        assertFalse(RequirementMatchLogic.isAccepted("", listOf("ja")))
        assertFalse(RequirementMatchLogic.isAccepted("   ", listOf("ja")))
    }

    @Test
    fun aRoleBoundRequirementOnlyHitsThatRole() {
        val steuerleute = UUID.randomUUID()
        val senior = UUID.randomUUID()

        assertTrue(matches(namedParticipantId = steuerleute, registeredRoles = listOf(steuerleute)))
        assertFalse(matches(namedParticipantId = steuerleute, registeredRoles = listOf(senior)))
        assertFalse(matches(namedParticipantId = steuerleute, registeredRoles = null))
        assertTrue(matches(namedParticipantId = null, registeredRoles = listOf(senior)))
    }
}
