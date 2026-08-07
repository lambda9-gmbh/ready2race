package de.lambda9.ready2race.backend.app.liveDashboard.control

import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionCheckSeverityRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_CHECK_SEVERITY
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.tailwind.jooq.Jooq
import java.util.UUID

object CheckSeverityRepo {

    /** Alle abweichenden Schweregrade der Wettkämpfe einer Veranstaltung. */
    fun getByEvent(eventId: UUID) = Jooq.query {
        select(
            COMPETITION_CHECK_SEVERITY.COMPETITION,
            COMPETITION_CHECK_SEVERITY.CHECK_TYPE,
            COMPETITION_CHECK_SEVERITY.PARTICIPANT_REQUIREMENT,
            COMPETITION_CHECK_SEVERITY.SEVERITY,
        )
            .from(COMPETITION_CHECK_SEVERITY)
            .join(COMPETITION).on(COMPETITION_CHECK_SEVERITY.COMPETITION.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .fetch()
    }

    /** Wettkämpfe der Veranstaltung samt An-/Abmelde-Flag, sortiert wie in der Wettkampfliste. */
    fun getCompetitions(eventId: UUID) = Jooq.query {
        select(
            COMPETITION.ID,
            COMPETITION_PROPERTIES.IDENTIFIER,
            COMPETITION_PROPERTIES.NAME,
            COMPETITION_PROPERTIES.CHECK_IN_OUT_REQUIRED,
        )
            .from(COMPETITION)
            .join(COMPETITION_PROPERTIES).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .orderBy(COMPETITION_PROPERTIES.IDENTIFIER.asc())
            .fetch()
    }

    /**
     * Ersetzt die Abweichungen aller Wettkämpfe einer Veranstaltung in einem Zug. Standardwerte
     * kommen als Löschung an, nicht als Zeile - so bleibt die Tabelle dünn und ein später
     * geänderter Standard wirkt auch auf Bestandsdaten.
     *
     * Löschen und Einfügen laufen als zwei Anweisungen in genau einem `Jooq.query`-Block (dasselbe
     * Muster wie z.B. `SequenceRepo.addMissing`). Die eigentliche Transaktionsklammer zieht ohnehin
     * `respondKIO` (`app.transact()`) über die gesamte Anfrage, `Jooq.query` selbst öffnet keine
     * eigene Transaktion - ein Löschen, das committet, während das Einfügen scheitert, ist dadurch
     * ausgeschlossen. Für den Mehrzeilen-Insert gilt derselbe Aufbau wie die Extension
     * `TableImpl<R>.insert(records)` in Extensions.kt, hier nur inline, weil er mit dem Löschen im
     * selben Block stehen soll.
     */
    fun replaceForEvent(
        eventId: UUID,
        records: Collection<CompetitionCheckSeverityRecord>,
    ) = Jooq.query {
        deleteFrom(COMPETITION_CHECK_SEVERITY)
            .where(
                COMPETITION_CHECK_SEVERITY.COMPETITION.`in`(
                    select(COMPETITION.ID).from(COMPETITION).where(COMPETITION.EVENT.eq(eventId))
                )
            )
            .execute()

        // Eine leere Menge ist der reguläre "auf Standard zurücksetzen"-Fall, kein Sonderfall: wer
        // alle Prüfungen einer Veranstaltung auf den Standard zurücksetzt, schickt lauter Zeilen,
        // die dem Standard entsprechen und deshalb vorher herausgefiltert wurden (siehe
        // LiveDashboardService.updateCheckSeverityConfig). Ohne diese Prüfung würde jOOQ aus
        // `.set(emptyList())` ein `insert into ... default values` bauen, das an den NOT-NULL-
        // Spalten der Tabelle scheitert.
        if (records.isNotEmpty()) {
            insertInto(COMPETITION_CHECK_SEVERITY)
                .set(records)
                .execute()
        }
    }
}
