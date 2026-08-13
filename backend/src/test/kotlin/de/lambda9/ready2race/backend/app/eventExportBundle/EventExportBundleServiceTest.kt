package de.lambda9.ready2race.backend.app.eventExportBundle

import de.lambda9.ready2race.backend.app.JEnv
import de.lambda9.ready2race.backend.app.eventDocument.boundary.EventDocumentService
import de.lambda9.ready2race.backend.app.eventExportBundle.boundary.EventExportBundleService
import de.lambda9.ready2race.backend.app.eventExportBundle.entity.AddEventExportBundleItemRequest
import de.lambda9.ready2race.backend.app.eventExportBundle.entity.EventExportBundleError
import de.lambda9.ready2race.backend.app.eventExportBundle.entity.EventExportBundleItemDto
import de.lambda9.ready2race.backend.app.eventExportBundle.entity.EventExportBundleItemKind
import de.lambda9.ready2race.backend.app.eventExportBundle.entity.EventExportBundleOrderRequest
import de.lambda9.ready2race.backend.calls.responses.ApiResponse
import de.lambda9.ready2race.backend.database.SYSTEM_USER
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDocumentDataRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventDocumentRecord
import de.lambda9.ready2race.backend.database.generated.tables.records.EventRecord
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DOCUMENT
import de.lambda9.ready2race.backend.database.generated.tables.references.EVENT_DOCUMENT_DATA
import de.lambda9.ready2race.backend.database.insert
import de.lambda9.ready2race.testing.kio.TestComprehensionScope
import de.lambda9.ready2race.testing.testComprehension
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Die Export-Mappe gegen eine echte Datenbank: fauler Platzhalter, Hinzufügen (samt Duplikat- und
 * Fremd-Dokument-Regeln), Umsortieren (vollständige Liste oder gar nicht), Löschen (Platzhalter
 * nie) und das Verschwinden eines Eintrags mit seinem gelöschten Dokument (on delete cascade).
 */
class EventExportBundleServiceTest {

    private val now: LocalDateTime = LocalDateTime.of(2026, 8, 14, 10, 0)

    private fun TestComprehensionScope<JEnv>.insertEvent(): UUID {
        val eventId = UUID.randomUUID()
        !EVENT.insert(
            EventRecord(
                id = eventId,
                name = "Testregatta",
                createdAt = now,
                updatedAt = now,
            )
        )
        return eventId
    }

    private fun TestComprehensionScope<JEnv>.insertDocument(
        eventId: UUID,
        name: String,
        bytes: ByteArray = byteArrayOf(1, 2, 3),
    ): UUID {
        val documentId = UUID.randomUUID()
        !EVENT_DOCUMENT.insert(
            EventDocumentRecord(
                id = documentId,
                event = eventId,
                name = name,
                createdAt = now,
                updatedAt = now,
            )
        )
        !EVENT_DOCUMENT_DATA.insert(
            EventDocumentDataRecord(
                eventDocument = documentId,
                data = bytes,
            )
        )
        return documentId
    }

    private fun TestComprehensionScope<JEnv>.bundle(eventId: UUID): List<EventExportBundleItemDto> {
        val response = !EventExportBundleService.getBundle(eventId)
        @Suppress("UNCHECKED_CAST")
        return (response as ApiResponse.ListDto<EventExportBundleItemDto>).data
    }

    @Test
    fun firstAccessCreatesThePlaceholderLastAndKeepsItSingle() = testComprehension {
        val eventId = insertEvent()

        val first = bundle(eventId)
        assertEquals(listOf(EventExportBundleItemKind.GENERATED_STARTLISTS), first.map { it.kind })

        // Der zweite Zugriff legt KEINEN zweiten Platzhalter an.
        val second = bundle(eventId)
        assertEquals(first.map { it.id }, second.map { it.id })
    }

    @Test
    fun addedDocumentsAppendAfterThePlaceholderAndCarryTheirName() = testComprehension {
        val eventId = insertEvent()
        val docA = insertDocument(eventId, "Hinweise.pdf")
        val docB = insertDocument(eventId, "Regelwerk.pdf")

        !EventExportBundleService.addDocument(eventId, AddEventExportBundleItemRequest(docA), SYSTEM_USER)
        !EventExportBundleService.addDocument(eventId, AddEventExportBundleItemRequest(docB), SYSTEM_USER)

        val items = bundle(eventId)
        // ensurePlaceholder läuft in addDocument VOR dem Anfügen: Der Platzhalter existiert
        // deshalb schon beim ersten Dokument, neue Dokumente landen hinter ihm - genau die
        // Zusage "ein angefügtes Dokument darf HINTER den Startlisten stehen" aus dem Service.
        assertEquals(
            listOf(
                EventExportBundleItemKind.GENERATED_STARTLISTS,
                EventExportBundleItemKind.DOCUMENT,
                EventExportBundleItemKind.DOCUMENT,
            ),
            items.map { it.kind },
        )
        assertEquals(listOf(null, "Hinweise.pdf", "Regelwerk.pdf"), items.map { it.documentName })
        assertEquals(listOf(null, docA, docB), items.map { it.document })
    }

