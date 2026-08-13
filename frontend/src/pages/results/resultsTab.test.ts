import {describe, expect, it} from 'vitest'
import {initialResultsTab, parseResultsTabSearch} from './resultsTab.ts'

describe('parseResultsTabSearch', () => {
    it('lässt die bekannten Werte durch', () => {
        expect(parseResultsTabSearch('results')).toBe('results')
        expect(parseResultsTabSearch('live')).toBe('live')
        expect(parseResultsTabSearch('my-event')).toBe('my-event')
    })

    it('wirft Unbekanntes und Fehlendes auf undefined (= Default)', () => {
        expect(parseResultsTabSearch(undefined)).toBeUndefined()
        expect(parseResultsTabSearch('')).toBeUndefined()
        expect(parseResultsTabSearch('liveticker')).toBeUndefined()
        // Interner Reitername ist kein öffentlicher URL-Wert.
        expect(parseResultsTabSearch('latest-results')).toBeUndefined()
    })
})

describe('initialResultsTab', () => {
    it('öffnet den Live-Reiter für ?tab=live (QR-Aushang)', () => {
        expect(initialResultsTab('live')).toBe('live')
    })

    it('öffnet Mein Event für ?tab=my-event (bestehender QR-Einstieg)', () => {
        expect(initialResultsTab('my-event')).toBe('my-event')
    })

    it('landet für ?tab=results und ohne Parameter auf den aktuellen Ergebnissen', () => {
        expect(initialResultsTab('results')).toBe('latest-results')
        expect(initialResultsTab(undefined)).toBe('latest-results')
    })
})
