package de.lambda9.ready2race.backend.app.timingConfig.control

import de.lambda9.ready2race.backend.app.timingConfig.entity.CompetitionTimingDeviationDto
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingSystem
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.RACECLOCKER_RACE
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.UUID

object TimingConfigRepo {

    /**
     * Die Wettkämpfe einer Veranstaltung, die mindestens eines der sechs vererbbaren Zeitnahme-Felder
     * selbst gesetzt haben. Alles null heißt „erbt" und taucht hier nicht auf.
     *
     * Nur `competition_properties` mit einem echten Wettkampf dahinter (`competition is not null`):
     * dieselbe Tabelle trägt laut Check-Constraint auch die Zeilen der Wettkampf-Vorlagen, und die
     * gehören zu keiner Veranstaltung.
     */
    fun getDeviations(eventId: UUID) = Jooq.query {
        // Zwei Aliase derselben Tabelle, damit beide Anwahlen ihren Namen mitbringen. Ohne den
        // Namen könnte diese Liste nur „hat ein eigenes Rennen" sagen -- und genau das half beim
        // Suchen nie weiter.
        val qualiRace = RACECLOCKER_RACE.`as`("quali_race")
        val roundsRace = RACECLOCKER_RACE.`as`("rounds_race")

        select(
            COMPETITION.ID,
            COMPETITION_PROPERTIES.IDENTIFIER,
            COMPETITION_PROPERTIES.NAME,
            COMPETITION.TIMING_SYSTEM,
            qualiRace.NAME,
            roundsRace.NAME,
            COMPETITION.STARTLIST_CONFIG_QUALIFICATION,
            COMPETITION.STARTLIST_CONFIG_ROUNDS,
            COMPETITION.RESULT_IMPORT_CONFIG,
        )
            .from(COMPETITION)
            .join(COMPETITION_PROPERTIES).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(qualiRace).on(qualiRace.ID.eq(COMPETITION.RACECLOCKER_RACE_QUALIFICATION))
            .leftJoin(roundsRace).on(roundsRace.ID.eq(COMPETITION.RACECLOCKER_RACE_ROUNDS))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(
                DSL.or(
                    COMPETITION.TIMING_SYSTEM.isNotNull,
                    COMPETITION.RACECLOCKER_RACE_QUALIFICATION.isNotNull,
                    COMPETITION.RACECLOCKER_RACE_ROUNDS.isNotNull,
                    COMPETITION.STARTLIST_CONFIG_QUALIFICATION.isNotNull,
                    COMPETITION.STARTLIST_CONFIG_ROUNDS.isNotNull,
                    COMPETITION.RESULT_IMPORT_CONFIG.isNotNull,
                )
            )
            .orderBy(COMPETITION_PROPERTIES.IDENTIFIER)
            .fetch {
                CompetitionTimingDeviationDto(
                    // Not null in the schema; the projection just loses that guarantee.
                    competitionId = it[COMPETITION.ID]!!,
                    identifier = it[COMPETITION_PROPERTIES.IDENTIFIER]!!,
                    name = it[COMPETITION_PROPERTIES.NAME]!!,
                    timingSystem = it[COMPETITION.TIMING_SYSTEM]?.let { s -> TimingSystem.valueOf(s) },
                    raceQualificationName = it[qualiRace.NAME],
                    raceRoundsName = it[roundsRace.NAME],
                    startlistConfigQualification = it[COMPETITION.STARTLIST_CONFIG_QUALIFICATION],
                    startlistConfigRounds = it[COMPETITION.STARTLIST_CONFIG_ROUNDS],
                    resultImportConfig = it[COMPETITION.RESULT_IMPORT_CONFIG],
                )
            }
    }
}
