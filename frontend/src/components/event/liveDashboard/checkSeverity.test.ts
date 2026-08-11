import {describe, expect, test} from 'vitest'
import {
    CheckSeverityCompetitionDto,
    CheckSeverityConfigDto,
    CheckSeverityEntryDto,
    CheckSeverityRowDefaultDto,
    CheckSeverityRowDto,
} from '@api/types.gen.ts'
import {
    applicableCells,
    buildSavePayload,
    preservedEntries,
    rowSummary,
    severityAt,
} from './checkSeverity.ts'

describe('rowSummary', () => {
    test('nennt den Wert, wenn alle Wettkämpfe ihn teilen', () => {
        expect(rowSummary(['CRITICAL', 'CRITICAL'])).toEqual({kind: 'uniform', severity: 'CRITICAL'})
    })

    test('meldet gemischt, sobald ein Wettkampf abweicht', () => {
        expect(rowSummary(['CRITICAL', 'WARNING'])).toEqual({kind: 'mixed'})
    })

    test('meldet leer, wenn kein Wettkampf diese Zeile hat', () => {
        expect(rowSummary([])).toEqual({kind: 'empty'})
    })
})

describe('severityAt', () => {
    const config = (defaults: CheckSeverityRowDefaultDto[]): CheckSeverityConfigDto => ({
        competitions: [],
        rows: [],
        defaults,
        entries: [],
    })

    test('ein vorhandener Eintrag schlägt den Standard', () => {
        const entries: CheckSeverityEntryDto[] = [
            {competitionId: 'c1', checkType: 'INVOICE_OPEN', requirementId: null, severity: 'OK'},
        ]
        const cfg = config([{checkType: 'INVOICE_OPEN', requirementId: null, severity: 'CRITICAL'}])
        expect(severityAt(cfg, entries, 'c1', 'INVOICE_OPEN', null)).toBe('OK')
    })

    test('ein fehlender Eintrag ergibt den Standard der passenden Zeile', () => {
        const cfg = config([{checkType: 'INVOICE_OPEN', requirementId: null, severity: 'WARNING'}])
        expect(severityAt(cfg, [], 'c1', 'INVOICE_OPEN', null)).toBe('WARNING')
    })

    test('ein Eintrag eines anderen Wettkampfs wirkt nicht', () => {
        const entries: CheckSeverityEntryDto[] = [
            {competitionId: 'anderer-wettkampf', checkType: 'INVOICE_OPEN', requirementId: null, severity: 'OK'},
        ]
        const cfg = config([{checkType: 'INVOICE_OPEN', requirementId: null, severity: 'WARNING'}])
        expect(severityAt(cfg, entries, 'c1', 'INVOICE_OPEN', null)).toBe('WARNING')
    })

    test('ein Eintrag einer anderen Bedingung wirkt nicht', () => {
        const entries: CheckSeverityEntryDto[] = [
            {competitionId: 'c1', checkType: 'REQUIREMENT', requirementId: 'req-1', severity: 'OK'},
        ]
        const cfg = config([{checkType: 'INVOICE_OPEN', requirementId: null, severity: 'WARNING'}])
        expect(severityAt(cfg, entries, 'c1', 'INVOICE_OPEN', null)).toBe('WARNING')
    })

    test('requirementId als null im Eintrag und als undefined in der Abfrage werden gleich behandelt', () => {
        const entries: CheckSeverityEntryDto[] = [
            {competitionId: 'c1', checkType: 'INVOICE_OPEN', requirementId: null, severity: 'OK'},
        ]
        const cfg = config([])
        expect(severityAt(cfg, entries, 'c1', 'INVOICE_OPEN', null)).toBe('OK')
    })

    test('requirementId als undefined im Eintrag und als null in der Abfrage werden gleich behandelt', () => {
        const entries: CheckSeverityEntryDto[] = [
            {competitionId: 'c1', checkType: 'INVOICE_OPEN', severity: 'OK'},
        ]
        const cfg = config([])
        expect(severityAt(cfg, entries, 'c1', 'INVOICE_OPEN', null)).toBe('OK')
    })

    test('requirementId als undefined im Standard und als null in der Abfrage werden gleich behandelt', () => {
        const cfgWithUndefinedDefault: CheckSeverityConfigDto = {
            competitions: [],
            rows: [],
            defaults: [{checkType: 'INVOICE_OPEN', severity: 'WARNING'}],
            entries: [],
        }
        expect(severityAt(cfgWithUndefinedDefault, [], 'c1', 'INVOICE_OPEN', null)).toBe('WARNING')
    })

    test('ein Standard einer anderen requirementId wirkt nicht', () => {
        const cfg = config([{checkType: 'REQUIREMENT', requirementId: 'req-1', severity: 'WARNING'}])
        expect(severityAt(cfg, [], 'c1', 'REQUIREMENT', 'req-2')).toBe('CRITICAL')
    })

    test('ohne Eintrag und ohne passenden Standard greift die Notbremse CRITICAL - eigentlich widersprüchliche Serverdaten', () => {
        const cfg = config([])
        expect(severityAt(cfg, [], 'c1', 'INVOICE_OPEN', null)).toBe('CRITICAL')
    })
})

