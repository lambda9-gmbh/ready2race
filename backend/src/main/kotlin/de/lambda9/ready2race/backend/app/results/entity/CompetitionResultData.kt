package de.lambda9.ready2race.backend.app.results.entity

import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDayRecord
import java.time.LocalDate

data class EventResultData(
    val name: String,
    val competitions: List<CompetitionResultData>,
    val eventDays: Pair<LocalDate, LocalDate>?,
) {

    data class CompetitionResultData(
        val identifier: String,
        val name: String,
        val shortName: String?,
        val days: List<EventDayRecord>,
        /**
         * Ein Abschnitt je Wertungskategorie, in der konfigurierten Reihenfolge. Ein Wettkampf
         * ohne Wertungskategorien hat genau einen Abschnitt ohne Namen — die Ausgabe sieht dann
         * aus wie die frühere gemeinsame Rangliste.
         */
        val categories: List<RatingCategoryResultData>,
    )

    /** [name] ist null für den Abschnitt „Ohne Wertungskategorie". */
    data class RatingCategoryResultData(
        val name: String?,
        val teams: List<TeamResultData>,
    )

    data class TeamResultData(
        /**
         * Der Platz **innerhalb** der Wertungskategorie, ab 1. Null für ein abgemeldetes,
         * ausgeschiedenes oder disqualifiziertes Boot: es steht am Ende seines Abschnitts, aber
         * ohne Platz.
         */
        val place: Int?,
        val clubName: String,
        val teamName: String?,
        val participatingClubName: String?,
        val participants: List<ParticipantResultData>,
        val sortedSubstitutions: List<SubstitutionResultData>
    )

    data class ParticipantResultData(
        val role: String,
        val firstname: String,
        val lastname: String,
        val year: Int,
        val gender: Gender,
        val externalClubName: String?,
    )

    sealed interface SubstitutionResultData {

        data class RoleSwap(
            val left: ParticipantResultData,
            val right: ParticipantResultData,
            val round: String,
        ) : SubstitutionResultData

        data class ParticipantSwap(
            val subIn: ParticipantResultData,
            val subOut: ParticipantResultData,
            val round: String,
        ) : SubstitutionResultData

    }
}
