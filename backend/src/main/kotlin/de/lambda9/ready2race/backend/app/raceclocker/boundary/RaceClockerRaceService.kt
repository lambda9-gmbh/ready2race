package de.lambda9.ready2race.backend.app.raceclocker.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerFeed
import de.lambda9.ready2race.backend.app.raceclocker.control.RaceClockerRaceRepo
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceDto
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceError
import de.lambda9.ready2race.backend.app.raceclocker.entity.RaceClockerRaceRequest
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.delete
import de.lambda9.ready2race.backend.database.generated.tables.records.RaceclockerRaceRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.RACECLOCKER_RACE
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.backend.database.update
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrNull
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.UUID

object RaceClockerRaceService {

    fun getRaces(eventId: UUID): App<ServiceError, ApiResponse.ListDto<RaceClockerRaceDto>> =
        KIO.comprehension {
            val races = !RaceClockerRaceRepo.getForEvent(eventId).orDie()
            KIO.ok(ApiResponse.ListDto(races))
        }

    fun addRace(
        eventId: UUID,
        userId: UUID,
        request: RaceClockerRaceRequest,
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {
        // Normalisiert gespeichert (Schema ergänzt, http auf https gehoben), damit der Tab hinterher
        // zeigt, was der Abruf tatsächlich anfragt — dieselbe Behandlung wie zuvor an den beiden
        // Adressfeldern. Die Host-Allowlist darin ist zugleich das, was diesen Endpunkt davon
        // abhält, ein SSRF-Hebel zu sein: die Adresse kommt vom Bediener.
        // Vor dem Einfuegen geprueft, damit eine unbekannte Veranstaltung als 404 endet und nicht
        // als Fremdschluessel-Defekt, den die Oberflaeche nur als "Unerwarteter Fehler" zeigen kann.
        !EventRepo.get(eventId).orDie().onNullFail { EventError.NotFound }

        val url = (!RaceClockerFeed.normalizeUrl(request.resultsUrl.trim())).toString()
        val name = request.name.trim()

        !ensureFree(eventId, name, url, exceptRaceId = null)

        val position = !RaceClockerRaceRepo.nextPosition(eventId).orDie()
        val raceId = UUID.randomUUID()
        val now = LocalDateTime.now()

        !RACECLOCKER_RACE.insert(
            RaceclockerRaceRecord(
                id = raceId,
                event = eventId,
                name = name,
                resultsUrl = url,
                startMode = request.startMode.name,
                capturesLaps = request.capturesLaps,
                position = position,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        ).orDie()

        KIO.ok(ApiResponse.Created(raceId))
    }

    fun updateRace(
        eventId: UUID,
        raceId: UUID,
        userId: UUID,
        request: RaceClockerRaceRequest,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        val url = (!RaceClockerFeed.normalizeUrl(request.resultsUrl.trim())).toString()
        val name = request.name.trim()

        !ensureFree(eventId, name, url, exceptRaceId = raceId)

        !RACECLOCKER_RACE.update({
            this.name = name
            resultsUrl = url
            startMode = request.startMode.name
            capturesLaps = request.capturesLaps
            updatedBy = userId
            updatedAt = LocalDateTime.now()
        }) {
            ID.eq(raceId).and(EVENT.eq(eventId))
        }.orDie().onNullFail { RaceClockerRaceError.NotFound }

        noData
    }

    /**
     * Löschen entwertet die Anwahl, statt sie zu blockieren (`on delete set null` in der Migration).
     * Ein Wettkampf, der auf das gelöschte Rennen zeigte, erbt danach wieder die Voreinstellung der
     * Veranstaltung — und ein Lauf ohne jedes Rennen wird vom Abruf still übersprungen.
     */
    fun deleteRace(eventId: UUID, raceId: UUID): App<ServiceError, ApiResponse.NoData> =
        KIO.comprehension {
            val deleted = !RACECLOCKER_RACE.delete { ID.eq(raceId).and(EVENT.eq(eventId)) }.orDie()
            if (deleted == 0) return@comprehension KIO.fail(RaceClockerRaceError.NotFound)
            noData
        }

    /** Name und Adresse sind je Veranstaltung eindeutig; beides fällt hier auf, nicht erst als 500er. */
    private fun ensureFree(
        eventId: UUID,
        name: String,
        url: String,
        exceptRaceId: UUID?,
    ): App<ServiceError, Unit> = KIO.comprehension {
        val races = !RaceClockerRaceRepo.getForEvent(eventId).orDie()
        val others = races.filter { it.id != exceptRaceId }

        if (others.any { it.name.equals(name, ignoreCase = true) }) {
            return@comprehension KIO.fail(RaceClockerRaceError.NameTaken)
        }
        // Beide Seiten normalisiert verglichen: Der Backfill hat die Altadressen wörtlich
        // übernommen, und die trugen durchweg `www.`. Neue Adressen faltet `normalizeUrl` auf den
        // Apex. Ohne diesen Schritt entstünden aus „derselben" Adresse zwei Rennen mit einer
        // Antwort — zwei Abrufe je Takt für dasselbe Ergebnis.
        val existing = others.mapNotNull { race ->
            RaceClockerFeed.normalizeUrl(race.resultsUrl).unsafeRunSync().getOrNull()?.toString()
        }
        if (url in existing) {
            return@comprehension KIO.fail(RaceClockerRaceError.UrlTaken)
        }
        KIO.ok(Unit)
    }
}