describe('preservedEntries', () => {
    const competition = (
        competitionId: string,
        checkInOutRequired: boolean,
    ): CheckSeverityCompetitionDto => ({
        competitionId,
        identifier: competitionId,
        name: competitionId,
        checkInOutRequired,
    })

    const row = (
        checkType: CheckSeverityRowDto['checkType'],
        requirementId: string | null = null,
    ): CheckSeverityRowDto => ({checkType, requirementId})

    const config = (
        competitions: CheckSeverityCompetitionDto[],
        rows: CheckSeverityRowDto[],
        entries: CheckSeverityEntryDto[],
    ): CheckSeverityConfigDto => ({
        competitions,
        rows,
        defaults: [],
        entries,
    })

    // Die allgemeine Regel: bewahrt wird, was die Matrix aus Wettkämpfen x Zeilen gerade nicht
    // abdeckt - unabhängig davon, aus welchem Grund die Kombination fehlt.

    test('ein gespeicherter Wert für eine Kombination, die die Matrix nicht (mehr) abdeckt, wird erhalten', () => {
        // Die Zeile existiert, aber der Wettkampf verlangt keine An-/Abmeldung mehr -
        // isRowApplicable nimmt die Kombination aus der Matrix.
        const entries: CheckSeverityEntryDto[] = [
            {competitionId: 'c1', checkType: 'NOT_IN_ARENA', requirementId: null, severity: 'OK'},
        ]
        const cfg = config([competition('c1', false)], [row('NOT_IN_ARENA')], entries)
        expect(preservedEntries(cfg)).toEqual(entries)
    })

    test('ein gespeicherter Wert, dessen Kombination weiterhin in der Matrix steht, wird nicht erhalten', () => {
        const entries: CheckSeverityEntryDto[] = [
            {competitionId: 'c1', checkType: 'NOT_IN_ARENA', requirementId: null, severity: 'OK'},
            {competitionId: 'c1', checkType: 'INVOICE_OPEN', requirementId: null, severity: 'WARNING'},
        ]
        const cfg = config(
            [competition('c1', true)],
            [row('NOT_IN_ARENA'), row('INVOICE_OPEN')],
            entries,
        )
        expect(preservedEntries(cfg)).toEqual([])
    })

    test('ein Eintrag zu einem inzwischen entfernten Wettkampf wird ebenfalls erhalten', () => {
        const entries: CheckSeverityEntryDto[] = [
            {competitionId: 'geloescht', checkType: 'INVOICE_OPEN', requirementId: null, severity: 'OK'},
        ]
        const cfg = config([], [row('INVOICE_OPEN')], entries)
        expect(preservedEntries(cfg)).toEqual(entries)
    })

    test('ein gespeicherter Eintrag, dessen Zeile entfallen ist (z.B. entferntes Prüf-Zeitfenster), wird erhalten', () => {
        // Wird das Zeitfenster einer Teilnahmebedingung entfernt (checkEarliestMinutesBefore und
        // checkLatestMinutesBefore auf null), liefert der Server keine REQUIREMENT_TIME_WINDOW-Zeile
        // mehr in config.rows. Der zuvor gespeicherte Schweregrad dafür steht aber noch in der DB.
        const entries: CheckSeverityEntryDto[] = [
            {
                competitionId: 'c1',
                checkType: 'REQUIREMENT_TIME_WINDOW',
                requirementId: 'req-1',
                severity: 'WARNING',
            },
        ]
        const cfg = config([competition('c1', true)], [], entries)
        expect(preservedEntries(cfg)).toEqual(entries)
    })

    test('kein bewahrter Eintrag teilt seine Kombination mit einer Zelle der Matrix', () => {
        // Das Backend lehnt doppelte Kombinationen aus Wettkampf, Prüfungsart und Bedingung ab -
        // bewahrte und bearbeitbare Einträge dürfen sich daher nie überschneiden.
        const entries: CheckSeverityEntryDto[] = [
            {competitionId: 'c1', checkType: 'NOT_IN_ARENA', requirementId: null, severity: 'OK'},
            {
                competitionId: 'c1',
                checkType: 'REQUIREMENT_TIME_WINDOW',
                requirementId: 'req-1',
                severity: 'WARNING',
            },
        ]
        const cfg = config(
            [competition('c1', true), competition('c2', false)],
            [row('NOT_IN_ARENA')],
            entries,
        )
        const cellKeys = new Set(
            applicableCells(cfg).map(
                ({competition: c, row: r}) =>
                    `${c.competitionId}:${r.checkType}:${r.requirementId ?? ''}`,
            ),
        )
        preservedEntries(cfg).forEach(e =>
            expect(cellKeys.has(`${e.competitionId}:${e.checkType}:${e.requirementId ?? ''}`)).toBe(
                false,
            ),
        )
    })
})

