package de.lambda9.ready2race.backend.app.event.entity

/**
 * Drei Betriebsarten der Lauf-Kette (Backlog C1): [SCHIEDSRICHTER] lässt Beenden und Kette wie
 * bisher über das Schiedsrichter-Dashboard laufen; [REGATTABUERO] verlagert beides exklusiv auf
 * den Zeitplan-Tab (der "Lauf beenden"-Button verschwindet dafür vom Schiedsrichter-Dashboard, das
 * Büro gibt nach Kontrolle frei, dann zieht die Kette); [DEAKTIVIERT] lässt Beenden nur auf den
 * Lauf selbst wirken, ohne die Läufe der nächsten Startzeit zu aktivieren.
 *
 * Ersetzt den bisherigen Boolean `auto_activate_next_match`
 * (Migration V202608051000: false -> DEAKTIVIERT, true -> SCHIEDSRICHTER).
 */
enum class ChainProgressionMode { SCHIEDSRICHTER, REGATTABUERO, DEAKTIVIERT }
