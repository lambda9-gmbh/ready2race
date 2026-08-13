package de.lambda9.ready2race.backend.app.eventExportBundle.entity

import java.util.UUID

/**
 * Ein Eintrag der Export-Mappe, in Mappen-Reihenfolge geliefert. [document] und [documentName]
 * tragen nur die Dokument-Einträge; der Platzhalter für die generierten Startlisten hat beides
 * nicht - seine Beschriftung kennt die Oberfläche selbst (i18n).
 */
data class EventExportBundleItemDto(
    val id: UUID,
    val kind: EventExportBundleItemKind,
    val document: UUID?,
    val documentName: String?,
)
