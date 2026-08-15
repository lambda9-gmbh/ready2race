package de.lambda9.ready2race.backend.app.eventInfo.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.competitionExecution.boundary.CompetitionExecutionService
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.MyEventRepo
import de.lambda9.ready2race.backend.app.eventInfo.entity.EventInfoProblem
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventDto
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventRegistrationDto
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventRequirementDto
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventTeamMemberDto
import de.lambda9.ready2race.backend.app.participantRequirement.control.ParticipantRequirementForEventRepo
import de.lambda9.ready2race.backend.app.qrCodeApp.control.QrCodeRepo
import de.lambda9.ready2race.backend.app.substitution.entity.ParticipantForExecutionDto
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.data.Timecode
import de.lambda9.ready2race.backend.database.generated.enums.Gender
import de.lambda9.ready2race.backend.database.generated.tables.records.SubstitutionViewRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.TIMECODE
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
import de.lambda9.tailwind.core.extensions.kio.traverse
import org.jooq.Record
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Das persönliche Dashboard "Mein Event": alles, was eine Person über ihren eigenen Tag wissen
 * will, hinter einem Aufruf ohne Anmeldung. Der QR-Code am Teilnehmerband ist der einzige
 * Ausweis, deshalb entscheidet sich hier - und nur hier -, wer welche Daten sieht.
 */
object MyEventService {

    // Zwischenspeicher je (Veranstaltung, Person). Bei einer Regatta hängt an jedem Armband ein
    // Telefon, das im selben Takt nachlädt; ohne ihn zahlt die Datenbank jeden dieser Abrufe mit
    // einer Handvoll Abfragen. Der Schlüssel enthält die Veranstaltung mit, weil dieselbe Person bei
    // mehreren Veranstaltungen starten kann und die Antworten sich unterscheiden.
    //
    // Anders als bei der Athleten-Anzeige (ein Eintrag je Veranstaltung) wächst die Karte hier mit
    // der Zahl der Personen: jedes gescannte Armband legt einen eigenen Eintrag an, und abgelaufene
    // Einträge verschwinden nicht von selbst, weil dieselbe Person nach dem Rennen einfach nicht
    // mehr nachlädt. Deshalb wird beim Schreiben aufgeräumt, sobald die Karte die Schranke
    // überschreitet - alles Abgelaufene fliegt dann in einem Rutsch heraus.
    private const val CACHE_CLEANUP_THRESHOLD = 500

    // Abfragetakt des persönlichen Dashboards. Ruhiger als die Untergrenze der öffentlichen
    // Anzeigen (AthleteBoardLogic.MIN_REFRESH_INTERVAL_SECONDS): eine Person hat nur ihre eigenen
    // paar Läufe, da ändert sich nichts im Sekundentakt.
    private const val REFRESH_INTERVAL_SECONDS = 15

    // [marker] ist der [EventChangeMarker]-Stand beim Bau: jede Schreibaktion an der
    // Veranstaltung entwertet den Eintrag sofort, die TTL deckelt nur den Nichts-passiert-Fall.
    private data class CachedMyEvent(val builtAt: LocalDateTime, val marker: Long, val dto: MyEventDto)

    private val cache = ConcurrentHashMap<Pair<UUID, UUID>, CachedMyEvent>()

