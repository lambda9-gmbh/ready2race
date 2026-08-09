package de.lambda9.ready2race.backend.app.competitionExecution.control

import de.lambda9.ready2race.backend.app.competitionExecution.entity.MatchForRunningStatusDto
import de.lambda9.ready2race.backend.app.competitionExecution.entity.StartListConfigTarget
import de.lambda9.ready2race.backend.app.competitionExecution.entity.WaveName
import de.lambda9.ready2race.backend.app.event.entity.PublicResultsVisibility
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingSystem
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
     * Everything needed to pull this match's results from RaceClocker: the wave name (match name plus
     * planned start time, see [WaveName] - MUST be formatted exactly like
     * [de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService.buildCsv]
     * builds it for the export, or the wave-name fallback filter in `assignFeedRows` never matches)
     * and the competition's two results URLs. Which of the two applies follows from the round: a
     * qualification round is timed as a separate time trial race, because only individual starts have
     * a real countdown in RaceClocker.
     */
    fun getForRaceClockerPull(id: UUID) = Jooq.query {
        // Wettkampf-Wert vor Veranstaltungs-Voreinstellung (Migration V202608062100): die
        // RaceClocker-Rennen werden pro Veranstaltung angelegt, einzelne Wettkaempfe koennen mit
        // eigenen URLs ausscheren.
        val timeTrialUrl = DSL.coalesce(COMPETITION.RACECLOCKER_TT_RESULTS_URL, EVENT.RACECLOCKER_TT_RESULTS_URL).`as`("time_trial_url")
        val heatsUrl = DSL.coalesce(COMPETITION.RACECLOCKER_HEATS_RESULTS_URL, EVENT.RACECLOCKER_HEATS_RESULTS_URL).`as`("heats_url")

        select(
            COMPETITION_SETUP_MATCH.NAME,
            COMPETITION_MATCH.START_TIME,
            COMPETITION_SETUP_ROUND.IS_QUALIFICATION,
            timeTrialUrl,
            heatsUrl,
        )
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
                RaceClockerMatchTarget(
                    waveName = WaveName.format(it[COMPETITION_SETUP_MATCH.NAME], it[COMPETITION_MATCH.START_TIME]),
                    // Not null in the schema; the projection just loses that guarantee.
                    isQualification = it[COMPETITION_SETUP_ROUND.IS_QUALIFICATION] == true,
                    timeTrialUrl = it[timeTrialUrl],
                    heatsUrl = it[heatsUrl],
                )
            }
    }

    /**
     * Welches Spalten-Preset die Startliste dieses Laufs bekommt. Dieselbe Join-Kette wie
     * [getForRaceClockerPull] und aus demselben Grund dieselbe Weiche: die Runde entscheidet, weil
     * RaceClocker pro Wettkampf zwei Rennen mit unterschiedlichen Spalten braucht.
     */
    fun getStartListConfigTarget(id: UUID) = Jooq.query {
        // Wettkampf-Wert vor Veranstaltungs-Voreinstellung, wie in getForRaceClockerPull.
        val timingSystem = DSL.coalesce(COMPETITION.TIMING_SYSTEM, EVENT.TIMING_SYSTEM).`as`("timing_system")
        val qualificationConfig = DSL.coalesce(
            COMPETITION.STARTLIST_CONFIG_QUALIFICATION,
            EVENT.STARTLIST_CONFIG_QUALIFICATION,
        ).`as`("qualification_config")
        val roundsConfig = DSL.coalesce(
            COMPETITION.STARTLIST_CONFIG_ROUNDS,
            EVENT.STARTLIST_CONFIG_ROUNDS,
        ).`as`("rounds_config")

        select(
            COMPETITION_SETUP_ROUND.IS_QUALIFICATION,
            timingSystem,
            qualificationConfig,
            roundsConfig,
        )
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
                    // Im Schema not null; die Projektion verliert nur die Garantie.
                    isQualification = it[COMPETITION_SETUP_ROUND.IS_QUALIFICATION] == true,
                    // Als Text gespeichert, kein jOOQ-Converter in diesem Projekt -- von Hand
                    // konvertiert wie EventRepo.getChainProgressionMode. null ist hier legitim:
                    // es bedeutet "kein Zeitnahmesystem gesetzt".
                    timingSystem = it[timingSystem]?.let { s -> TimingSystem.valueOf(s) },
                    qualificationConfig = it[qualificationConfig],
                    roundsConfig = it[roundsConfig],
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
    ) = Jooq.query {
        // Kein Boot mehr ohne Ergebnis: abgemeldete und ausgeschiedene Boote zählen nicht mit,
        // für sie kommt keins mehr (dieselbe Auslegung wie LiveDashboardLogic.teamHasResult).
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
            COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_VIEW.NAME.`as`("competition_name"),
            COMPETITION_VIEW.CATEGORY_NAME,
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
            }
            .orderBy(COMPETITION_MATCH.UPDATED_AT.desc())
            .limit(limit)
            .fetch()
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
            COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_VIEW.NAME.`as`("competition_name"),
            COMPETITION_VIEW.CATEGORY_NAME,
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
            COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
            COMPETITION_SETUP_MATCH.START_TIME_OFFSET,
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_VIEW.NAME.`as`("competition_name"),
            COMPETITION_VIEW.CATEGORY_NAME,
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
            COMPETITION_SETUP_MATCH.NAME.`as`("match_name"),
            COMPETITION_SETUP_MATCH.START_TIME_OFFSET,
            COMPETITION_SETUP_ROUND.NAME.`as`("round_name"),
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_VIEW.NAME.`as`("competition_name"),
            COMPETITION_VIEW.CATEGORY_NAME,
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