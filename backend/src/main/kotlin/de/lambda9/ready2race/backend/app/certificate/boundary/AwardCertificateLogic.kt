package de.lambda9.ready2race.backend.app.certificate.boundary

import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateEntry
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateMode
import de.lambda9.ready2race.backend.app.certificate.entity.AwardCertificateTeam
import de.lambda9.ready2race.backend.app.documentTemplate.entity.GapPlaceholderValues
import java.time.LocalDate

object AwardCertificateLogic {

    private val germanMonths = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember",
    )

    /**
     * @param maxPlace Platzgrenze, ab der keine Urkunde mehr erstellt wird. `null` bedeutet
     * unbegrenzt — für den Einzeldownload einer Urkunde, bei dem die Platzgrenze nicht greifen soll.
     */
    fun entriesForCompetition(
        competitionIdentifier: String,
        competitionName: String,
        competitionShortName: String?,
        teams: List<AwardCertificateTeam>,
        mode: AwardCertificateMode,
        maxPlace: Int?,
    ): List<AwardCertificateEntry> = teams
        .filter { !it.excluded && (maxPlace == null || it.place <= maxPlace) }
        .sortedWith(compareBy({ it.place }, { it.startNumber }))
        .flatMap { team ->
            // Im PER_TEAM-Modus teilen sich mehrere Personen eine Urkunde, deshalb bleiben
            // firstName/lastName dort null; names trägt weiterhin die vollständige Liste.
            val groups: List<Triple<List<String>, String?, String?>> = when (mode) {
                AwardCertificateMode.PER_ATHLETE -> team.participants.map {
                    Triple(listOf("${it.firstName} ${it.lastName}"), it.firstName, it.lastName)
                }

                AwardCertificateMode.PER_TEAM -> listOf(
                    Triple(team.participants.map { "${it.firstName} ${it.lastName}" }, null, null)
                )
            }

            groups.map { (names, firstName, lastName) ->
                AwardCertificateEntry(
                    place = team.place,
                    competitionIdentifier = competitionIdentifier,
                    competitionName = competitionName,
                    competitionShortName = competitionShortName,
                    clubName = team.clubName,
                    teamName = team.teamName,
                    result = team.result,
                    names = names,
                    firstName = firstName,
                    lastName = lastName,
                    registrationId = team.registrationId,
                )
            }
        }

    fun formatPlace(place: Int): String = "$place. Platz"

    /**
     * Baut die Platzhalterwerte für eine einzelne Urkundenseite aus [entry] und den Eckdaten der
     * Veranstaltung. Rein und ohne Datenbankzugriff, damit finding 1 (Namen) hier testbar bleibt.
     */
    fun placeholderValues(
        entry: AwardCertificateEntry,
        eventName: String,
        eventLocation: String?,
        eventDate: String,
    ): GapPlaceholderValues = GapPlaceholderValues(
        firstName = entry.firstName,
        lastName = entry.lastName,
        fullName = entry.names.joinToString("\n"),
        result = entry.result,
        eventName = eventName,
        place = formatPlace(entry.place),
        competitionName = entry.competitionName,
        competitionShortName = entry.competitionShortName,
        clubName = entry.clubName,
        teamName = entry.teamName,
        eventDate = eventDate,
        eventLocation = eventLocation,
    )

    /**
     * Renntage als Bereich, wie auf der DRV-Vorlage: „16.–17. August 2025", über Monatsgrenzen
     * hinweg „31. Juli – 1. August 2025".
     */
    fun formatEventDate(days: List<LocalDate>): String {
        if (days.isEmpty()) return ""

        val first = days.min()
        val last = days.max()
        val year = last.year

        return when {
            first == last -> "${first.dayOfMonth}. ${germanMonths[first.monthValue - 1]} $year"
            first.month == last.month && first.year == last.year ->
                "${first.dayOfMonth}.–${last.dayOfMonth}. ${germanMonths[first.monthValue - 1]} $year"
            else ->
                "${first.dayOfMonth}. ${germanMonths[first.monthValue - 1]} – " +
                    "${last.dayOfMonth}. ${germanMonths[last.monthValue - 1]} $year"
        }
    }
}
