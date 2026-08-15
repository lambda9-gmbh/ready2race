package de.lambda9.ready2race.backend.app.competitionExecution.control

import de.lambda9.ready2race.backend.app.competitionExecution.entity.BulkStartlistCompetitionRow
import de.lambda9.ready2race.backend.app.competitionExecution.entity.MatchForRunningStatusDto
import de.lambda9.ready2race.backend.app.competitionExecution.entity.StartListConfigTarget
import de.lambda9.ready2race.backend.app.competitionExecution.entity.WaveName
import de.lambda9.ready2race.backend.app.event.entity.PublicResultsVisibility
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceRef
import de.lambda9.ready2race.backend.database.*
import de.lambda9.ready2race.backend.database.generated.tables.records.CompetitionMatchRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.jooq.JIO
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.Record10
import org.jooq.Record12
import org.jooq.Result
import org.jooq.impl.DSL
import org.jooq.impl.DSL.*
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

object CompetitionMatchRepo {
    fun create(records: List<CompetitionMatchRecord>) = COMPETITION_MATCH.insert(records)

    fun exists(id: UUID) = COMPETITION_MATCH.exists { COMPETITION_SETUP_MATCH.eq(id) }

    // Of the given setup match ids, returns those that have already been created during execution.
    fun getExistingSetupMatchIds(ids: Collection<UUID>) =
        COMPETITION_MATCH.select({ COMPETITION_SETUP_MATCH }) { COMPETITION_SETUP_MATCH.`in`(ids) }

    fun update(id: UUID, f: CompetitionMatchRecord.() -> Unit) =
        COMPETITION_MATCH.update(f) { COMPETITION_SETUP_MATCH.eq(id) }

    fun delete(ids: List<UUID>) = COMPETITION_MATCH.delete { COMPETITION_SETUP_MATCH.`in`(ids) }

    /** Setzt den Vermerk "Paarung neu berechnet" auf die angegebenen Läufe. */
    fun markPairingsRecalculated(setupMatchIds: List<UUID>, at: LocalDateTime) =
        COMPETITION_MATCH.updateMany(
            f = { pairingsRecalculatedAt = at },
            condition = { COMPETITION_SETUP_MATCH.`in`(setupMatchIds) },
        )

    /**
     * Materialisiert die Freilos-Namen ("Freilos <Setzungszahl>") an den Lauf-Instanzen der
     * soeben erzeugten Runde (Schlüssel: Setup-Lauf-Id = Primärschlüssel von competition_match).
     * Nur `createNewRound` schreibt hier, direkt nach der Erzeugung - ein Zurücksetzen braucht es
     * nicht, weil die Instanz samt Name beim Löschen der Runde stirbt (V202608121300).
     */
    fun setByeNames(byeNameBySetupMatch: Map<UUID, String>) =
        COMPETITION_MATCH.updateMany(
            f = { byeName = byeNameBySetupMatch.getValue(competitionSetupMatch!!) },
            condition = { COMPETITION_SETUP_MATCH.`in`(byeNameBySetupMatch.keys) },
        )

    /**
     * Der Wettkampf, zu dem dieser Lauf gehört — die Kette Lauf → Setup-Lauf → Runde →
     * Eigenschaften → Wettkampf. Die Aufrufer der Automatik kennen nur den Lauf.
     */
    fun getCompetitionId(setupMatchId: UUID) = Jooq.query {
        select(COMPETITION_PROPERTIES.COMPETITION)
            .from(COMPETITION_SETUP_MATCH)
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_ROUND.ID.eq(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_PROPERTIES.ID.eq(COMPETITION_SETUP_ROUND.COMPETITION_SETUP))
            .where(COMPETITION_SETUP_MATCH.ID.eq(setupMatchId))
            .fetchOne(COMPETITION_PROPERTIES.COMPETITION)
    }

    fun getForStartList(id: UUID) = STARTLIST_VIEW.selectOne { ID.eq(id) }