    @Test
    fun addingRejectsDuplicatesAndForeignDocuments() = testComprehension {
        val eventId = insertEvent()
        val otherEventId = insertEvent()
        val doc = insertDocument(eventId, "Hinweise.pdf")
        val foreign = insertDocument(otherEventId, "Fremd.pdf")

        !EventExportBundleService.addDocument(eventId, AddEventExportBundleItemRequest(doc), SYSTEM_USER)

        assertKIOFails(EventExportBundleError.DocumentAlreadyInBundle) {
            EventExportBundleService.addDocument(eventId, AddEventExportBundleItemRequest(doc), SYSTEM_USER)
        }
        assertKIOFails(EventExportBundleError.DocumentNotFound) {
            EventExportBundleService.addDocument(eventId, AddEventExportBundleItemRequest(foreign), SYSTEM_USER)
        }
        assertKIOFails(EventExportBundleError.DocumentNotFound) {
            EventExportBundleService.addDocument(
                eventId,
                AddEventExportBundleItemRequest(UUID.randomUUID()),
                SYSTEM_USER,
            )
        }
    }

    @Test
    fun reorderWritesTheCompleteNewOrderAndRejectsPartialOrForeignLists() = testComprehension {
        val eventId = insertEvent()
        val docA = insertDocument(eventId, "A.pdf")
        val docB = insertDocument(eventId, "B.pdf")
        !EventExportBundleService.addDocument(eventId, AddEventExportBundleItemRequest(docA), SYSTEM_USER)
        !EventExportBundleService.addDocument(eventId, AddEventExportBundleItemRequest(docB), SYSTEM_USER)

        val before = bundle(eventId)
        // Platzhalter ans Ende: [Platzhalter, A, B] -> [A, Platzhalter, B] -> hier: volle Liste.
        val newOrder = listOf(before[1].id, before[0].id, before[2].id)
        !EventExportBundleService.reorder(eventId, EventExportBundleOrderRequest(newOrder), SYSTEM_USER)

        assertEquals(newOrder, bundle(eventId).map { it.id })

        // Unvollständig oder mit fremder Id: 409, nichts wird geschrieben.
        assertKIOFails(EventExportBundleError.OrderMismatch) {
            EventExportBundleService.reorder(
                eventId,
                EventExportBundleOrderRequest(newOrder.drop(1)),
                SYSTEM_USER,
            )
        }
        assertKIOFails(EventExportBundleError.OrderMismatch) {
            EventExportBundleService.reorder(
                eventId,
                EventExportBundleOrderRequest(newOrder.dropLast(1) + UUID.randomUUID()),
                SYSTEM_USER,
            )
        }
        assertEquals(newOrder, bundle(eventId).map { it.id })
    }

    @Test
    fun removeDeletesDocumentsButNeverThePlaceholder() = testComprehension {
        val eventId = insertEvent()
        val doc = insertDocument(eventId, "A.pdf")
        !EventExportBundleService.addDocument(eventId, AddEventExportBundleItemRequest(doc), SYSTEM_USER)

        val items = bundle(eventId)
        val placeholder = items.single { it.kind == EventExportBundleItemKind.GENERATED_STARTLISTS }
        val documentItem = items.single { it.kind == EventExportBundleItemKind.DOCUMENT }

        assertKIOFails(EventExportBundleError.PlaceholderNotRemovable) {
            EventExportBundleService.removeItem(eventId, placeholder.id)
        }

        !EventExportBundleService.removeItem(eventId, documentItem.id)
        assertEquals(listOf(placeholder.id), bundle(eventId).map { it.id })

        // Ein Eintrag einer ANDEREN Veranstaltung ist von hier aus nicht löschbar.
        val otherEventId = insertEvent()
        val otherDoc = insertDocument(otherEventId, "B.pdf")
        !EventExportBundleService.addDocument(otherEventId, AddEventExportBundleItemRequest(otherDoc), SYSTEM_USER)
        val otherItem = bundle(otherEventId).single { it.kind == EventExportBundleItemKind.DOCUMENT }
        assertKIOFails(EventExportBundleError.ItemNotFound) {
            EventExportBundleService.removeItem(eventId, otherItem.id)
        }
    }

    @Test
    fun deletingTheDocumentRemovesItsBundleEntry() = testComprehension {
        val eventId = insertEvent()
        val doc = insertDocument(eventId, "A.pdf")
        !EventExportBundleService.addDocument(eventId, AddEventExportBundleItemRequest(doc), SYSTEM_USER)
        assertEquals(2, bundle(eventId).size)

        // Löschen über den Dokument-Service, wie es die Dokumenttabelle tut - der Mappen-Eintrag
        // verschwindet mit (on delete cascade), es bleibt kein Loch für den Zusammenbau.
        !EventDocumentService.deleteDocument(doc)

        val remaining = bundle(eventId)
        assertEquals(1, remaining.size)
        assertTrue(remaining.single().kind == EventExportBundleItemKind.GENERATED_STARTLISTS)
    }
}
