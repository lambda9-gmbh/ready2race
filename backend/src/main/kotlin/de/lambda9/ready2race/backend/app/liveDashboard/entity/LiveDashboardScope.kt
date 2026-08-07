package de.lambda9.ready2race.backend.app.liveDashboard.entity

/**
 * Wie viel des Zeitplans eine Abfrage liefert. Der Live-Tab pollt im Sekundentakt und braucht
 * nur die laufenden Läufe; die vollständige Liste sieht sich niemand im Takt an.
 */
enum class LiveDashboardScope {
    /** Laufende Läufe; läuft keiner, der nächste anstehende. */
    LIVE,

    /** Alle Läufe der Veranstaltung. */
    ALL,
}
