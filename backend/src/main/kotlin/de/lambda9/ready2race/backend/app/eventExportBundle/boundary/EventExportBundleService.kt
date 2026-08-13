package de.lambda9.ready2race.backend.app.eventExportBundle.boundary

import de.lambda9.ready2race.backend.app.App
import de.lambda9.ready2race.backend.app.ServiceError
import de.lambda9.ready2race.backend.app.event.control.EventRepo
import de.lambda9.ready2race.backend.app.event.entity.EventError
import de.lambda9.ready2race.backend.app.eventDocument.control.EventDocumentRepo
import de.lambda9.ready2race.backend.app.eventExportBundle.control.EventExportBundleItemRepo
import de.lambda9.ready2race.backend.app.eventExportBundle.entity.AddEventExportBundleItemRequest
import de.lambda9.ready2race.backend.app.eventExportBundle.entity.EventExportBundleError
import de.lambda9.ready2race.backend.app.eventExportBundle.entity.EventExportBundleItemDto
import de.lambda9.ready2race.backend.app.eventExportBundle.entity.EventExportBundleItemKind
import de.lambda9.ready2race.backend.app.eventExportBundle.entity.EventExportBundleOrderRequest
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.calls.responses.ApiResponse.Companion.noData
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.EventExportBundleItemRecord
import de.lambda9.ready2race.backend.kio.onFalseFail
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.onNullFail
import de.lambda9.tailwind.core.extensions.kio.orDie
import java.time.LocalDateTime
import java.util.UUID

/**
 * Die Export-Mappe einer Veranstaltung: eine sortierte Liste aus hochgeladenen Dokumenten und
 * GENAU EINEM Platzhalter für die generierten Startlisten. Der PDF-Sammelexport am Zeitplan
 * hängt die Einträge in dieser Reihenfolge zusammen - wie das handgebaute Meldeergebnis-Dokument
 * der Vorjahre (Hinweise und Regelwerk vorn, Startlisten an der Platzhalter-Position).
 */
object EventExportBundleService {

    /**
     * Die Mappe in Reihenfolge - legt den Startlisten-Platzhalter beim ersten Zugriff an
     * (Default: ganz hinten). Faul statt bei der Veranstaltungsanlage, damit auch alle
     * BESTEHENDEN Veranstaltungen ihre Mappe bekommen, ohne dass eine Migration Daten erfinden
     * muss; der partielle Unique-Index hält den Platzhalter auch bei parallelen Zugriffen einzig.
     */
    fun getBundle(
        eventId: UUID,
    ): App<ServiceError, ApiResponse.ListDto<EventExportBundleItemDto>> = KIO.comprehension {
        !EventRepo.exists(eventId).orDie().onFalseFail { EventError.NotFound }
        !ensurePlaceholder(eventId)

        val items = !EventExportBundleItemRepo.allForEvent(eventId).orDie()

        // Die Dokumentnamen für die Anzeige - EIN Nachschlag über die Veranstaltung statt je Zeile.
        val documents = !EventDocumentRepo.getByEventIds(listOf(eventId)).orDie()
        val nameById = documents.associate { it.id to it.name }

        KIO.ok(
            ApiResponse.ListDto(
                items.map { item ->
                    EventExportBundleItemDto(
                        id = item.id,
                        kind = EventExportBundleItemKind.valueOf(item.kind),
                        document = item.document,
                        documentName = item.document?.let { nameById[it] },
                    )
                }
            )
        )
    }