    fun getMyEvent(eventId: UUID, qrCode: String): App<EventInfoProblem, ApiResponse.Dto<MyEventDto>> =
        KIO.comprehension {
            val exists = !EventRepo.exists(eventId).orDie()
            if (!exists) {
                !KIO.fail<EventInfoProblem>(EventInfoProblem.EventNotFound(eventId))
            }

            val record = !QrCodeRepo.findByCode(qrCode).orDie()
            // Drei Fälle, eine Antwort: der Code ist unbekannt, er gehört zu einer anderen
            // Veranstaltung, oder er gehört zu einer Helferrolle (dann steht in `participant`
            // nichts, sondern in `app_user`). Sie dürfen sich nach außen nicht unterscheiden -
            // sonst wird der Endpunkt zum Auskunftsdienst darüber, welche Codes es gibt.
            val participantId = record
                ?.takeIf { it.event == eventId }
                ?.participant
            if (participantId == null) {
                !KIO.fail<EventInfoProblem>(EventInfoProblem.QrCodeNotFound(qrCode))
            }

            val now = LocalDateTime.now()
            val key = eventId to participantId!!

            // serverTime muss je Antwort frisch sein, sie ist die Bezugsgröße für den Countdown
            // auf dem Gerät; der Rest darf aus dem Zwischenspeicher kommen. Die Frist ist
            // dieselbe wie bei der Athleten-Anzeige, weil sie über AthleteBoardLogic.isCacheFresh
            // aus deren CACHE_TTL_SECONDS kommt.
            //
            // Damit ist die Antwort in sich minimal ungleichzeitig: startState und die Aufteilung
            // in laufend/kommend/Ergebnis stammen vom Bauzeitpunkt, die Uhr daneben ist neu. Bei
            // fünf Sekunden Frist fällt das in keiner Anzeige auf; eine Neuberechnung nur wegen
            // der Uhr wäre der Zweck des Zwischenspeichers.
            // Markerstand VOR dem Bau lesen (siehe BoardService.getBoardView): ein Schreibzugriff
            // mitten im Bau macht den Eintrag dann sofort wieder alt statt ihn TTL-lang zu halten.
            val marker = EventChangeMarker.current(eventId)

            val cached = cache[key]
                ?.takeIf { AthleteBoardLogic.isCacheFresh(it.builtAt, now) && it.marker == marker }
            if (cached != null) {
                return@comprehension KIO.ok(ApiResponse.Dto(cached.dto.copy(serverTime = now)))
            }

            // Nach der Existenzprüfung oben kann der Name nicht mehr fehlen; die Abfrage verliert
            // auf dem Weg nur die Zusage darauf.
            val eventName = !EventRepo.getName(eventId).orDie()
            val visibility = !EventRepo.getPublicResultsVisibility(eventId).orDie()
            // Der veranstaltungsweite Hinweis wandert mit in den Zwischenspeicher - sein PUT
            // bumpt den EventChangeMarker, eine Änderung kommt also mit dem nächsten Poll.
            val notice = !EventRepo.getNotice(eventId).orDie()

            val person = !MyEventRepo.findParticipant(participantId).orDie()
            val matchRecords = !MyEventRepo.findMatchesForParticipant(eventId, participantId).orDie()
            // Die Auswechslungen zu genau den Booten und Runden, die oben herausgekommen sind.
            // Sie stehen nicht in derselben Abfrage, weil sie eine eigene Zeilenmenge sind - die
            // Sicht `startlist_team` trennt sie aus demselben Grund von `participants`.
            val substitutionRecords = !MyEventRepo
                .findSubstitutionsForRegistrationRounds(registrationRounds(matchRecords)).orDie()
            val registrationRecords = !MyEventRepo.findRegistrationsWithoutMatch(eventId, participantId).orDie()
            val requirementRecords = !ParticipantRequirementForEventRepo.get(eventId, onlyActive = true).orDie()
            val fulfilledRequirementIds = !MyEventRepo.findFulfilledRequirementIds(eventId, participantId).orDie()

            // Welche Bedingungen für eine Person gelten, hängt an ihrer Rolle im Boot: eine
            // Bedingung ohne Rollenbindung gilt für alle, eine rollengebundene nur für Personen
            // in dieser Rolle. Ohne diese Einschränkung stünde eine fremde Bedingung bei jeder
            // Person als "nicht erfüllt" - ein Fehlalarm, der Leute am Veranstaltungstag ohne
            // Grund zur Meldestelle schickt. Die Regel kommt bewusst aus dem bestehenden Repo
            // und wird hier nicht nachgebaut.
            val namedParticipantIds =
                !MyEventRepo.findNamedParticipantIdsForParticipant(eventId, participantId).orDie()
            val applicableRequirementIds = !ParticipantRequirementForEventRepo
                .getRequirementsForNamedParticipants(eventId, namedParticipantIds).orDie()
                .map { records -> records.map { it.participantRequirement }.toSet() }

            val split = MyEventLogic.split(
                entries = !toRawMatches(matchRecords, substitutionRecords, participantId),
                now = now,
                visibility = visibility,
                // Das persönliche Dashboard zeigt immer einen Countdown. Die Athleten-Anzeige darf
                // ihn abschalten, weil sie an der Wand hängt und ruhig bleiben soll; auf dem
                // eigenen Telefon ist "in 12 Minuten" genau die Auskunft, für die man hinsieht.
                showCountdown = AthleteBoardLogic.DEFAULT_SHOW_COUNTDOWN,
            )

            // Bezugsgröße für die Erledigungsfenster der Bedingungen: die Fenster hängen am
            // ersten künftigen Start, und den kennt erst die fertige Aufteilung.
            val firstFutureStart = MyEventLogic.firstFutureStart(split.upcoming, now)

            val dto = MyEventDto(
                displayName = listOfNotNull(
                    person?.get(PARTICIPANT.FIRSTNAME),
                    person?.get(PARTICIPANT.LASTNAME),
                ).joinToString(" "),
                // Gaststarter tragen ihren echten Verein im Freitextfeld; der Verein aus der
                // Stammdatentabelle ist bei ihnen nur der meldende.
                clubName = person?.get(PARTICIPANT.EXTERNAL_CLUB_NAME)
                    ?: person?.get("club_name", String::class.java),
                eventName = eventName ?: "",
                serverTime = now,
                refreshIntervalSeconds = REFRESH_INTERVAL_SECONDS,
                running = split.running,
                upcoming = split.upcoming,
                results = split.results,
                unscheduled = toRegistrations(registrationRecords),
                requirements = requirementRecords
                    // Nur Bedingungen, die für diese Person überhaupt gelten, und davon nur die
                    // ausdrücklich freigegebenen. Die Freitext-Notiz dazu wird nicht einmal
                    // geladen, siehe MyEventRepo.findFulfilledRequirementIds. Nach außen geht
                    // der öffentliche Text `publicNote` — die interne `description` bleibt seit
                    // dem 11.08.2026 im Haus, siehe MyEventRequirementDto.
                    .filter { applicableRequirementIds.contains(it.id) }
                    .filter { it.publiclyVisible == true }
                    .map {
                        MyEventRequirementDto(
                            id = it.id!!,
                            name = it.name ?: "",
                            publicNote = it.publicNote,
                            optional = it.optional == true,
                            fulfilled = fulfilledRequirementIds.contains(it.id),
                            checkFrom = MyEventLogic.checkWindowBound(
                                firstFutureStart,
                                it.checkEarliestMinutesBefore,
                            ),
                            checkUntil = MyEventLogic.checkWindowBound(
                                firstFutureStart,
                                it.checkLatestMinutesBefore,
                            ),
                        )
                    }
                    .sortedBy { it.name },
                notice = notice,
            )

            // Rechnen zwei Abrufe gleichzeitig, gewinnt der letzte - bei Millisekunden Rechenzeit
            // kein Grund für ein Lock, genau wie beim Zwischenspeicher der Athleten-Anzeige.
            if (cache.size >= CACHE_CLEANUP_THRESHOLD) {
                cache.values.removeIf { !AthleteBoardLogic.isCacheFresh(it.builtAt, now) }
            }
            cache[key] = CachedMyEvent(now, marker, dto)

            KIO.ok(ApiResponse.Dto(dto))
        }

