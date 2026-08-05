package de.lambda9.ready2race.backend.app.eventSchedule.control

import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.records.EventScheduleSlotRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.traverse
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import org.jooq.impl.DSL.selectOne
import java.time.LocalDateTime
import java.util.UUID

object EventScheduleRepo {

    /**
     * Alle Slots des Events mit dem Kontext für die Zustandsableitung. "Runde materialisiert" =
     * mindestens eine Setup-Zeile derselben Runde hat einen Lauf. Dafür braucht die korrelierte
     * Subquery eigene Aliase für competition_setup_match/competition_match, weil beide Tabellen
     * im äußeren Query bereits unaliast verjoint sind.
     *
     * [setupRoundId], wenn gesetzt, schränkt auf die Slots genau dieser Setup-Runde ein - Grundlage
     * für "ganze Runde überspringen" (siehe EventScheduleService.setRoundSkipped), das dieselbe
     * Zustandsableitung braucht wie der Einzel-Slot-Skip, nur für mehrere Zeilen auf einmal.
     */
    fun getSlots(eventId: UUID, setupRoundId: UUID? = null) = Jooq.query {
        val sibling = COMPETITION_SETUP_MATCH.`as`("sibling")
        val siblingMatch = COMPETITION_MATCH.`as`("sibling_match")

        val roundMaterialized = DSL.field(
            DSL.exists(
                selectOne()
                    .from(sibling)
                    .join(siblingMatch)
                    .on(siblingMatch.COMPETITION_SETUP_MATCH.eq(sibling.ID))
                    .where(sibling.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND))
            )
        ).`as`("round_materialized")

        var condition = EVENT_SCHEDULE_SLOT.EVENT.eq(eventId)
        if (setupRoundId != null) {
            condition = condition.and(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(setupRoundId))
        }