describe('buildSavePayload', () => {
    test('bewahrte Einträge nicht anwendbarer Kombinationen werden unverändert angehängt', () => {
        const entries: CheckSeverityEntryDto[] = [
            {competitionId: 'c1', checkType: 'INVOICE_OPEN', requirementId: null, severity: 'WARNING'},
        ]
        const preserved: CheckSeverityEntryDto[] = [
            {competitionId: 'c2', checkType: 'NOT_IN_ARENA', requirementId: null, severity: 'OK'},
        ]
        expect(buildSavePayload(entries, preserved)).toEqual([...entries, ...preserved])
    })

    test('ohne bewahrte Einträge bleibt die Matrix unverändert', () => {
        const entries: CheckSeverityEntryDto[] = [
            {competitionId: 'c1', checkType: 'INVOICE_OPEN', requirementId: null, severity: 'WARNING'},
        ]
        expect(buildSavePayload(entries, [])).toEqual(entries)
    })

    test('ein bewahrter Eintrag, der in der Matrix schon vorkommt, wird nicht verdoppelt', () => {
        const entries: CheckSeverityEntryDto[] = [
            {competitionId: 'c1', checkType: 'INVOICE_OPEN', requirementId: null, severity: 'WARNING'},
        ]
        const preserved: CheckSeverityEntryDto[] = [
            {competitionId: 'c1', checkType: 'INVOICE_OPEN', requirementId: null, severity: 'CRITICAL'},
        ]
        expect(buildSavePayload(entries, preserved)).toEqual(entries)
    })

    test('requirementId als null und als undefined gelten als derselbe Schlüssel', () => {
        const entries: CheckSeverityEntryDto[] = [
            {competitionId: 'c1', checkType: 'INVOICE_OPEN', requirementId: null, severity: 'WARNING'},
        ]
        const preserved: CheckSeverityEntryDto[] = [
            // requirementId fehlt hier bewusst (undefined) statt explizit null zu sein - beides
            // muss auf denselben Schlüssel abbilden, sonst würde derselbe Eintrag verdoppelt.
            {competitionId: 'c1', checkType: 'INVOICE_OPEN', severity: 'CRITICAL'},
        ]
        expect(buildSavePayload(entries, preserved)).toEqual(entries)
    })
})