    /** Die Paare aus Meldung und Runde, zu denen Auswechslungen geladen werden müssen. */
    private fun registrationRounds(records: List<Record>): List<Pair<UUID, UUID>> =
        records.mapNotNull { record ->
            val registrationId = record.get("registration_id", UUID::class.java)
            val roundId = record.get("round_id", UUID::class.java)
            if (registrationId != null && roundId != null) registrationId to roundId else null
        }.distinct()

    /**
     * Die Zeilen aus [MyEventRepo.findMatchesForParticipant] kommen je Lauf mehrfach - einmal pro
     * Mitglied der **gemeldeten** Mannschaft. Hier werden sie wieder zu einem Lauf, die
     * Auswechslungen der Runde werden darauf angewandt, und was übrig bleibt, ist die Aufstellung
     * dieses Laufs.
     *
     * Läufe, in denen die Person danach nicht mehr steht, fallen weg: sie ist ausgewechselt
     * worden, und der Lauf gehört ihr nicht mehr. Umgekehrt bleiben Läufe stehen, in denen sie gar
     * nicht gemeldet, aber eingewechselt ist.
     */
    private fun toRawMatches(
        records: List<Record>,
        substitutions: List<SubstitutionViewRecord>,
        participantId: UUID,
    ): App<Nothing, List<MyEventLogic.RawMatch>> = KIO.comprehension {
        val matches = !records.groupBy { it[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!! }
            .toList()
            .traverse { (matchId, rows) ->
                toRawMatchOrNull(matchId, rows, substitutions, participantId)
            }
        KIO.ok(matches.filterNotNull())
    }

    private fun toRawMatchOrNull(
        matchId: UUID,
        rows: List<Record>,
        substitutions: List<SubstitutionViewRecord>,
        participantId: UUID,
    ): App<Nothing, MyEventLogic.RawMatch?> = KIO.comprehension {
        val first = rows.first()
        val registrationId = first.get("registration_id", UUID::class.java)
        val roundId = first.get("round_id", UUID::class.java)

        // Dieselbe Auflösung wie in Durchführung, Startliste und Scan-Übersicht. Die Ketten und
        // der Tausch über zwei Boote sind heikel genug, dass ein zweiter Nachbau hier über kurz
        // oder lang eine andere Aufstellung zeigen würde als die Startliste.
        val lineup = !CompetitionExecutionService.getActuallyParticipatingParticipants(
            teamParticipants = registeredCrew(rows),
            // Auf dieses Boot und diese Runde eingeschränkt - so wie es auch
            // SubstitutionService und ParticipantTrackingService tun. Ein Tausch besteht aus zwei
            // Zeilen, je einer pro Boot; jede Seite sieht nur ihre eigene und darf die andere
            // Person deshalb folgerichtig als gegangen führen.
            substitutionsForRegistration = substitutions.filter {
                it.competitionRegistrationId == registrationId && it.competitionSetupRoundId == roundId
            },
        )

        if (lineup.none { it.id == participantId }) {
            return@comprehension KIO.ok(null)
        }

        KIO.ok(toRawMatch(matchId, first, rows, lineup, participantId))
    }

    private fun toRawMatch(
        matchId: UUID,
        first: Record,
        rows: List<Record>,
        lineup: List<ParticipantForExecutionDto>,
        participantId: UUID,
    ): MyEventLogic.RawMatch {
        // Einschränkung: die Zeilen hier sind die Mitglieder des eigenen Bootes und tragen
        // alle denselben Timecode. Die Funktion sieht also n-mal denselben Wert und
        // liefert daher immer die gröbste Stufe. Die Athleten-Anzeige geht bei knappen
        // Zeiten feiner auf, weil sie alle Boote des Laufs vor sich hat - dafür müssten
        // hier auch die fremden Zeiten des Laufs geladen werden, was die Abfrage um eine
        // Ebene aufbläht, die für "meine Zeit" niemand braucht.
        val precision = Timecode.displayPrecision(rows.mapNotNull { it[TIMECODE.TIME] })

        return MyEventLogic.RawMatch(
            matchId = matchId,
            teamId = first.get("registration_id", UUID::class.java),
            competitionName = first.get("competition_name", String::class.java) ?: "",
            categoryName = first.get("category_name", String::class.java),
            roundName = first.get("round_name", String::class.java),
            matchName = first.get("match_name", String::class.java),
            startTime = first[COMPETITION_MATCH.START_TIME],
            actualStartTime = first[COMPETITION_MATCH.STARTED_AT],
            finishedAt = first[COMPETITION_MATCH.FINISHED_AT],
            allTeamsScored = first.get("all_teams_scored", Boolean::class.java) == true,
            // `currently_running` ist mit V202608091400 zu `activated_at` geworden:
            // dieselbe Aussage („vom Schiedsrichter an den Start gerufen"), nur als
            // Zeitstempel statt als Merker. Für die Aufteilung laufend/kommend zählt
            // weiterhin nur, ob der Lauf gerufen wurde.
            currentlyRunning = first[COMPETITION_MATCH.ACTIVATED_AT] != null,
            lane = first[COMPETITION_MATCH_TEAM.START_NUMBER],
            teamName = first.get("team_name", String::class.java),
            clubName = first.get("club_name", String::class.java),
            // Nach Nachnamen sortiert wie zuvor - die Reihenfolge kam bis hierher aus dem
            // `order by` der Abfrage, die Eingewechselten stehen dort aber nicht drin.
            teamMembers = lineup
                .sortedWith(compareBy({ it.lastName }, { it.firstName }))
                .map {
                    MyEventTeamMemberDto(
                        name = "${it.firstName} ${it.lastName}",
                        role = it.namedParticipantName,
                        self = it.id == participantId,
                    )
                },
            place = first[COMPETITION_MATCH_TEAM.PLACE],
            timeString = first[TIMECODE.TIME]?.let {
                Timecode(
                    millis = it,
                    baseUnit = Timecode.BaseUnit.valueOf(first[TIMECODE.BASE_UNIT]!!),
                    millisecondPrecision = precision,
                ).toString()
            },
            penaltySeconds = first[COMPETITION_MATCH_TEAM.PENALTY_SECONDS],
            penaltyNote = first[COMPETITION_MATCH_TEAM.PENALTY_NOTE],
            failed = first[COMPETITION_MATCH_TEAM.FAILED] == true,
            failedReason = first[COMPETITION_MATCH_TEAM.FAILED_REASON],
            bye = first[COMPETITION_MATCH_TEAM.BYE] == true,
            deregistered = first.get("deregistered", Boolean::class.java) == true,
            deregisteredReason = first.get("deregistration_reason", String::class.java),
        )
    }

    /**
     * Die gemeldete Mannschaft eines Bootes aus den Zeilen eines Laufs - der Ausgangspunkt, auf
     * den die Auswechslungen angewandt werden.
     *
     * Jahrgang, Geschlecht und Verein trägt der Dto nur mit, weil die geteilte Auflösung sie
     * verlangt; in der Antwort landet nichts davon. Zeilen ohne Person entstehen durch die
     * äußeren Verbunde der Abfrage und fallen hier weg.
     */
    private fun registeredCrew(rows: List<Record>): List<ParticipantForExecutionDto> =
        rows.mapNotNull { row ->
            val memberId = row.get("participant_id", UUID::class.java) ?: return@mapNotNull null
            val roleId = row.get("named_role_id", UUID::class.java) ?: return@mapNotNull null
            ParticipantForExecutionDto(
                id = memberId,
                namedParticipantId = roleId,
                namedParticipantName = row.get("named_role", String::class.java) ?: "",
                firstName = row[PARTICIPANT.FIRSTNAME] ?: "",
                lastName = row[PARTICIPANT.LASTNAME] ?: "",
                year = row[PARTICIPANT.YEAR] ?: 0,
                gender = row[PARTICIPANT.GENDER] ?: Gender.D,
                clubId = row.get("registration_club_id", UUID::class.java)!!,
                clubName = row.get("club_name", String::class.java) ?: "",
                competitionRegistrationId = row.get("registration_id", UUID::class.java)!!,
                competitionRegistrationName = row.get("team_name", String::class.java),
                external = row[PARTICIPANT.EXTERNAL],
                externalClubName = row[PARTICIPANT.EXTERNAL_CLUB_NAME],
            )
        }.distinctBy { it.id to it.namedParticipantId }

    private fun toRegistrations(records: List<Record>): List<MyEventRegistrationDto> =
        records.mapNotNull { record ->
            record.get("competition_id", UUID::class.java)?.let { competitionId ->
                MyEventRegistrationDto(
                    competitionId = competitionId,
                    competitionIdentifier = record[COMPETITION_PROPERTIES.IDENTIFIER] ?: "",
                    competitionName = record.get("competition_name", String::class.java) ?: "",
                    categoryName = record.get("category_name", String::class.java),
                    teamName = record.get("team_name", String::class.java),
                    role = record.get("named_role", String::class.java),
                )
            }
        }
}