    /**
     * Everything needed to pull this match's results from RaceClocker: the wave name (planned start
     * time, competition and match name, see [WaveName] - MUST be formatted exactly like
     * [de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService.buildCsv]
     * builds it for the export, or the wave-name fallback filter in `assignFeedRows` never matches)
     * and the ONE RaceClocker race the competition selected - since 2026-08-11 it serves the
     * qualification and every other round alike.
     */
    fun getForRaceClockerPull(id: UUID) = Jooq.query {
        // Die Zuordnung Wettkampf→Rennen steht ausschließlich am Wettkampf (seit dem 11.08.2026).
        // Der frühere Veranstaltungs-Default ist entfallen: er duplizierte die Pro-Rennen-Zuordnung
        // und ein nicht zugeordneter Wettkampf soll ehrlich „kein Rennen" sein, statt still zu erben.
        select(
            // Freilose tragen ihren materialisierten Namen (V202608121300) - dieselbe Koaleszenz
            // wie startlist_view, sonst fände der Wellennamen-Abgleich die exportierte Welle nicht.
            DSL.coalesce(COMPETITION_MATCH.BYE_NAME, COMPETITION_SETUP_MATCH.NAME).`as`("match_name"),
            COMPETITION_MATCH.START_TIME,
            // Kennung und Kürzel tragen den Wettkampf in den Wellennamen (crf-2026); die drei
            // Rennen-Spalten die Anwahl.
            COMPETITION_PROPERTIES.IDENTIFIER,
            COMPETITION_PROPERTIES.SHORT_NAME,
            RACECLOCKER_RACE.ID,
            RACECLOCKER_RACE.NAME,
            RACECLOCKER_RACE.RESULTS_URL,
        )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(RACECLOCKER_RACE).on(RACECLOCKER_RACE.ID.eq(COMPETITION.RACECLOCKER_RACE))
            .where(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(id))
            .fetchOne { record ->
                RaceClockerMatchTarget(
                    waveName = WaveName.format(
                        matchName = record["match_name", String::class.java],
                        startTime = record[COMPETITION_MATCH.START_TIME],
                        competitionIdentifier = record[COMPETITION_PROPERTIES.IDENTIFIER],
                        competitionShortName = record[COMPETITION_PROPERTIES.SHORT_NAME],
                    ),
                    race = record[RACECLOCKER_RACE.ID]?.let {
                        RaceClockerRaceRef(it, record[RACECLOCKER_RACE.NAME]!!, record[RACECLOCKER_RACE.RESULTS_URL]!!)
                    },
                )
            }
    }

    /**
     * Die Wettkämpfe einer Veranstaltung für den Startlisten-Sammelexport: Kennung (Dateiname und
     * Reihenfolge der ZIP-Einträge) und das angewählte RaceClocker-Rennen (Delta-Abgleich und
     * Rennen-Filter, null = keines angewählt). Sortiert nach Kennung, damit die ZIP-Einträge in
     * Programmreihenfolge liegen. Bewusst ungefiltert - ob auf ein Rennen eingeschränkt wird,
     * entscheidet der Service (eventStartlistPlan).
     */
    fun getForBulkStartlistExport(eventId: UUID) = Jooq.query {
        select(
            COMPETITION.ID,
            COMPETITION_PROPERTIES.IDENTIFIER,
            RACECLOCKER_RACE.RESULTS_URL,
            RACECLOCKER_RACE.ID,
            COMPETITION_PROPERTIES.SHORT_NAME,
            COMPETITION_PROPERTIES.NAME,
        )
            .from(COMPETITION)
            .join(COMPETITION_PROPERTIES).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(RACECLOCKER_RACE).on(RACECLOCKER_RACE.ID.eq(COMPETITION.RACECLOCKER_RACE))
            .where(COMPETITION.EVENT.eq(eventId))
            .orderBy(COMPETITION_PROPERTIES.IDENTIFIER)
            .fetch { record ->
                BulkStartlistCompetitionRow(
                    competitionId = record[COMPETITION.ID]!!,
                    identifier = record[COMPETITION_PROPERTIES.IDENTIFIER]!!,
                    raceUrl = record[RACECLOCKER_RACE.RESULTS_URL],
                    raceId = record[RACECLOCKER_RACE.ID],
                    shortName = record[COMPETITION_PROPERTIES.SHORT_NAME],
                    name = record[COMPETITION_PROPERTIES.NAME],
                )
            }
    }

    /**
     * Welches Spalten-Preset die Startliste dieses Laufs bekommt: das eine Preset des Wettkampfs,
     * mit der Veranstaltung als Vorgabe. Die frühere Weiche nach Rundenart ist mit den
     * RaceClocker-Startarten entfallen (11.08.2026) — jede Runde exportiert dieselben Spalten.
     */
    fun getStartListConfigTarget(id: UUID) = Jooq.query {
        // Wettkampf-Wert vor Veranstaltungs-Voreinstellung, wie beim Zeitnahmesystem.
        val config = DSL.coalesce(
            COMPETITION.STARTLIST_CONFIG,
            EVENT.STARTLIST_CONFIG,
        ).`as`("startlist_config")

        select(config)
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .join(EVENT).on(COMPETITION.EVENT.eq(EVENT.ID))
            .where(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(id))
            .fetchOne {
                StartListConfigTarget(
                    // null ist hier legitim: es bedeutet "kein Preset konfiguriert".
                    configId = it[config],
                )
            }
    }