    fun addDocument(
        eventId: UUID,
        request: AddEventExportBundleItemRequest,
        userId: UUID,
    ): App<ServiceError, ApiResponse.Created> = KIO.comprehension {
        !EventRepo.exists(eventId).orDie().onFalseFail { EventError.NotFound }
        // Der Platzhalter zuerst: Ein hinten angefügtes Dokument soll HINTER ihm landen dürfen -
        // wäre er noch nicht angelegt, entstünde er später hinter dem Dokument, obwohl die
        // Oberfläche ihn schon am Ende gezeigt hätte.
        !ensurePlaceholder(eventId)

        val document = !EventDocumentRepo.get(request.document).orDie()
            .onNullFail { EventExportBundleError.DocumentNotFound }
        !KIO.failOn(document.event != eventId) { EventExportBundleError.DocumentNotFound }

        val existing = !EventExportBundleItemRepo.allForEvent(eventId).orDie()
        !KIO.failOn(existing.any { it.document == request.document }) {
            EventExportBundleError.DocumentAlreadyInBundle
        }

        val now = LocalDateTime.now()
        val position = !EventExportBundleItemRepo.nextPosition(eventId).orDie()
        val id = !EventExportBundleItemRepo.create(
            EventExportBundleItemRecord(
                id = UUID.randomUUID(),
                event = eventId,
                position = position,
                kind = EventExportBundleItemKind.DOCUMENT.name,
                document = request.document,
                createdAt = now,
                createdBy = userId,
                updatedAt = now,
                updatedBy = userId,
            )
        ).orDie()

        KIO.ok(ApiResponse.Created(id))
    }

    fun removeItem(
        eventId: UUID,
        itemId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        val item = !EventExportBundleItemRepo.get(itemId).orDie()
            .onNullFail { EventExportBundleError.ItemNotFound }
        !KIO.failOn(item.event != eventId) { EventExportBundleError.ItemNotFound }
        !KIO.failOn(item.kind == EventExportBundleItemKind.GENERATED_STARTLISTS.name) {
            EventExportBundleError.PlaceholderNotRemovable
        }

        !EventExportBundleItemRepo.delete(itemId).orDie()
        noData
    }

    fun reorder(
        eventId: UUID,
        request: EventExportBundleOrderRequest,
        userId: UUID,
    ): App<ServiceError, ApiResponse.NoData> = KIO.comprehension {
        val current = !EventExportBundleItemRepo.allForEvent(eventId).orDie()

        // Genau die aktuellen Einträge, jede genau einmal - sonst schriebe ein Tab mit altem
        // Stand eine Mappe, die niemand so bestellt hat.
        !KIO.failOn(
            request.itemIds.size != current.size ||
                request.itemIds.toSet() != current.map { it.id }.toSet()
        ) { EventExportBundleError.OrderMismatch }

        !EventExportBundleItemRepo.writeOrder(request.itemIds, userId, LocalDateTime.now()).orDie()
        noData
    }

    /**
     * Legt den Startlisten-Platzhalter an, falls er fehlt - als System-Eintrag, denn er ist
     * Buchhaltung der Mappe, keine Nutzeraktion. Ein paralleler zweiter Anlauf scheitert am
     * partiellen Unique-Index; das fängt der Aufrufer nicht ab, weil zwei GLEICHZEITIGE erste
     * Zugriffe auf dieselbe frische Mappe praktisch nicht vorkommen und der zweite Request nach
     * einem Neuladen sauber durchläuft.
     */
    private fun ensurePlaceholder(eventId: UUID): App<Nothing, Unit> = KIO.comprehension {
        val items = !EventExportBundleItemRepo.allForEvent(eventId).orDie()
        if (items.none { it.kind == EventExportBundleItemKind.GENERATED_STARTLISTS.name }) {
            val now = LocalDateTime.now()
            val position = !EventExportBundleItemRepo.nextPosition(eventId).orDie()
            !EventExportBundleItemRepo.create(
                EventExportBundleItemRecord(
                    id = UUID.randomUUID(),
                    event = eventId,
                    position = position,
                    kind = EventExportBundleItemKind.GENERATED_STARTLISTS.name,
                    document = null,
                    createdAt = now,
                    createdBy = SYSTEM_USER,
                    updatedAt = now,
                    updatedBy = SYSTEM_USER,
                )
            ).orDie()
        }
        KIO.unit
    }
}
