import {useParams} from '@tanstack/react-router'

/**
 * Die Durchführung lief bis zum 12.08.2026 ausschließlich auf der Wettkampf-Seite und zog ihre
 * IDs strikt aus der Route. Im Veranstaltungs-Modus des Zeitplan-Tabs steht derselbe Teilbaum
 * aber eingebettet unter der Event-Route — dort gibt es keine `$competitionId`, ein striktes
 * `competitionRoute.useParams()` würde werfen. Deshalb nehmen die betroffenen Komponenten die
 * IDs wahlweise als Props (eingebettet) und fallen sonst auf die Route zurück (Wettkampf-Seite,
 * die dadurch unverändert bleibt) — dasselbe Muster wie das eventId-Prop der LiveDashboardPage
 * für die Helfer-App.
 */
export type CompetitionScopeProps = {
    /** Veranstaltungs-ID beim eingebetteten Einsatz; fehlt sie, liefert die Route den Wert. */
    eventId?: string
    /** Wettkampf-ID beim eingebetteten Einsatz; gleiche Regel wie bei [eventId]. */
    competitionId?: string
}

/** Löst die IDs aus den Props auf, mit der Route als Rückfall (siehe [CompetitionScopeProps]). */
export const useCompetitionScope = ({
    eventId,
    competitionId,
}: CompetitionScopeProps): {eventId: string; competitionId: string} => {
    // strict: false, weil die Komponente eingebettet nicht unter der Wettkampf-Route steht.
    // Was die Route nicht kennt, müssen die Props liefern.
    const params = useParams({strict: false})
    const resolvedEventId = eventId ?? params.eventId
    const resolvedCompetitionId = competitionId ?? params.competitionId
    if (resolvedEventId === undefined || resolvedCompetitionId === undefined) {
        throw new Error(
            'Durchführung ohne Kontext: eventId/competitionId weder als Prop gesetzt noch in der Route vorhanden',
        )
    }
    return {eventId: resolvedEventId, competitionId: resolvedCompetitionId}
}
