package de.lambda9.ready2race.backend.app.event.entity

/**
 * Der veranstaltungsweite Hinweis (z.B. Wetterwarnung), wie ihn die gepollten öffentlichen
 * Antworten tragen: als kleines eingebettetes Objekt oder gar nicht (`null` = kein Banner).
 *
 * Bewusst ein eigenes Objekt statt zweier loser Felder: die Anzeigen brauchen genau die
 * Entscheidung "Banner oder kein Banner", und ein halber Zustand (Text ohne Stufe) kann so
 * schon im Typ nicht vorkommen — dieselbe Zusicherung, die in der Datenbank der Paar-Check
 * aus Migration V202608111700 gibt.
 */
data class EventNoticeDto(
    val text: String,
    val severity: EventNoticeSeverity,
) {
    companion object {

        /**
         * Der eine Bau-Punkt von den Spalten zur Antwort — alle Stellen, die den Hinweis
         * einbetten (Mein Event, Boards, Live-Dashboard, öffentliche Ergebnisseite, EventDto),
         * gehen hier durch, statt die Null-Regel vierfach nachzubauen.
         */
        fun fromColumns(text: String?, severity: String?): EventNoticeDto? =
            if (text == null || severity == null) {
                null
            } else {
                EventNoticeDto(text, EventNoticeSeverity.valueOf(severity))
            }
    }
}
