import {Alert, SxProps, Theme} from '@mui/material'
import {EventNoticeDto} from '@api/types.gen.ts'
import {eventNoticeAlertSeverity} from './eventNoticeAlert.ts'

type Props = {
    /** null/undefined = kein Banner; die Komponente rendert dann nichts. */
    notice: EventNoticeDto | null | undefined
    /** Schmale Variante für Anzeigen, die um jede Zeile kämpfen (Boards). */
    dense?: boolean
    sx?: SxProps<Theme>
}

/**
 * Der veranstaltungsweite Hinweisbanner (z.B. Wetterwarnung) — eine Komponente für alle
 * Einhängepunkte: Mein Event, Boards, Live-Dashboard und die Tabs der Ergebnisseite. Der
 * Inhalt kommt eingebettet aus der jeweils ohnehin gepollten Antwort, es gibt keinen eigenen
 * Abruf. Farbe je Stufe: siehe eventNoticeAlertSeverity.
 */
const EventNoticeBanner = ({notice, dense = false, sx}: Props) => {
    if (!notice) {
        return null
    }
    return (
        <Alert
            severity={eventNoticeAlertSeverity(notice.severity)}
            sx={{
                // Zeilenumbrüche aus dem Pflege-Textfeld bleiben erhalten.
                whiteSpace: 'pre-line',
                ...(dense ? {py: 0, alignItems: 'center'} : {}),
                ...sx,
            }}>
            {notice.text}
        </Alert>
    )
}

export default EventNoticeBanner
