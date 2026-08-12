package de.lambda9.ready2race.backend.app.timingConfig.control

import de.lambda9.ready2race.backend.app.timingConfig.entity.CompetitionTimingDeviationDto
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingSystem
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.util.UUID

object TimingConfigRepo {

    /**
     * Die Wettkämpfe einer Veranstaltung, die System oder ein Dateiformat selbst gesetzt haben statt
     * es von der Veranstaltung zu erben. Alles null heißt „erbt" und taucht hier nicht auf. Die
     * Rennen-Zuordnung steht bewusst nicht mehr hier — sie wird pro Rennen zugewiesen und dort
     * angezeigt.
     *
     * Nur `competition_properties` mit einem echten Wettkampf dahinter (`competition is not null`):
     * dieselbe Tabelle trägt laut Check-Constraint auch die Zeilen der Wettkampf-Vorlagen, und die
     * gehören zu keiner Veranstaltung.
     */
    fun getDeviations(eventId: UUID) = Jooq.query {
        select(
            COMPETITION.ID,
            COMPETITION_PROPERTIES.IDENTIFIER,
            COMPETITION_PROPERTIES.NAME,
            COMPETITION.TIMING_SYSTEM,
            COMPETITION.STARTLIST_CONFIG,
            COMPETITION.RESULT_IMPORT_CONFIG,
        )
            .from(COMPETITION)
            .join(COMPETITION_PROPERTIES).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(
                DSL.or(
                    COMPETITION.TIMING_SYSTEM.isNotNull,
                    COMPETITION.STARTLIST_CONFIG.isNotNull,
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
                    startlistConfig = it[COMPETITION.STARTLIST_CONFIG],
                    resultImportConfig = it[COMPETITION.RESULT_IMPORT_CONFIG],
                )
            }
    }
}