        select(
            EVENT_SCHEDULE_SLOT.asterisk(),
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_PROPERTIES.NAME.`as`("competition_name"),
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
            COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.`as`("setup_round_id"),
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH.isNotNull.`as`("match_exists"),
            COMPETITION_MATCH.STARTED_AT.`as`("match_started_at"),
            COMPETITION_MATCH.FINISHED_AT.`as`("match_finished_at"),
            COMPETITION_MATCH.CURRENTLY_RUNNING,
            roundMaterialized,
        )
            .from(EVENT_SCHEDULE_SLOT)
            .leftJoin(COMPETITION_SETUP_MATCH)
            .on(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .leftJoin(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .leftJoin(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .leftJoin(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH))
            .where(condition)
            .orderBy(
                EVENT_SCHEDULE_SLOT.START_TIME.asc(),
                COMPETITION_SETUP_MATCH.EXECUTION_ORDER.asc(),
                EVENT_SCHEDULE_SLOT.ID.asc(),
            )
            .fetch()
    }

    /**
     * Slots ab (einschließlich) [after] für die Aktivierungskette (Task 9). Gleiche Joins/Aliase wie
     * [getSlots], zusätzlich `match_open` — mindestens eine Mannschaft ohne Ergebnis, wortgleiches
     * Prädikat zu `LiveDashboardRepo.getActivationCandidates` — und `CURRENTLY_RUNNING`, damit
     * [ScheduleChain.decideNext] einen noch laufenden Sibling-Lauf derselben Startzeit von einem
     * frisch aktivierbaren unterscheiden kann.
     *
     * Bewusst `>=` statt `>`: [after] ist die Startzeit des gerade beendeten Slots selbst, und dessen
     * Gruppe (parallele Starts derselben Startzeit) muss mit in den Walk - sonst würde ein noch
     * offener Parallel-Lauf derselben Gruppe stillschweigend übergangen und die Kette bereits auf die
     * nächste Gruppe vorrücken, obwohl die aktuelle noch nicht fertig ist (siehe ScheduleChainTest).
     */
    fun getChainSlots(eventId: UUID, after: LocalDateTime) = Jooq.query {
        val sibling = COMPETITION_SETUP_MATCH.`as`("sibling")
        val siblingMatch = COMPETITION_MATCH.`as`("sibling_match")

        val roundMaterialized = DSL.field(
            DSL.exists(
                selectOne()
                    .from(sibling)
                    .join(siblingMatch)
                    .on(siblingMatch.COMPETITION_SETUP_MATCH.eq(sibling.ID))
                    .where(sibling.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND))
            )
        ).`as`("round_materialized")

        val matchOpen = DSL.field(
            DSL.exists(
                selectOne()
                    .from(COMPETITION_MATCH_TEAM)
                    .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH))
                    .and(COMPETITION_MATCH_TEAM.OUT.isTrue.not())
                    .and(COMPETITION_MATCH_TEAM.PLACE.isNull)
                    .and(COMPETITION_MATCH_TEAM.FAILED.isTrue.not())
                    .andNotExists(
                        selectOne()
                            .from(COMPETITION_DEREGISTRATION)
                            .where(COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.eq(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION))
                            .and(COMPETITION_DEREGISTRATION.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND))
                    )
            )
        ).`as`("match_open")

        select(
            EVENT_SCHEDULE_SLOT.asterisk(),
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH.isNotNull.`as`("match_exists"),
            COMPETITION_MATCH.FINISHED_AT.`as`("match_finished_at"),
            COMPETITION_MATCH.CURRENTLY_RUNNING,
            roundMaterialized,
            matchOpen,
        )
            .from(EVENT_SCHEDULE_SLOT)
            .leftJoin(COMPETITION_SETUP_MATCH)
            .on(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .leftJoin(COMPETITION_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH))
            .where(EVENT_SCHEDULE_SLOT.EVENT.eq(eventId))
            .and(EVENT_SCHEDULE_SLOT.START_TIME.ge(after))
            .orderBy(
                EVENT_SCHEDULE_SLOT.START_TIME.asc(),
                COMPETITION_SETUP_MATCH.EXECUTION_ORDER.asc(),
                EVENT_SCHEDULE_SLOT.ID.asc(),
            )
            .fetch()
    }

    /** Die Startzeit des Slots, der auf diese Setup-Zeile zeigt — null, wenn keiner existiert. */
    fun getSlotBySetupMatch(setupMatchId: UUID) = Jooq.query {
        select(EVENT_SCHEDULE_SLOT.START_TIME)
            .from(EVENT_SCHEDULE_SLOT)
            .where(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH.eq(setupMatchId))
            .fetchOne(EVENT_SCHEDULE_SLOT.START_TIME)
    }

    /** Ob irgendein Lauf des Events gerade läuft — Gate für [de.lambda9.ready2race.backend.app.eventSchedule.boundary.ScheduleChainService.resumeIfParked]. */
    fun hasRunningMatch(eventId: UUID) = Jooq.query {
        fetchExists(
            COMPETITION_MATCH
                .join(COMPETITION_SETUP_MATCH)
                .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
                .join(COMPETITION_SETUP_ROUND)
                .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
                .join(COMPETITION_PROPERTIES)
                .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
                .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
                .where(
                    COMPETITION.EVENT.eq(eventId)
                        .and(COMPETITION_MATCH.CURRENTLY_RUNNING.isTrue)
                )
        )
    }

    /**
     * Größte Startzeit unter allen Slots des Events, deren verknüpfter Lauf bereits beendet ist —
     * Referenzpunkt für den zweiten Auslöser der Kette nach Rundenerzeugung. Bewusst `max(start_time)`
     * statt "Slot des zuletzt (nach finished_at) beendeten Laufs": Läufe werden nicht zwingend in
     * Startzeit-Reihenfolge beendet, `max(start_time)` liefert dagegen eine monotone Front, die nie
     * zurückspringt. null, wenn im Zeitstrahl des Events noch nichts beendet ist.
     */
    fun getLastFinishedSlotTime(eventId: UUID) = Jooq.query {
        val maxStartTime = DSL.max(EVENT_SCHEDULE_SLOT.START_TIME)
        select(maxStartTime)
            .from(EVENT_SCHEDULE_SLOT)
            .join(COMPETITION_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH))
            .where(EVENT_SCHEDULE_SLOT.EVENT.eq(eventId))
            .and(COMPETITION_MATCH.FINISHED_AT.isNotNull)
            .fetchOne(maxStartTime)
    }

    /**
     * Ein einzelner Slot mit demselben Kontext wie [getSlots] (Match-Status, "Runde
     * materialisiert") — für die Zustandsableitung vor Skip/Unskip. Gleiche Joins wie dort, nur
     * auf einen Slot gefiltert statt auf das ganze Event.
     */
    fun getSlotWithContext(eventId: UUID, slotId: UUID) = Jooq.query {
        val sibling = COMPETITION_SETUP_MATCH.`as`("sibling")
        val siblingMatch = COMPETITION_MATCH.`as`("sibling_match")

        val roundMaterialized = DSL.field(
            DSL.exists(
                selectOne()
                    .from(sibling)
                    .join(siblingMatch)
                    .on(siblingMatch.COMPETITION_SETUP_MATCH.eq(sibling.ID))
                    .where(sibling.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND))
            )
        ).`as`("round_materialized")

        // Dieselben Aliasse wie in getSlots - die Aufrufer lesen die Zeile mit denselben Namen.
        select(
            EVENT_SCHEDULE_SLOT.asterisk(),
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH.isNotNull.`as`("match_exists"),
            COMPETITION_MATCH.STARTED_AT.`as`("match_started_at"),
            COMPETITION_MATCH.FINISHED_AT.`as`("match_finished_at"),
            roundMaterialized,
        )
            .from(EVENT_SCHEDULE_SLOT)
            .leftJoin(COMPETITION_SETUP_MATCH)
            .on(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .leftJoin(COMPETITION_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH))
            .where(EVENT_SCHEDULE_SLOT.EVENT.eq(eventId).and(EVENT_SCHEDULE_SLOT.ID.eq(slotId)))
            .fetchOne()
    }

    /** Setup-Zeilen des Events ohne Slot — die "nicht verplant"-Liste. */
    fun getUnplannedSetupMatches(eventId: UUID) = Jooq.query {
        select(
            COMPETITION_SETUP_MATCH.ID,
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_PROPERTIES.NAME.`as`("competition_name"),
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
        )
            .from(COMPETITION_SETUP_MATCH)
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .andNotExists(
                selectOne()
                    .from(EVENT_SCHEDULE_SLOT)
                    .where(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            )
            .orderBy(COMPETITION_PROPERTIES.NAME, COMPETITION_SETUP_MATCH.EXECUTION_ORDER)
            .fetch()
    }

    /**
     * Alle Setup-Zeilen des Events als Match-Kandidaten für den Excel-Import (Task 12) — gleiche
     * Joins wie [getUnplannedSetupMatches], aber ohne den notExists-Filter: der Import muss auch
     * bereits verplante Zeilen kennen, um Duplikate zu erkennen. Zusätzlich SHORT_NAME/IDENTIFIER
     * für die Text-Kandidaten des Namens-Matchings.
     */
    fun getImportCandidates(eventId: UUID) = Jooq.query {
        select(
            COMPETITION_SETUP_MATCH.ID,
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_PROPERTIES.NAME.`as`("competition_name"),
            COMPETITION_PROPERTIES.SHORT_NAME,
            COMPETITION_PROPERTIES.IDENTIFIER,
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
        )
            .from(COMPETITION_SETUP_MATCH)
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .orderBy(COMPETITION_PROPERTIES.NAME, COMPETITION_SETUP_MATCH.EXECUTION_ORDER)
            .fetch()
    }

    /** Alle Slots des Events löschen — Vorstufe des Re-Imports (Task 12): der Import ersetzt den ganzen Zeitstrahl. */
    fun deleteAllSlots(eventId: UUID) = EVENT_SCHEDULE_SLOT.delete { EVENT.eq(eventId) }

    fun createSlot(record: EventScheduleSlotRecord) = EVENT_SCHEDULE_SLOT.insertReturning(record) { ID }

    fun createSlots(records: List<EventScheduleSlotRecord>) = EVENT_SCHEDULE_SLOT.insert(records)

    fun updateSlot(eventId: UUID, slotId: UUID, f: EventScheduleSlotRecord.() -> Unit) =
        EVENT_SCHEDULE_SLOT.update(f) { ID.eq(slotId).and(EVENT.eq(eventId)) }

    fun deleteSlot(eventId: UUID, slotId: UUID) =
        EVENT_SCHEDULE_SLOT.delete { ID.eq(slotId).and(EVENT.eq(eventId)) }

    /** Ob die Setup-Zeile überhaupt zu diesem Event gehört — gleicher Join wie [getUnplannedSetupMatches]. */
    fun setupMatchExistsForEvent(eventId: UUID, setupMatchId: UUID) = Jooq.query {
        fetchExists(
            COMPETITION_SETUP_MATCH
                .join(COMPETITION_SETUP_ROUND)
                .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
                .join(COMPETITION_PROPERTIES)
                .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
                .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
                .where(
                    COMPETITION_SETUP_MATCH.ID.eq(setupMatchId)
                        .and(COMPETITION.EVENT.eq(eventId))
                )
        )
    }

    /**
     * Explizite Prüfung statt dem DB-Unique-Fehler überlassen (siehe EventScheduleError.SetupMatchAlreadyPlanned).
     * [excludeSlotId] blendet den gerade bearbeiteten Slot aus, damit ein Update, das ihm die Setup-Zeile
     * belässt, die er ohnehin schon hält, nicht fälschlich als Konflikt zählt.
     */
    fun slotExistsForSetupMatch(setupMatchId: UUID, excludeSlotId: UUID? = null) =
        EVENT_SCHEDULE_SLOT.exists {
            var condition = COMPETITION_SETUP_MATCH.eq(setupMatchId)
            if (excludeSlotId != null) {
                condition = condition.and(ID.ne(excludeSlotId))
            }
            condition
        }

    /**
     * Write-Through: spiegelt die geplante Slot-Zeit auf competition_match.start_time. No-op,
     * wenn zu dieser Setup-Zeile noch kein Lauf existiert (competition_match wird erst bei
     * Rundenerzeugung materialisiert) — [update] fetcht die Zeile vorher und macht bei keinem
     * Treffer schlicht nichts.
     *
     * Beendete Läufe behalten ihre historische Startzeit: `.and(FINISHED_AT.isNull)` sorgt dafür,
     * dass ein nachträgliches Verschieben/Importieren/Anlegen im Zeitstrahl einen bereits
     * abgeschlossenen Lauf nicht mehr rückwirkend umdatiert - der Zeitstrahl ist ab dann nur noch
     * für die (noch offenen) Läufe maßgeblich, nicht mehr für die Historie.
     */
    fun stampMatchStartTime(setupMatchId: UUID, startTime: LocalDateTime, userId: UUID) =
        COMPETITION_MATCH.update(
            f = {
                this.startTime = startTime
                updatedAt = LocalDateTime.now()
                updatedBy = userId
            },
            condition = { COMPETITION_SETUP_MATCH.eq(setupMatchId).and(FINISHED_AT.isNull) }
        )

    /**
     * Zeitstrahl-Write-Through nach Rundenerzeugung (Task 9): stempelt für jede frisch erzeugte
     * Setup-Zeile, die einen Slot hat, dessen geplante Startzeit auf den neuen Lauf — simple
     * Wiederverwendung von [stampMatchStartTime] je id statt einer eigenen Update-Join-Query.
     * Setup-Zeilen ohne Slot (kein Zeitstrahl für dieses Event) bleiben unangetastet.
     */
    fun stampSlotTimesForSetupMatches(ids: List<UUID>, userId: UUID) = KIO.comprehension {
        !ids.traverse { id ->
            KIO.comprehension {
                val slotTime = !getSlotBySetupMatch(id)
                if (slotTime != null) {
                    !stampMatchStartTime(id, slotTime, userId)
                }
                KIO.unit
            }
        }
        KIO.unit
    }
}
