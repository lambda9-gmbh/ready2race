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
     *
     * `match_teams_total`/`match_teams_scored`/`match_teams_raced`/`match_teams_deregistered`
     * tragen den Stand des verknüpften Laufs für den Status-Chip im Zeitplan bei. Alle sind 0, wo
     * kein Lauf hängt - dort fragt die Anzeige ohnehin erst `matchId` ab. Die Trennung zwischen
     * "erledigt" (scored) und "gefahren" (raced) ist der Kern: der Zustand hängt an der ersten
     * Zahl, die Ablesung "Teilweise gewertet" an der zweiten.
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

        // Mannschaften des verknüpften Laufs, die im Rennen sind: OUT-Zeilen sind Boote, die aus
        // der Vorrunde nicht weitergekommen sind und nur als Lücke mitgeführt werden. Sowohl das
        // Dashboard (LiveDashboardRepo) als auch die Durchführungsseite
        // (CompetitionExecutionService) blenden sie aus - der Zeitplan zählt deshalb genauso, sonst
        // stünde dort 2/6, wo die anderen Oberflächen 2/4 zeigen.
        val teamInMatch = COMPETITION_MATCH_TEAM.COMPETITION_MATCH
            .eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH)
            .and(COMPETITION_MATCH_TEAM.OUT.isTrue.not())

        val matchTeamsTotal = DSL.field(
            DSL.selectCount()
                .from(COMPETITION_MATCH_TEAM)
                .where(teamInMatch)
        ).`as`("match_teams_total")

        // Die Abmeldung dieser Mannschaft für die Runde dieses Slots - dreimal gebraucht, deshalb
        // einmal benannt.
        val teamDeregistered = DSL.exists(
            selectOne()
                .from(COMPETITION_DEREGISTRATION)
                .where(COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.eq(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION))
                .and(COMPETITION_DEREGISTRATION.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND))
        )

        // "Erledigt" ist wortgleich zu LiveDashboardLogic.teamIsSettled (Platz ODER ausgeschieden
        // ODER abgemeldet) und damit genau die Negation von `match_open` in [getChainSlots]: die
        // Frage, ob hier noch jemand auf ein Ergebnis wartet. Korrelierte Unterabfrage statt
        // zweitem Query: eine Abfrage bleibt eine Abfrage.
        val matchTeamsScored = DSL.field(
            DSL.selectCount()
                .from(COMPETITION_MATCH_TEAM)
                .where(teamInMatch)
                .and(
                    COMPETITION_MATCH_TEAM.PLACE.isNotNull
                        .or(COMPETITION_MATCH_TEAM.FAILED.isTrue)
                        .or(teamDeregistered)
                )
        ).`as`("match_teams_scored")

        // "Gefahren" ist LiveDashboardLogic.teamHasRaced: Platz ODER ausgeschieden, ohne die
        // Abgemeldeten. Nur diese Zahl trägt "Teilweise gewertet". Vorher las der Zeitplan dafür
        // `match_teams_scored`, und ein Lauf mit einer Abmeldung und vier offenen Booten stand als
        // "Teilweise gewertet 1/5" da - obwohl niemand gefahren war (Vorfall vom 14.08.2026).
        val matchTeamsRaced = DSL.field(
            DSL.selectCount()
                .from(COMPETITION_MATCH_TEAM)
                .where(teamInMatch)
                .and(COMPETITION_MATCH_TEAM.PLACE.isNotNull.or(COMPETITION_MATCH_TEAM.FAILED.isTrue))
                .andNot(teamDeregistered)
        ).`as`("match_teams_raced")

        val matchTeamsDeregistered = DSL.field(
            DSL.selectCount()
                .from(COMPETITION_MATCH_TEAM)
                .where(teamInMatch)
                .and(teamDeregistered)
        ).`as`("match_teams_deregistered")

        var condition = EVENT_SCHEDULE_SLOT.EVENT.eq(eventId)
        if (setupRoundId != null) {
            condition = condition.and(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(setupRoundId))
        }

        select(
            EVENT_SCHEDULE_SLOT.asterisk(),
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_PROPERTIES.NAME.`as`("competition_name"),
            COMPETITION_PROPERTIES.IDENTIFIER.`as`("competition_identifier"),
            COMPETITION_PROPERTIES.SHORT_NAME.`as`("competition_short_name"),
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            // Freilose zeigen ihren materialisierten Namen (V202608121300).
            DSL.coalesce(COMPETITION_MATCH.BYE_NAME, COMPETITION_SETUP_MATCH.NAME).`as`("match_name"),
            COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.`as`("setup_round_id"),
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH.isNotNull.`as`("match_exists"),
            COMPETITION_MATCH.STARTED_AT.`as`("match_started_at"),
            COMPETITION_MATCH.FINISHED_AT.`as`("match_finished_at"),
            COMPETITION_MATCH.ACTIVATED_AT,
            roundMaterialized,
            matchTeamsTotal,
            matchTeamsScored,
            matchTeamsRaced,
            matchTeamsDeregistered,
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
     * ALLE Slots der Veranstaltung für die Aktivierungskette (Task 9). Gleiche Joins/Aliase wie
     * [getSlots], zusätzlich `match_open` und `ACTIVATED_AT`, damit [ScheduleChain.decideNext]
     * einen bereits an den Start gerufenen Sibling-Lauf derselben Startzeit von einem frisch
     * aktivierbaren unterscheiden kann.
     *
     * `match_open` ist die Negation von "erledigt" (`LiveDashboardLogic.teamIsSettled`, in SQL
     * `match_teams_scored` in [getSlots]): mindestens eine Mannschaft, auf deren Ergebnis noch
     * jemand wartet. **Die Kette fragt bewusst nach "erledigt" und nicht nach "gefahren"** - sonst
     * bliebe sie an einem Lauf stehen, dessen Boote alle abgemeldet sind, und die Regatta käme
     * nicht weiter. Die Abmeldung ist damit für die Kette eine Aussage, für die Ablesung
     * "Teilweise gewertet" aber keine; siehe `LiveDashboardLogic.teamHasRaced`.
     *
     * Bewusst ohne Untergrenze: Bis zum 10.08.2026 stand hier die Startzeit des gerade beendeten
     * Slots (`>= after`), und alles davor war für die Kette unsichtbar. Ein Beenden am zweiten
     * Regattatag hat damit den offenen Rest des ersten Tages übersprungen. Welche Gruppe an der
     * Reihe ist, entscheidet jetzt allein [ScheduleChain.decideNext] — an einer Stelle, mit dem
     * ganzen Zeitplan vor sich. Ein Renntag hat einige hundert Slots; das ist eine Abfrage, die die
     * Kette ohnehin nur beim Beenden, beim Setzen einer Runde und beim Absagen stellt.
     */
    fun getChainSlots(eventId: UUID) = Jooq.query {
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
            COMPETITION_MATCH.ACTIVATED_AT,
            // Aktivierung und Ist-Start werden beide gebraucht: die eine entscheidet, ob ein Lauf
            // noch aktivierbar ist, der andere, ob seine Startgruppe die Kette blockiert
            // (ScheduleChain.decideNext).
            COMPETITION_MATCH.STARTED_AT,
            roundMaterialized,
            matchOpen,
        )
            .from(EVENT_SCHEDULE_SLOT)
            .leftJoin(COMPETITION_SETUP_MATCH)
            .on(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .leftJoin(COMPETITION_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH))
            .where(EVENT_SCHEDULE_SLOT.EVENT.eq(eventId))
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

    /**
     * Größte Startzeit unter allen Slots des Events, deren verknüpfter Lauf bereits beendet ist —
     * null, wenn im Zeitstrahl des Events noch nichts beendet ist. Genau diese Unterscheidung
     * braucht `ScheduleChainService.resumeIfParked`: Solange nichts beendet ist, hat die Regatta
     * noch nicht begonnen, und den allerersten Lauf ruft der Schiedsrichter selbst an den Start.
     *
     * Der Wert selbst ist seit dem 10.08.2026 kein Startpunkt der Kette mehr. Als monotone Front
     * ("springt nie zurück") hat er genau das getan, was er sollte — und damit den offenen Rest des
     * ersten Regattatags übersprungen, sobald am zweiten Tag ein Lauf beendet war. Wo die Kette
     * ansetzt, entscheidet jetzt der Zeitplan selbst (siehe [getChainSlots]).
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
            COMPETITION_MATCH.ACTIVATED_AT,
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
            COMPETITION_PROPERTIES.IDENTIFIER.`as`("competition_identifier"),
            COMPETITION_PROPERTIES.SHORT_NAME.`as`("competition_short_name"),
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            // Freilose zeigen ihren materialisierten Namen (V202608121300) - gerade hier: die
            // nicht verplanten Läufe sind vor allem Dauer-Freilose.
            DSL.coalesce(COMPETITION_MATCH.BYE_NAME, COMPETITION_SETUP_MATCH.NAME).`as`("match_name"),
            // Der Lauf-Zustand, sofern die Runde schon gesetzt ist: Die nicht verplanten Läufe
            // sind vor allem Dauer-Freilose, und ob so eines noch quittiert werden muss, soll
            // der Zeitplan zeigen, statt es hinter "Zur Durchführung" zu verstecken.
            COMPETITION_MATCH.ACTIVATED_AT.`as`("match_activated_at"),
            COMPETITION_MATCH.STARTED_AT.`as`("match_started_at"),
            COMPETITION_MATCH.FINISHED_AT.`as`("match_finished_at"),
        )
            .from(COMPETITION_SETUP_MATCH)
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
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

    /**
     * Ob die Setup-Runde überhaupt materialisiert ist - mindestens ein Lauf (competition_match)
     * existiert für eine ihrer Setup-Zeilen. Scoped aufs Event: eine setupRoundId aus einem anderen
     * Event darf hier nicht fälschlich als "materialisiert" durchgehen (siehe
     * EventScheduleService.setRoundSkipped, RoundNotMaterialized).
     */
    fun countMatchesInRound(eventId: UUID, setupRoundId: UUID) = Jooq.query {
        fetchCount(
            select(COMPETITION_MATCH.COMPETITION_SETUP_MATCH)
                .from(COMPETITION_MATCH)
                .join(COMPETITION_SETUP_MATCH)
                .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
                .join(COMPETITION_SETUP_ROUND)
                .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
                .join(COMPETITION_PROPERTIES)
                .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
                .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
                .where(COMPETITION.EVENT.eq(eventId))
                .and(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(setupRoundId))
        )
    }

    /**
     * Zählt die Läufe der Runde, die noch mindestens 2 tatsächlich fahrende Mannschaften haben -
     * dasselbe "fahrend"-Prädikat wie im `match_open` von [getChainSlots] (nicht `out`, keine
     * Abmeldung für diese Runde), nur pro Lauf gruppiert/gezählt statt auf Existenz geprüft. >0 heißt: die Runde muss noch gefahren werden, "Runde entfällt" ist dann
     * nicht erlaubt (siehe EventScheduleService.setRoundSkipped, RoundHasRunsToRace).
     */
    fun countRaceableMatchesInRound(eventId: UUID, setupRoundId: UUID) = Jooq.query {
        select(COMPETITION_MATCH.COMPETITION_SETUP_MATCH)
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .join(COMPETITION_MATCH_TEAM)
            .on(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(setupRoundId))
            .and(COMPETITION_MATCH_TEAM.OUT.isFalse)
            .andNotExists(
                selectOne()
                    .from(COMPETITION_DEREGISTRATION)
                    .where(COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.eq(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION))
                    .and(COMPETITION_DEREGISTRATION.COMPETITION_SETUP_ROUND.eq(setupRoundId))
            )
            .groupBy(COMPETITION_MATCH.COMPETITION_SETUP_MATCH)
            .having(DSL.count(COMPETITION_MATCH_TEAM.ID).ge(2))
            .fetch()
            .size
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
