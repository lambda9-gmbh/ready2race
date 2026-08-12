package de.lambda9.ready2race.backend.app.eventInfo.entity

import de.lambda9.ready2race.backend.app.event.entity.EventNoticeDto

/**
 * Die Antwort des Live-Tabs der öffentlichen Ergebnisseite. Bis zum 11.08.2026 war das ein
 * nacktes Array aus [LiveMatchInfo]; der Umschlag kam mit dem veranstaltungsweiten
 * Hinweisbanner dazu - der Live-Tab ist die einzige GEPOLLTE Antwort der Ergebnisseite, ohne
 * den eingebetteten Hinweis erschiene eine Wetterwarnung dort erst nach einem Neuladen.
 */
data class LiveMatchesDto(
    /** Der veranstaltungsweite Hinweisbanner (z.B. Wetterwarnung); null = kein Banner. */
    val notice: EventNoticeDto?,
    val matches: List<LiveMatchInfo>,
)
