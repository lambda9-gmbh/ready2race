package de.lambda9.ready2race.backend.app.event.entity

/**
 * Ernst-Stufe des veranstaltungsweiten Hinweises. Die Namen stehen wortgleich im
 * Check-Constraint der Spalte `event.notice_severity` (Migration V202608111700) —
 * eine neue Stufe braucht beides.
 *
 * Die Übersetzung in Farben trifft das Frontend (CRITICAL→Rot, WARNING→Gelb, INFO→Grün);
 * hier steht nur die fachliche Aussage, nicht ihre Darstellung.
 */
enum class EventNoticeSeverity { INFO, WARNING, CRITICAL }
