package de.lambda9.ready2race.backend.app.eventInfo.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.eventInfo.control.MyEventRepo
import de.lambda9.ready2race.backend.app.eventInfo.entity.EventInfoProblem
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventDto
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventRegistrationDto
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventRequirementDto
import de.lambda9.ready2race.backend.app.eventInfo.entity.MyEventTeamMemberDto
import de.lambda9.ready2race.backend.app.participantRequirement.control.ParticipantRequirementForEventRepo
import de.lambda9.ready2race.backend.app.qrCodeApp.control.QrCodeRepo
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.data.Timecode
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_MATCH_TEAM
import de.lambda9.ready2race.backend.database.generated.tables.references.COMPETITION_PROPERTIES
import de.lambda9.ready2race.backend.database.generated.tables.references.PARTICIPANT
import de.lambda9.ready2race.backend.database.generated.tables.references.TIMECODE
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.orDie
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

    private data class CachedMyEvent(val builtAt: LocalDateTime, val dto: MyEventDto)

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
            val cached = cache[key]?.takeIf { AthleteBoardLogic.isCacheFresh(it.builtAt, now) }
            if (cached != null) {
                return@comprehension KIO.ok(ApiResponse.Dto(cached.dto.copy(serverTime = now)))
            }

            // Nach der Existenzprüfung oben kann der Name nicht mehr fehlen; die Abfrage verliert
            // auf dem Weg nur die Zusage darauf.
            val eventName = !EventRepo.getName(eventId).orDie()
            val visibility = !EventRepo.getPublicResultsVisibility(eventId).orDie()

            val person = !MyEventRepo.findParticipant(participantId).orDie()
            val matchRecords = !MyEventRepo.findMatchesForParticipant(eventId, participantId).orDie()
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
                entries = toRawMatches(matchRecords, participantId),
                now = now,
                visibility = visibility,
                // Das persönliche Dashboard zeigt immer einen Countdown. Die Athleten-Anzeige darf
                // ihn abschalten, weil sie an der Wand hängt und ruhig bleiben soll; auf dem
                // eigenen Telefon ist "in 12 Minuten" genau die Auskunft, für die man hinsieht.
                showCountdown = AthleteBoardLogic.DEFAULT_SHOW_COUNTDOWN,
            )

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
                    // geladen, siehe MyEventRepo.findFulfilledRequirementIds.
                    .filter { applicableRequirementIds.contains(it.id) }
                    .filter { it.publiclyVisible == true }
                    .map {
                        MyEventRequirementDto(
                            id = it.id!!,
                            name = it.name ?: "",
                            description = it.description,
                            optional = it.optional == true,
                            fulfilled = fulfilledRequirementIds.contains(it.id),
                        )
                    }
                    .sortedBy { it.name },
            )

            // Rechnen zwei Abrufe gleichzeitig, gewinnt der letzte - bei Millisekunden Rechenzeit
            // kein Grund für ein Lock, genau wie beim Zwischenspeicher der Athleten-Anzeige.
            if (cache.size >= CACHE_CLEANUP_THRESHOLD) {
                cache.values.removeIf { !AthleteBoardLogic.isCacheFresh(it.builtAt, now) }
            }
            cache[key] = CachedMyEvent(now, dto)

            KIO.ok(ApiResponse.Dto(dto))
        }

    /**
     * Die Zeilen aus [MyEventRepo.findMatchesForParticipant] kommen je Lauf mehrfach - einmal pro
     * Mitglied der eigenen Mannschaft. Hier werden sie wieder zu einem Lauf mit Aufstellung.
     */
    private fun toRawMatches(records: List<Record>, participantId: UUID): List<MyEventLogic.RawMatch> =
        records.groupBy { it[COMPETITION_MATCH.COMPETITION_SETUP_MATCH]!! }
            .map { (matchId, rows) ->
                val first = rows.first()
                // Einschränkung: die Zeilen hier sind die Mitglieder des eigenen Bootes und tragen
                // alle denselben Timecode. Die Funktion sieht also n-mal denselben Wert und
                // liefert daher immer die gröbste Stufe. Die Athleten-Anzeige geht bei knappen
                // Zeiten feiner auf, weil sie alle Boote des Laufs vor sich hat - dafür müssten
                // hier auch die fremden Zeiten des Laufs geladen werden, was die Abfrage um eine
                // Ebene aufbläht, die für "meine Zeit" niemand braucht.
                val precision = Timecode.displayPrecision(rows.mapNotNull { it[TIMECODE.TIME] })

                MyEventLogic.RawMatch(
                    matchId = matchId,
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
                    teamMembers = rows.mapNotNull { row ->
                        row.get("participant_id", UUID::class.java)?.let { memberId ->
                            MyEventTeamMemberDto(
                                name = listOfNotNull(row[PARTICIPANT.FIRSTNAME], row[PARTICIPANT.LASTNAME])
                                    .joinToString(" "),
                                role = row.get("named_role", String::class.java),
                                self = memberId == participantId,
                            )
                        }
                    }.distinct(),
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
                    deregistered = first.get("deregistered", Boolean::class.java) == true,
                    deregisteredReason = first.get("deregistration_reason", String::class.java),
                )
            }

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