    /**
     * Die Läufe, die auf den öffentlichen Ansichten als Ergebnis erscheinen dürfen. [visibility]
     * kommt aus `Event.publicResultsVisibility`; die Regel selbst steht als reine Funktion in
     * [de.lambda9.ready2race.backend.app.eventInfo.boundary.AthleteBoardLogic.isPublicResult] und
     * ein zweites Mal in der View `competition_having_results` (Wettbewerbsauswahl derselben
     * Seite). Die drei Stellen gehören zusammen.
     */
    fun getMatchResults(
        eventId: UUID,
        competitionId: UUID?,
        limit: Int,
        visibility: PublicResultsVisibility,
        // Auf EINEN Lauf eingegrenzt ("Mein Event" öffnet das Feld des angetippten Laufs).
        // Bewusst ein Filter in derselben Abfrage statt eines eigenen Selects: so kann ein
        // einzelner Lauf gar nicht an der Sichtbarkeitsregel oben vorbeikommen.
        matchId: UUID? = null,
    ) = Jooq.query {
        // Kein Boot mehr ohne Ergebnis: abgemeldete und ausgeschiedene Boote zählen nicht mit,
        // für sie kommt keins mehr (dieselbe Auslegung wie LiveDashboardLogic.teamIsSettled -
        // die Frage ist "erledigt", nicht "gefahren").
        val allTeamsScored = notExists(
            selectOne()
                .from(COMPETITION_MATCH_TEAM)
                .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH))
                .and(COMPETITION_MATCH_TEAM.PLACE.isNull)
                .and(COMPETITION_MATCH_TEAM.OUT.isFalse)
                .and(COMPETITION_MATCH_TEAM.FAILED.isFalse)
                .and(
                    notExists(
                        selectOne()
                            .from(COMPETITION_DEREGISTRATION)
                            .where(COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.eq(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION))
                            .and(COMPETITION_DEREGISTRATION.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
                    )
                )
        )

        // Ein beendeter Lauf (finished_at gesetzt) ist immer ein Ergebnis. Ein vollständig
        // gewerteter, aber nicht beendeter Lauf (Zustand AWAITING_FINISH) nur dann, wenn die
        // Veranstaltung das erlaubt — bis zum Beenden kann noch eine Zeitstrafe kommen.
        val publiclyVisible = when (visibility) {
            PublicResultsVisibility.FINISHED_ONLY -> COMPETITION_MATCH.FINISHED_AT.isNotNull
            PublicResultsVisibility.RESULTS_COMPLETE ->
                COMPETITION_MATCH.FINISHED_AT.isNotNull.or(allTeamsScored)
        }

