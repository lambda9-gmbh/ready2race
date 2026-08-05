package de.lambda9.ready2race.backend.app.certificate

import de.lambda9.ready2race.backend.app.certificate.boundary.AwardCertificateLogic
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateMode
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateOptions
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateParticipant
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateTeam
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AwardCertificateLogicTest {

    private fun participant(firstName: String, lastName: String, role: String = "Ruderer") =
        AwardCertificateParticipant(firstName = firstName, lastName = lastName, role = role)

    private fun team(
        place: Int,
        clubName: String = "RC Allemannia Hamburg v. 1866",
        teamName: String? = null,
        result: String? = "33:17,7 min",
        startNumber: Int = place,
        excluded: Boolean = false,
        participants: List<AwardCertificateParticipant> = listOf(participant("Carina", "Hein")),
    ) = AwardCertificateTeam(
        place = place,
        clubName = clubName,
        teamName = teamName,
        result = result,
        startNumber = startNumber,
        excluded = excluded,
        participants = participants,
        registrationId = UUID.randomUUID(),
    )

    private fun options(
        maxPlace: Int = 3,
        mode: AwardCertificateMode = AwardCertificateMode.PER_ATHLETE,
    ) = AwardCertificateOptions(maxPlace = maxPlace, mode = mode, withBackground = false)

    private fun entries(
        teams: List<AwardCertificateTeam>,
        options: AwardCertificateOptions = options(),
    ) = AwardCertificateLogic.entriesForCompetition(
        competitionIdentifier = "1",
        competitionName = "CF 1x Frauen-Einer",
        competitionShortName = "CF 1x",
        teams = teams,
        mode = options.mode,
        maxPlace = options.maxPlace,
    )

    @Test
    fun placesBeyondTheLimitAreDropped() {
        val result = entries(listOf(team(1), team(2), team(3), team(4)))
        assertEquals(listOf(1, 2, 3), result.map { it.place })
    }

    @Test
    fun allPlacesArePossible() {
        val result = entries(listOf(team(1), team(2), team(3), team(4)), options(maxPlace = 99))
        assertEquals(listOf(1, 2, 3, 4), result.map { it.place })
    }

    @Test
    fun excludedTeamsGetNoCertificate() {
        val result = entries(listOf(team(1), team(2, excluded = true), team(3)))
        assertEquals(listOf(1, 3), result.map { it.place })
    }

    @Test
    fun perAthleteYieldsOneEntryPerParticipant() {
        val result = entries(
            listOf(
                team(
                    place = 1,
                    participants = listOf(
                        participant("Carina", "Hein"),
                        participant("Malte", "Hein"),
                        participant("Jonas", "Meier", role = "Steuermann"),
                    ),
                )
            )
        )

        assertEquals(3, result.size)
        assertEquals(listOf("Carina Hein"), result[0].names)
        assertEquals(listOf("Jonas Meier"), result[2].names)
        assertTrue(result.all { it.place == 1 })
    }

    @Test
    fun perTeamYieldsOneEntryWithAllNames() {
        val result = entries(
            listOf(
                team(
                    place = 1,
                    participants = listOf(participant("Carina", "Hein"), participant("Malte", "Hein")),
                )
            ),
            options(mode = AwardCertificateMode.PER_TEAM),
        )

        assertEquals(1, result.size)
        assertEquals(listOf("Carina Hein", "Malte Hein"), result.single().names)
    }

    /**
     * Finding 1: Der Vor- und Nachname müssen unverändert aus den Stammdaten übernommen werden,
     * statt aus dem zusammengesetzten Namen an der ersten Leerstelle gesplittet zu werden — sonst
     * würde bei mehrteiligen Vornamen wie „Anna Maria" der Nachname fälschlich „Maria Müller".
     */
    @Test
    fun perAthleteExposesStructuredFirstAndLastName() {
        val result = entries(
            listOf(
                team(
                    place = 1,
                    participants = listOf(participant("Anna Maria", "Müller")),
                )
            )
        )

        assertEquals("Anna Maria", result.single().firstName)
        assertEquals("Müller", result.single().lastName)
    }

    /**
     * Im PER_TEAM-Modus teilen sich mehrere Personen eine Urkunde, ein einzelner Vor-/Nachname
     * ergibt daher keinen Sinn und muss null bleiben; `names` trägt weiterhin die volle Liste.
     */
    @Test
    fun perTeamLeavesFirstAndLastNameNull() {
        val result = entries(
            listOf(
                team(
                    place = 1,
                    participants = listOf(participant("Carina", "Hein"), participant("Malte", "Hein")),
                )
            ),
            options(mode = AwardCertificateMode.PER_TEAM),
        )

        assertNull(result.single().firstName)
        assertNull(result.single().lastName)
    }

    /**
     * Finding 5: Der Einzeldownload einer Urkunde (Nachdruck/Korrektur) darf nicht an der
     * Platzgrenze scheitern. `maxPlace = null` steht dafür statt eines Sentinel-Werts.
     */
    @Test
    fun nullMaxPlaceKeepsAllPlaces() {
        val unlimited = AwardCertificateLogic.entriesForCompetition(
            competitionIdentifier = "1",
            competitionName = "CF 1x Frauen-Einer",
            competitionShortName = "CF 1x",
            teams = listOf(team(1), team(5), team(12)),
            mode = AwardCertificateMode.PER_ATHLETE,
            maxPlace = null,
        )
        // Ohne Platzgrenze (wie beim Einzeldownload) bleiben alle Plätze erhalten, auch jenseits
        // der sonst üblichen Grenze von 3.
        assertEquals(listOf(1, 5, 12), unlimited.map { it.place })
    }

    @Test
    fun entriesAreSortedByPlaceThenStartNumber() {
        val result = entries(
            listOf(
                team(place = 2, startNumber = 7, clubName = "Startnummer 7"),
                team(place = 1, startNumber = 4, clubName = "Startnummer 4"),
                team(place = 2, startNumber = 3, clubName = "Startnummer 3"),
            )
        )

        assertEquals(listOf(1, 2, 2), result.map { it.place })
        assertEquals(
            listOf("Startnummer 4", "Startnummer 3", "Startnummer 7"),
            result.map { it.clubName },
        )
    }

    @Test
    fun competitionDataIsCarriedOver() {
        val result = entries(listOf(team(1, teamName = "Flensburg I"))).single()
        assertEquals("1", result.competitionIdentifier)
        assertEquals("CF 1x", result.competitionShortName)
        assertEquals("Flensburg I", result.teamName)
        assertEquals("33:17,7 min", result.result)
        assertEquals("RC Allemannia Hamburg v. 1866", result.clubName)
    }

    @Test
    fun missingResultStaysNull() {
        val result = entries(listOf(team(1, result = null))).single()
        assertEquals(null, result.result)
    }

    @Test
    fun placeIsFormattedGerman() {
        assertEquals("1. Platz", AwardCertificateLogic.formatPlace(1))
        assertEquals("12. Platz", AwardCertificateLogic.formatPlace(12))
    }

    @Test
    fun eventDateIsFormattedAsRange() {
        assertEquals(
            "16.–17. August 2025",
            AwardCertificateLogic.formatEventDate(
                listOf(LocalDate.of(2025, 8, 16), LocalDate.of(2025, 8, 17))
            ),
        )
    }

    @Test
    fun singleEventDayHasNoRange() {
        assertEquals(
            "16. August 2025",
            AwardCertificateLogic.formatEventDate(listOf(LocalDate.of(2025, 8, 16))),
        )
    }

    @Test
    fun eventDateAcrossMonthsSpellsBothMonths() {
        assertEquals(
            "31. Juli – 1. August 2025",
            AwardCertificateLogic.formatEventDate(
                listOf(LocalDate.of(2025, 7, 31), LocalDate.of(2025, 8, 1))
            ),
        )
    }

    @Test
    fun noEventDaysYieldsEmptyString() {
        assertEquals("", AwardCertificateLogic.formatEventDate(emptyList()))
    }

    /**
     * Finding 3: Die Zuordnung von einem AwardCertificateEntry auf GapPlaceholderValues war bisher
     * nur inline in der datenbankabhängigen `render`-Funktion vorhanden und damit ungetestet — was
     * finding 1 (falsch gesplittete Namen) erst ermöglicht hat. Jetzt als reine Funktion getestet,
     * inklusive des mehrteiligen Vornamens aus finding 1.
     */
    @Test
    fun placeholderValuesCarriesStructuredNameForPerAthlete() {
        val entry = entries(
            listOf(team(place = 1, participants = listOf(participant("Anna Maria", "Müller"))))
        ).single()

        val values = AwardCertificateLogic.placeholderValues(
            entry = entry,
            eventName = "Deutsche Meisterschaften",
            eventLocation = "Hamburg",
            eventDate = "16. August 2025",
        )

        assertEquals("Anna Maria", values.firstName)
        assertEquals("Müller", values.lastName)
        assertEquals("Anna Maria Müller", values.fullName)
        assertEquals("1. Platz", values.place)
        assertEquals("Deutsche Meisterschaften", values.eventName)
        assertEquals("Hamburg", values.eventLocation)
        assertEquals("16. August 2025", values.eventDate)
    }

    @Test
    fun placeholderValuesLeavesNamesNullForPerTeamAndJoinsFullName() {
        val entry = entries(
            listOf(
                team(
                    place = 1,
                    participants = listOf(participant("Carina", "Hein"), participant("Malte", "Hein")),
                )
            ),
            options(mode = AwardCertificateMode.PER_TEAM),
        ).single()

        val values = AwardCertificateLogic.placeholderValues(
            entry = entry,
            eventName = "Deutsche Meisterschaften",
            eventLocation = null,
            eventDate = "16. August 2025",
        )

        assertNull(values.firstName)
        assertNull(values.lastName)
        assertEquals("Carina Hein\nMalte Hein", values.fullName)
        assertNull(values.eventLocation)
    }
}
