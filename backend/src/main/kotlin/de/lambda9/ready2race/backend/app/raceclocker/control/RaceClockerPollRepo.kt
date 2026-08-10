package de.lambda9.ready2race.backend.app.raceclocker.control

import de.lambda9.ready2race.backend.app.competitionExecution.entity.WaveName
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerMatchTarget
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerPollCandidate
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerPollEvent
import de.lambda9.ready2race.backend.app.timingConfig.entity.TimingSystem
import de.lambda9.ready2race.backend.database.generated.tables.references.*
import de.lambda9.tailwind.jooq.Jooq
import org.jooq.impl.DSL
import java.time.LocalDateTime
import java.util.UUID

/**
 * Die Abfragen des automatischen RaceClocker-Abrufs.
 *
 * Der Herzschlag des Jobs läuft im Sekundentakt, deshalb ist [getPollingEvents] absichtlich winzig:
 * Sie liefert nur die Veranstaltungen mit eingeschalteter Automatik (im Normalfall keine oder eine)
 * und entscheidet damit, ob der teurere [getCandidates] überhaupt gebraucht wird.
 */
object RaceClockerPollRepo {

    fun getPollingEvents() = Jooq.query {
        select(
            EVENT.ID,
            EVENT.RACECLOCKER_INTERVAL_ACTIVE_SECONDS,
            EVENT.RACECLOCKER_INTERVAL_UPCOMING_SECONDS,
            EVENT.RACECLOCKER_WATCH_BEFORE_MINUTES,
            EVENT.RACECLOCKER_WATCH_AFTER_MINUTES,
        )
            .from(EVENT)
            .where(EVENT.RACECLOCKER_AUTO_PULL.isTrue)
            // Challenge-Veranstaltungen haben keine Läufe im Sinne der Durchführung; der manuelle
            // Pull weist sie mit IsChallengeEvent ab, der Job lädt sie gar nicht erst.
            .and(EVENT.CHALLENGE_EVENT.isFalse)
            .fetch {
                RaceClockerPollEvent(
                    // Im Schema not null; die Projektion verliert nur die Garantie.
                    eventId = it[EVENT.ID]!!,
                    intervalActiveSeconds = it[EVENT.RACECLOCKER_INTERVAL_ACTIVE_SECONDS]!!,
                    intervalUpcomingSeconds = it[EVENT.RACECLOCKER_INTERVAL_UPCOMING_SECONDS]!!,
                    watchBeforeMinutes = it[EVENT.RACECLOCKER_WATCH_BEFORE_MINUTES]!!,
                    watchAfterMinutes = it[EVENT.RACECLOCKER_WATCH_AFTER_MINUTES]!!,
                )
            }
    }

