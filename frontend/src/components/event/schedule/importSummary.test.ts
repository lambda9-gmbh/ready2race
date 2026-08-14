import {describe, expect, it} from 'vitest'
import {
    isNoteworthyImportRow,
    isUnmatchedImportRow,
    needsUnmatchedConfirmation,
    summarizeImportRows,
    visibleImportRows,
} from './importSummary.ts'
import {ImportRowResultDto} from '@api/types.gen.ts'

const importRow = (over: Partial<ImportRowResultDto> = {}): ImportRowResultDto => ({
    rowNumber: 1,
    startTime: '2026-08-17T08:00:00',
    competitionText: 'CM 1x',
    laufText: 'Viertelfinale VF1',
    status: 'LINKED',
    targetLabel: 'CM 1x – Viertelfinale – VF1',
    availableMatches: [],
    ...over,
})

describe('isUnmatchedImportRow', () => {
    it('is true for the three statuses that silently become a free slot', () => {
        expect(isUnmatchedImportRow(importRow({status: 'COMPETITION_NOT_FOUND'}))).toBe(true)
        expect(isUnmatchedImportRow(importRow({status: 'MATCH_NOT_FOUND'}))).toBe(true)
        expect(isUnmatchedImportRow(importRow({status: 'AMBIGUOUS'}))).toBe(true)
    })

    it('is false for a deliberately free row - a break is not a mistake', () => {
        expect(isUnmatchedImportRow(importRow({status: 'FREE'}))).toBe(false)
    })

    it('is false for linked and duplicate rows', () => {
        expect(isUnmatchedImportRow(importRow({status: 'LINKED'}))).toBe(false)
        expect(isUnmatchedImportRow(importRow({status: 'DUPLICATE'}))).toBe(false)
    })
})

describe('isNoteworthyImportRow', () => {
    it('also covers the blocking duplicate, not just the unmatched rows', () => {
        expect(isNoteworthyImportRow(importRow({status: 'DUPLICATE'}))).toBe(true)
        expect(isNoteworthyImportRow(importRow({status: 'MATCH_NOT_FOUND'}))).toBe(true)
    })

    it('leaves out the rows that import as intended', () => {
        expect(isNoteworthyImportRow(importRow({status: 'LINKED'}))).toBe(false)
        expect(isNoteworthyImportRow(importRow({status: 'FREE'}))).toBe(false)
    })
})

describe('summarizeImportRows', () => {
    it('counts every category of a mixed preview', () => {
        const summary = summarizeImportRows([
            importRow({rowNumber: 2, status: 'LINKED'}),
            importRow({rowNumber: 3, status: 'LINKED'}),
            importRow({rowNumber: 4, status: 'FREE'}),
            // Der Fall vom 1. Regattatag: Datei sagt "VF2", das Setup kennt nur "VF1".
            importRow({rowNumber: 5, status: 'MATCH_NOT_FOUND', availableMatches: ['VF1']}),
            importRow({rowNumber: 6, status: 'COMPETITION_NOT_FOUND'}),
            importRow({rowNumber: 7, status: 'AMBIGUOUS'}),
            importRow({rowNumber: 8, status: 'DUPLICATE'}),
        ])
        expect(summary).toEqual({
            total: 7,
            linked: 2,
            free: 1,
            unmatched: 3,
            duplicate: 1,
            noteworthy: 4,
        })
    })

    it('returns zeroes for an empty preview', () => {
        expect(summarizeImportRows([])).toEqual({
            total: 0,
            linked: 0,
            free: 0,
            unmatched: 0,
            duplicate: 0,
            noteworthy: 0,
        })
    })

    it('does not count a deliberately free row as unmatched', () => {
        const summary = summarizeImportRows([importRow({status: 'FREE'}), importRow({status: 'FREE'})])
        expect(summary.free).toBe(2)
        expect(summary.unmatched).toBe(0)
        expect(summary.noteworthy).toBe(0)
    })
})

describe('visibleImportRows', () => {
    const rows = [
        importRow({rowNumber: 2, status: 'LINKED'}),
        importRow({rowNumber: 3, status: 'MATCH_NOT_FOUND'}),
        importRow({rowNumber: 4, status: 'FREE'}),
        importRow({rowNumber: 5, status: 'DUPLICATE'}),
    ]

    it('returns every row when the filter is off', () => {
        expect(visibleImportRows(rows, false)).toEqual(rows)
    })

    it('keeps only the noteworthy rows, in file order', () => {
        expect(visibleImportRows(rows, true).map(r => r.rowNumber)).toEqual([3, 5])
    })

    it('is empty when the filter is on and nothing is noteworthy', () => {
        expect(visibleImportRows([importRow({status: 'LINKED'})], true)).toEqual([])
    })
})

describe('needsUnmatchedConfirmation', () => {
    it('is false for a clean preview - no extra click in the normal case', () => {
        expect(
            needsUnmatchedConfirmation([
                importRow({status: 'LINKED'}),
                importRow({status: 'FREE'}),
            ]),
        ).toBe(false)
    })

    it('is true as soon as one row was not matched', () => {
        expect(
            needsUnmatchedConfirmation([
                importRow({status: 'LINKED'}),
                importRow({status: 'MATCH_NOT_FOUND', availableMatches: ['VF1']}),
            ]),
        ).toBe(true)
    })

    it('is false for a duplicate alone - that one blocks anyway', () => {
        expect(needsUnmatchedConfirmation([importRow({status: 'DUPLICATE'})])).toBe(false)
    })

    it('is false for an empty preview', () => {
        expect(needsUnmatchedConfirmation([])).toBe(false)
    })
})
