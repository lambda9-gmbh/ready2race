import {AlertColor} from '@mui/material'
import {EventNoticeSeverity} from '@api/types.gen.ts'

/**
 * Übersetzt die Ernst-Stufe des veranstaltungsweiten Hinweises in die MUI-Alert-Farbe:
 * CRITICAL→Rot (error), WARNING→Gelb (warning), INFO→Grün — bewusst `success` statt `info`,
 * damit die grüne Stufe das Design-Grün des Themes trägt und nicht das Blau von `info`.
 *
 * Die eine Stelle für diese Zuordnung; alle Banner (Mein Event, Boards, Live-Dashboard,
 * Ergebnisseite) und die Pflege-Vorschau gehen hier durch.
 */
export const eventNoticeAlertSeverity = (severity: EventNoticeSeverity): AlertColor => {
    switch (severity) {
        case 'CRITICAL':
            return 'error'
        case 'WARNING':
            return 'warning'
        case 'INFO':
            return 'success'
    }
}