        select(
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH,
            COMPETITION_MATCH.UPDATED_AT,
            COMPETITION_MATCH.START_TIME,
            COMPETITION_MATCH.STARTED_AT,
            // Freilose zeigen ihren materialisierten Namen (V202608121300).
            DSL.coalesce(COMPETITION_MATCH.BYE_NAME, COMPETITION_SETUP_MATCH.NAME).`as`("match_name"),
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_VIEW.NAME.`as`("competition_name"),
            COMPETITION_VIEW.CATEGORY_NAME,
            COMPETITION_VIEW.SHORT_NAME,
        )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_VIEW).on(COMPETITION_VIEW.ID.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(publiclyVisible)
            .and(
                field(
                    select(count())
                        .from(COMPETITION_MATCH_TEAM)
                        .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH))
                        .and(COMPETITION_MATCH_TEAM.OUT.isFalse)
                ).let {
                    it.gt(1).or(
                        it.gt(0).and(
                            COMPETITION_SETUP_ROUND.REQUIRED.isTrue
                        )
                    )
                }
            )
            .apply {
                if (competitionId != null) {
                    and(COMPETITION.ID.eq(competitionId))
                }
                if (matchId != null) {
                    and(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(matchId))
                }
            }
            .orderBy(COMPETITION_MATCH.UPDATED_AT.desc())
            .limit(limit)
            .fetch()
    }

    /**
     * Die geplante Startzeit des im Zeitplan spätesten Laufs mit Aktivität (gestartet, aktiviert
     * oder beendet) — die Schwelle der Programm-Reihenfolgen-Regel der Boards
     * ([de.lambda9.ready2race.backend.app.eventInfo.boundary.BoardLogic.freeSlotPassed]):
     * Programmpunkte vor dieser Zeit gelten als überholt.
     */
    fun getLatestProgressStartTime(eventId: UUID): JIO<LocalDateTime?> = Jooq.query {
        select(max(COMPETITION_MATCH.START_TIME))
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(
                COMPETITION_MATCH.STARTED_AT.isNotNull
                    .or(COMPETITION_MATCH.ACTIVATED_AT.isNotNull)
                    .or(COMPETITION_MATCH.FINISHED_AT.isNotNull)
            )
            .fetchOne { it.value1() }
    }

    /**
     * Ist-Start und geplanter Start des zuletzt gestarteten Laufs der Veranstaltung — die
     * Eingabe der Verspätungsanzeige
     * ([de.lambda9.ready2race.backend.app.eventInfo.boundary.BoardLogic.currentDelaySeconds]).
     * Leer, solange noch nichts gestartet ist.
     */
    fun getLatestStartedTimes(eventId: UUID): JIO<List<Pair<LocalDateTime, LocalDateTime?>>> =
        Jooq.query {
            select(COMPETITION_MATCH.STARTED_AT, COMPETITION_MATCH.START_TIME)
                .from(COMPETITION_MATCH)
                .join(COMPETITION_SETUP_MATCH)
                .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
                .join(COMPETITION_SETUP_ROUND)
                .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
                .join(COMPETITION_PROPERTIES)
                .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
                .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
                .where(COMPETITION.EVENT.eq(eventId))
                .and(COMPETITION_MATCH.STARTED_AT.isNotNull)
                .orderBy(COMPETITION_MATCH.STARTED_AT.desc())
                .limit(1)
                .fetch { it.value1()!! to it.value2() }
        }

    fun getRunningMatches(
        eventId: UUID,
        limit: Int
    ) = Jooq.query {
        select(
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH,
            COMPETITION_MATCH.START_TIME,
            COMPETITION_MATCH.STARTED_AT,
            COMPETITION_MATCH.ACTIVATED_AT,
            COMPETITION_SETUP_MATCH.EXECUTION_ORDER,
            // Freilose zeigen ihren materialisierten Namen (V202608121300).
            DSL.coalesce(COMPETITION_MATCH.BYE_NAME, COMPETITION_SETUP_MATCH.NAME).`as`("match_name"),
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_VIEW.NAME.`as`("competition_name"),
            COMPETITION_VIEW.CATEGORY_NAME,
            COMPETITION_VIEW.SHORT_NAME,
        )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_VIEW).on(COMPETITION_VIEW.ID.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(COMPETITION_MATCH.ACTIVATED_AT.isNotNull)
            .orderBy(
                COMPETITION_MATCH.START_TIME.asc(),
                COMPETITION_SETUP_MATCH.EXECUTION_ORDER.asc()
            )
            .limit(limit)
            .fetch()
    }

    fun getUpcomingMatches(
        eventId: UUID,
        limit: Int
    ) = Jooq.query {

        select(
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH,
            COMPETITION_MATCH.START_TIME,
            COMPETITION_SETUP_MATCH.EXECUTION_ORDER,
            // Freilose zeigen ihren materialisierten Namen (V202608121300).
            DSL.coalesce(COMPETITION_MATCH.BYE_NAME, COMPETITION_SETUP_MATCH.NAME).`as`("match_name"),
            COMPETITION_SETUP_MATCH.START_TIME_OFFSET,
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_VIEW.NAME.`as`("competition_name"),
            COMPETITION_VIEW.CATEGORY_NAME,
            COMPETITION_VIEW.SHORT_NAME,
        )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_VIEW).on(COMPETITION_VIEW.ID.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(COMPETITION_MATCH.START_TIME.isNotNull)
            .and(COMPETITION_MATCH.START_TIME.gt(LocalDateTime.now()))
            .orderBy(
                COMPETITION_MATCH.START_TIME.asc(),
                COMPETITION_SETUP_MATCH.EXECUTION_ORDER.asc()
            )
            .limit(limit)
            .fetch()
    }

    // Eigene Query für die Athleten-Anzeige: Anders als getUpcomingMatches fällt ein Lauf hier
    // nicht aus der Liste, nur weil seine geplante Startzeit verstrichen ist oder weil er noch
    // gar keine Startzeit hat. Ein Lauf gehört hierher, wenn er nicht gerade läuft, noch kein
    // vollständiges Ergebnis hat (Umkehrung der "abgeschlossen"-Bedingung aus getMatchResults)
    // und entweder keine Startzeit hat oder seine Startzeit noch innerhalb der Nachfrist liegt.
    fun getUpcomingMatchesForBoard(
        eventId: UUID,
        limit: Int,
        grace: Duration
    ) = Jooq.query {

        val threshold = LocalDateTime.now().minus(grace)

        select(
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH,
            COMPETITION_MATCH.START_TIME,
            COMPETITION_SETUP_MATCH.EXECUTION_ORDER,
            // Freilose zeigen ihren materialisierten Namen (V202608121300).
            DSL.coalesce(COMPETITION_MATCH.BYE_NAME, COMPETITION_SETUP_MATCH.NAME).`as`("match_name"),
            COMPETITION_SETUP_MATCH.START_TIME_OFFSET,
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_VIEW.NAME.`as`("competition_name"),
            COMPETITION_VIEW.CATEGORY_NAME,
            COMPETITION_VIEW.SHORT_NAME,
        )
            .from(COMPETITION_MATCH)
            .join(COMPETITION_SETUP_MATCH)
            .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
            .join(COMPETITION_SETUP_ROUND)
            .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
            .join(COMPETITION_PROPERTIES)
            .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
            .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
            .leftJoin(COMPETITION_VIEW).on(COMPETITION_VIEW.ID.eq(COMPETITION.ID))
            .where(COMPETITION.EVENT.eq(eventId))
            .and(COMPETITION_MATCH.ACTIVATED_AT.isNull)
            .and(COMPETITION_MATCH.FINISHED_AT.isNull)
            .and(
                // Unverändert aus getMatchResults übernommene Teilbedingungen, hier als exists
                // statt notExists: es gibt noch ein Team ohne Platz, das nicht ausgeschieden,
                // nicht disqualifiziert und nicht abgemeldet ist - der Lauf ist also noch offen.
                exists(
                    selectOne()
                        .from(COMPETITION_MATCH_TEAM)
                        .where(COMPETITION_MATCH_TEAM.COMPETITION_MATCH.eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH))
                        .and(COMPETITION_MATCH_TEAM.PLACE.isNull)
                        .and(COMPETITION_MATCH_TEAM.OUT.isFalse)
                        .and(COMPETITION_MATCH_TEAM.FAILED.isFalse)
                        .and(notExists(
                            selectOne()
                                .from(COMPETITION_DEREGISTRATION)
                                .where(COMPETITION_DEREGISTRATION.COMPETITION_REGISTRATION.eq(COMPETITION_MATCH_TEAM.COMPETITION_REGISTRATION))
                                .and(COMPETITION_DEREGISTRATION.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
                        ))
                )
            )
            .and(
                COMPETITION_MATCH.START_TIME.isNull
                    .or(COMPETITION_MATCH.START_TIME.gt(threshold))
            )
            .orderBy(
                COMPETITION_MATCH.START_TIME.asc().nullsLast(),
                COMPETITION_SETUP_MATCH.EXECUTION_ORDER.asc()
            )
            .limit(limit)
            .fetch()
    }

    fun getMatchesByEvent(
        eventId: UUID,
        activated: Boolean? = null,
        withoutPlaces: Boolean? = null
    ): JIO<List<MatchForRunningStatusDto>> = Jooq.query {
        val cm = COMPETITION_MATCH
        val csm = COMPETITION_SETUP_MATCH
        val csr = COMPETITION_SETUP_ROUND
        val cs = COMPETITION_SETUP
        val cp = COMPETITION_PROPERTIES
        val c = COMPETITION
        val cmt = COMPETITION_MATCH_TEAM

        var query = select(
            cm.COMPETITION_SETUP_MATCH,
            c.ID,
            cp.NAME,
            DSL.denseRank()
                .over(DSL.partitionBy(c.ID).orderBy(csr.ID))
                .`as`("round_number"),
            csr.NAME,
            DSL.rowNumber()
                .over(DSL.partitionBy(csr.ID).orderBy(csm.EXECUTION_ORDER))
                .`as`("match_number"),
            csm.NAME,
            DSL.case_()
                .`when`(
                    DSL.exists(
                        selectOne()
                            .from(cmt)
                            .where(cmt.COMPETITION_MATCH.eq(cm.COMPETITION_SETUP_MATCH))
                            .and(cmt.PLACE.isNull)
                    ), DSL.inline(false)
                )
                .otherwise(DSL.inline(true))
                .`as`("has_places_set"),
            cm.ACTIVATED_AT,
            cm.START_TIME
        )
            .from(cm)
            .join(csm).on(cm.COMPETITION_SETUP_MATCH.eq(csm.ID))
            .join(csr).on(csm.COMPETITION_SETUP_ROUND.eq(csr.ID))
            .join(cs).on(csr.COMPETITION_SETUP.eq(cs.COMPETITION_PROPERTIES))
            .join(cp).on(cs.COMPETITION_PROPERTIES.eq(cp.ID))
            .join(c).on(cp.COMPETITION.eq(c.ID))
            .where(c.EVENT.eq(eventId))

        if (activated != null) {
            query = query.and(if (activated) cm.ACTIVATED_AT.isNotNull else cm.ACTIVATED_AT.isNull)
        }

        if (withoutPlaces == true) {
            query = query.and(
                DSL.exists(
                    selectOne()
                        .from(cmt)
                        .where(cmt.COMPETITION_MATCH.eq(cm.COMPETITION_SETUP_MATCH))
                        .and(cmt.PLACE.isNull)
                )
            )
        }

        query.fetch { record ->
            MatchForRunningStatusDto(
                id = record.value1()!!,
                competitionId = record.value2()!!,
                competitionName = record.value3()!!,
                roundNumber = record.value4()!!,
                roundName = record.value5()!!,
                matchNumber = record.value6()!!,
                matchName = record.value7(),
                hasPlacesSet = record.value8()!!,
                activatedAt = record.value9(),
                startTime = record.value10()
            )
        }
    }

    /**
     * Die Steg-Scans der Crews dieses Wettkampfs — die Grundlage des Arena-Chips auf der
     * Durchführungsseite („Arena 2/6").
     *
     * Bewusst je Wettkampf statt je Veranstaltung: die Durchführungsseite zeigt immer genau einen
     * Wettkampf, die Scans der übrigen läse dort niemand. Das ist der einzige Unterschied zu
     * [de.lambda9.ready2race.backend.app.participantTracking.control.ParticipantTrackingRepo.getScansByEvent],
     * dem Pendant des Schiedsrichter-Dashboards — sonst dasselbe Muster: eine flache Abfrage, die
     * Reduktion auf den letzten Scan je Person macht der Aufrufer, und ob eine Mannschaft draußen
     * ist, entscheidet weiterhin allein
     * [de.lambda9.ready2race.backend.app.liveDashboard.boundary.LiveDashboardLogic.teamInArenaAt].
     *
     * Abfragelast: ein Index-Zugriff auf `participant_tracking` (Index auf `event`) plus ein
     * `exists` über die Anmeldungen des Wettkampfs. Kein Join je Lauf und kein N+1 je Mannschaft —
     * die Zeilenzahl wächst mit den Scans des Wettkampfs, nicht mit der Zahl der Läufe.
     */
    fun getScansByCompetition(eventId: UUID, competitionId: UUID) = Jooq.query {
        select(
            PARTICIPANT_TRACKING.PARTICIPANT,
            PARTICIPANT_TRACKING.SCAN_TYPE,
            PARTICIPANT_TRACKING.SCANNED_AT,
        )
            .from(PARTICIPANT_TRACKING)
            .where(PARTICIPANT_TRACKING.EVENT.eq(eventId))
            .andExists(
                selectOne()
                    .from(COMPETITION_REGISTRATION_NAMED_PARTICIPANT)
                    .join(COMPETITION_REGISTRATION)
                    .on(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.COMPETITION_REGISTRATION.eq(COMPETITION_REGISTRATION.ID))
                    .where(COMPETITION_REGISTRATION.COMPETITION.eq(competitionId))
                    .and(COMPETITION_REGISTRATION_NAMED_PARTICIPANT.PARTICIPANT.eq(PARTICIPANT_TRACKING.PARTICIPANT))
            )
            .fetch()
    }

    fun getMatchForEventByEvents(eventIds: List<UUID>) = COMPETITION_MATCH_FOR_EVENT.select{ EVENT_ID.`in`(eventIds) }
}