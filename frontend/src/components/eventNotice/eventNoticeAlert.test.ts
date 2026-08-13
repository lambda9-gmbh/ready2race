import {describe, expect, it} from 'vitest'
import {eventNoticeAlertSeverity} from './eventNoticeAlert.ts'

/**
 * Die Zuordnung Stufe→Farbe ist die ganze Logik des Banners — und genau die Stelle, an der
 * eine Vertauschung (Wetterwarnung erscheint grün) niemandem im Code auffiele.
 */
describe('eventNoticeAlertSeverity', () => {
    it('CRITICAL wird zum roten Alert', () => {
        expect(eventNoticeAlertSeverity('CRITICAL')).toBe('error')
    })

    it('WARNING wird zum gelben Alert', () => {
        expect(eventNoticeAlertSeverity('WARNING')).toBe('warning')
    })

    it('INFO wird zum grünen Alert (success, nicht info)', () => {
        // `info` wäre blau — die grüne Stufe soll das Design-Grün des Themes tragen.
        expect(eventNoticeAlertSeverity('INFO')).toBe('success')
    })
})
