import {describe, expect, it} from 'vitest'
import {failedLabel, formatFailedReason, matchResultStatus} from './matchResultStatus.ts'

describe('matchResultStatus', () => {
    it('erkennt die drei Kürzel', () => {
        expect(matchResultStatus('DNS')).toEqual({status: 'DNS', note: null})
        expect(matchResultStatus('DNF')).toEqual({status: 'DNF', note: null})
        expect(matchResultStatus('DQ')).toEqual({status: 'DQ', note: null})
    })

    it('ignoriert Groß-/Kleinschreibung und Leerraum', () => {
        expect(matchResultStatus('  dnf ')).toEqual({status: 'DNF', note: null})
    })

    it('normalisiert DSQ und DISQ auf DQ', () => {
        expect(matchResultStatus('DSQ')).toEqual({status: 'DQ', note: null})
        expect(matchResultStatus('disq')).toEqual({status: 'DQ', note: null})
    })

    it('trennt eine Notiz hinter dem Kürzel ab', () => {
        expect(matchResultStatus('DQ – Frühstart')).toEqual({status: 'DQ', note: 'Frühstart'})
        expect(matchResultStatus('DQ: Frühstart')).toEqual({status: 'DQ', note: 'Frühstart'})
        expect(matchResultStatus('DNS, krank gemeldet')).toEqual({
            status: 'DNS',
            note: 'krank gemeldet',
        })
        expect(matchResultStatus('DNF Boot gekentert')).toEqual({
            status: 'DNF',
            note: 'Boot gekentert',
        })
    })

    it('lässt reinen Freitext unangetastet', () => {
        expect(matchResultStatus('Boot gekentert')).toEqual({status: null, note: 'Boot gekentert'})
    })

    it('erkennt ein Kürzel nur am Anfang', () => {
        expect(matchResultStatus('Wertung strittig, DNS beantragt')).toEqual({
            status: null,
            note: 'Wertung strittig, DNS beantragt',
        })
    })

    it('erkennt kein Kürzel innerhalb eines Wortes', () => {
        expect(matchResultStatus('DNSchiedsrichter')).toEqual({
            status: null,
            note: 'DNSchiedsrichter',
        })
    })

    it('liefert für leere Angaben nichts', () => {
        expect(matchResultStatus(null)).toEqual({status: null, note: null})
        expect(matchResultStatus(undefined)).toEqual({status: null, note: null})
        expect(matchResultStatus('   ')).toEqual({status: null, note: null})
    })
})

describe('failedLabel', () => {
    it('stellt das Kürzel voran und hängt die Notiz an', () => {
        expect(failedLabel('DQ – Frühstart', 'Ausgeschieden')).toBe('DQ (Frühstart)')
        expect(failedLabel('DNS', 'Ausgeschieden')).toBe('DNS')
    })

    it('fällt ohne Kürzel auf den Ersatztext zurück', () => {
        expect(failedLabel('Boot gekentert', 'Ausgeschieden')).toBe('Ausgeschieden (Boot gekentert)')
        expect(failedLabel(null, 'Ausgeschieden')).toBe('Ausgeschieden')
    })
})

describe('formatFailedReason', () => {
    it('setzt Status und Notiz wieder zusammen', () => {
        expect(formatFailedReason('DQ', 'Frühstart')).toBe('DQ Frühstart')
        expect(formatFailedReason('DNS', '')).toBe('DNS')
        expect(formatFailedReason(null, 'Boot gekentert')).toBe('Boot gekentert')
        expect(formatFailedReason(null, '  ')).toBe(null)
        expect(formatFailedReason(null, null)).toBe(null)
    })
})