    /**
     * Die Läufe einer Veranstaltung, die der Job anfassen darf. Dieselbe Coalesce-Kette wie
     * [de.lambda9.ready2race.backend.app.competitionExecution.control.CompetitionMatchRepo.getForRaceClockerPull]
     * - Wettkampf-Wert vor Veranstaltungs-Voreinstellung.
     *
     * Ausgeschlossen sind hier nur die harten Fälle: beendet, pausiert, kein RaceClocker, keine URL,
     * Slot abgesagt. Das Zeitfenster fehlt bewusst - es hängt an `now` und gehört in die prüfbare
     * Logik, nicht in SQL.
     */
    fun getCandidates(eventId: UUID) = Jooq.query {
        // time_trial_url/heats_url werden aliasiert gebraucht: einmal für die SELECT-Liste (die
        // fetch{}-Projektion liest unten per `it[timeTrialUrl]`/`it[heatsUrl]` genau diese
        // Alias-Felder), UND roh (ohne Alias) für die WHERE-Klausel. timing_system wird nirgends
        // projiziert, braucht also gar keinen Alias. jOOQ rendert einen wiederverwendeten
        // aliasierten Field-Ausdruck im WHERE nämlich nur als bloßen Alias-Bezeichner
        // ("timing_system" statt coalesce(...)) - Postgres akzeptiert Output-Aliase im WHERE
        // grundsätzlich nicht, und bei timing_system wäre der bloße Name zusätzlich mehrdeutig,
        // weil sowohl competition.timing_system als auch event.timing_system in der Join-Kette
        // stehen. Deshalb hier bewusst eigene *Filter-Variablen ohne Alias für das WHERE - nicht
        // wieder mit den SELECT-Feldern zusammenlegen.
        val timingSystem = DSL.coalesce(COMPETITION.TIMING_SYSTEM, EVENT.TIMING_SYSTEM)
        val timeTrialUrl = DSL.coalesce(COMPETITION.RACECLOCKER_TT_RESULTS_URL, EVENT.RACECLOCKER_TT_RESULTS_URL)
            .`as`("time_trial_url")
        val timeTrialUrlFilter =
            DSL.coalesce(COMPETITION.RACECLOCKER_TT_RESULTS_URL, EVENT.RACECLOCKER_TT_RESULTS_URL)
        val heatsUrl = DSL.coalesce(COMPETITION.RACECLOCKER_HEATS_RESULTS_URL, EVENT.RACECLOCKER_HEATS_RESULTS_URL)
            .`as`("heats_url")
        val heatsUrlFilter =
            DSL.coalesce(COMPETITION.RACECLOCKER_HEATS_RESULTS_URL, EVENT.RACECLOCKER_HEATS_RESULTS_URL)

        select(
            COMPETITION_MATCH.COMPETITION_SETUP_MATCH,
            COMPETITION_MATCH.START_TIME,
            COMPETITION_MATCH.ACTIVATED_AT,
            // Der Ist-Start entscheidet, ob der Abruf ihn im Feed noch nachtragen muss.
            COMPETITION_MATCH.STARTED_AT,
            COMPETITION_SETUP_MATCH.NAME,
            COMPETITION_SETUP_ROUND.IS_QUALIFICATION,
            COMPETITION.ID.`as`("competition_id"),
            COMPETITION_PROPERTIES.IDENTIFIER,
            COMPETITION_PROPERTIES.SHORT_NAME,
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
            .where(COMPETITION.EVENT.eq(eventId))
            .and(COMPETITION_MATCH.FINISHED_AT.isNull)
            .and(COMPETITION_MATCH.RACECLOCKER_AUTO_PAUSED_AT.isNull)
            .and(timingSystem.eq(TimingSystem.RACECLOCKER.name))
            .and(DSL.or(timeTrialUrlFilter.isNotNull, heatsUrlFilter.isNotNull))
            // Ein abgesagter Slot bleibt abgesagt, auch wenn in RaceClocker jemand die Welle
            // startet. Die volle Zustandsableitung (EventScheduleLogic.deriveSlotState) ist hier
            // nicht nötig: Ihre beiden anderen Eingaben sind an dieser Stelle konstant - der Lauf
            // existiert, also ist die Runde erzeugt -, und damit bleibt genau `skipped_at` übrig.
            .andNotExists(
                DSL.selectOne()
                    .from(EVENT_SCHEDULE_SLOT)
                    .where(EVENT_SCHEDULE_SLOT.COMPETITION_SETUP_MATCH.eq(COMPETITION_MATCH.COMPETITION_SETUP_MATCH))
                    .and(EVENT_SCHEDULE_SLOT.SKIPPED_AT.isNotNull)
            )
            .fetch {
                RaceClockerPollCandidate(
                    matchId = it[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!!,
                    competitionId = it["competition_id", UUID::class.java],
                    startTime = it[COMPETITION_MATCH.START_TIME],
                    activatedAt = it[COMPETITION_MATCH.ACTIVATED_AT],
                    startedAt = it[COMPETITION_MATCH.STARTED_AT],
                    target = RaceClockerMatchTarget(
                        waveName = WaveName.format(
                            matchName = it[COMPETITION_SETUP_MATCH.NAME],
                            startTime = it[COMPETITION_MATCH.START_TIME],
                            competitionIdentifier = it[COMPETITION_PROPERTIES.IDENTIFIER],
                            competitionShortName = it[COMPETITION_PROPERTIES.SHORT_NAME],
                        ),
                        isQualification = it[COMPETITION_SETUP_ROUND.IS_QUALIFICATION] == true,
                        timeTrialUrl = it[timeTrialUrl],
                        heatsUrl = it[heatsUrl],
                    ),
                )
            }
    }

    /**
     * Ob dieser Lauf gerade für die Automatik pausiert ist.
     *
     * [getCandidates] filtert das schon einmal, aber am Anfang des Takts. Zwischen dieser Lesung und
     * dem Schreiben liegen bis zu zwei HTTP-Abrufe mit je zehn Sekunden Zeitlimit - lang genug, dass
     * ein Schiedsrichter dazwischen von Hand einträgt und damit pausiert. Der Job fragt deshalb ein
     * zweites Mal, in derselben Transaktion wie das Schreiben, und lässt den Lauf dann in Ruhe.
     */
    fun isAutoPaused(matchId: UUID) = Jooq.query {
        fetchExists(
            selectOne()
                .from(COMPETITION_MATCH)
                .where(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(matchId))
                .and(COMPETITION_MATCH.RACECLOCKER_AUTO_PAUSED_AT.isNotNull)
        )
    }

    /**
     * Ob es für diesen Lauf überhaupt eine Automatik gibt, die man pausieren könnte: eingeschaltete
     * Automatik an der Veranstaltung und RaceClocker als (geerbtes) Zeitnahmesystem.
     *
     * Ohne diese Frage sammelt jede Handeingabe auf jeder Veranstaltung ein
     * `raceclocker_auto_paused_at` ein - auch dort, wo gar kein Zeitnahmesystem gesetzt ist. Die
     * Oberfläche schriebe dann dauerhaft „Automatischer Abruf pausiert" an einen Lauf und meinte
     * damit etwas, das es nicht gibt. Die Bedingungen sind bewusst dieselben wie in
     * [getPollingEvents] und [getCandidates]: Was der Job nie anfasst, wird auch nicht pausiert.
     */
    fun isAutoPullConfigured(matchId: UUID) = Jooq.query {
        fetchExists(
            selectOne()
                .from(COMPETITION_MATCH)
                .join(COMPETITION_SETUP_MATCH)
                .on(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(COMPETITION_SETUP_MATCH.ID))
                .join(COMPETITION_SETUP_ROUND)
                .on(COMPETITION_SETUP_MATCH.COMPETITION_SETUP_ROUND.eq(COMPETITION_SETUP_ROUND.ID))
                .join(COMPETITION_PROPERTIES)
                .on(COMPETITION_SETUP_ROUND.COMPETITION_SETUP.eq(COMPETITION_PROPERTIES.ID))
                .join(COMPETITION).on(COMPETITION_PROPERTIES.COMPETITION.eq(COMPETITION.ID))
                .join(EVENT).on(COMPETITION.EVENT.eq(EVENT.ID))
                .where(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(matchId))
                .and(EVENT.RACECLOCKER_AUTO_PULL.isTrue)
                .and(EVENT.CHALLENGE_EVENT.isFalse)
                .and(DSL.coalesce(COMPETITION.TIMING_SYSTEM, EVENT.TIMING_SYSTEM).eq(TimingSystem.RACECLOCKER.name))
        )
    }

    /**
     * Der Ausgang eines Abrufversuchs. Rührt `updated_at`/`updated_by` bewusst NICHT an: Der
     * Zeitstempel sagt "der Job war hier", nicht "am Lauf hat sich etwas geändert" - sonst sähe im
     * Änderungsprotokoll alle fünf Sekunden jeder aktive Lauf bearbeitet aus.
     */
    fun recordPoll(matchId: UUID, at: LocalDateTime, errorCode: String?) = Jooq.query {
        update(COMPETITION_MATCH)
            .set(COMPETITION_MATCH.RACECLOCKER_POLLED_AT, at)
            .set(COMPETITION_MATCH.RACECLOCKER_POLL_ERROR, errorCode)
            .where(COMPETITION_MATCH.COMPETITION_SETUP_MATCH.eq(matchId))
            .execute()
    }
}
