package de.lambda9.ready2race.backend.app.eventExportBundle.entity

/**
 * Die Art eines Mappen-Eintrags: ein hochgeladenes Veranstaltungs-Dokument ([DOCUMENT]) oder der
 * eine Platzhalter für die generierten Startlisten ([GENERATED_STARTLISTS]), den man an die
 * richtige Stelle der Mappe schiebt. Die Werte spiegeln den Check-Constraint der Tabelle
 * `event_export_bundle_item`.
 */
enum class EventExportBundleItemKind { DOCUMENT, GENERATED_STARTLISTS }
