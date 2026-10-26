import {describe, expect, it} from 'vitest'
import type {FormSetupMatch, FormSetupRound} from './common'
import {buildSetupPreview, formatPlaceRanges, MAX_PREVIEW_SLOTS} from './setupPreviewModel'

const match = (teams: string, seeds: number[] = [], name?: string): FormSetupMatch => ({
    teams,
    participants: seeds.map(seed => ({seed})),
    executionOrder: 1,
    name,
})
const round = (matches: FormSetupMatch[], extra: Partial<FormSetupRound> = {}): FormSetupRound => ({
    name: '',
    required: false,
    matches,
    groups: [],
    useDefaultSeeding: true,
    placesOption: 'EQUAL',
    places: [],
    isGroupRound: false,
    useStartTimeOffsets: false,
    isQualification: false,
    matchNamings: [],
    ...extra,
})
const result = (matchId: string, place: number, seed: number) => ({
    kind: 'result',
    matchId,
    place,
    seed,
})

describe('competition setup preview', () => {
    it('uses first-round snake seeding, not empty participant lists or execution order', () => {
        const model = buildSetupPreview([
            round([{...match('2'), executionOrder: 4}, match('2'), match('2'), match('2')]),
        ])
        expect(model.rounds[0].matches.map(m => m.sources)).toEqual(
            [
                [1, 8],
                [2, 7],
                [3, 6],
                [4, 5],
            ].map(seeds => seeds.map(seed => ({kind: 'seed', seed}))),
        )
    })

    it('resolves manually assigned winners and losers without predicting winning teams', () => {
        const model = buildSetupPreview([
            round([match('2', [], 'A'), match('2', [], 'B')]),
            round([match('2', [2, 1]), match('2', [4, 3])], {useDefaultSeeding: false}),
        ])
        expect(model.rounds[1].matches.map(m => m.sources)).toEqual([
            [result('r0m1', 1, 2), result('r0m0', 1, 1)],
            [result('r0m0', 2, 4), result('r0m1', 2, 3)],
        ])
        expect(model.edges).toContainEqual({from: 'r0m0', to: 'r1m1', places: [2]})
    })

    it('groups places 1–2 into A and 3–4 into B from two four-participant matches', () => {
        const model = buildSetupPreview([
            round([match('4'), match('4')]),
            round([match('4', [1, 2, 3, 4], 'A'), match('4', [5, 6, 7, 8], 'B')]),
        ])
        expect(model.edges).toEqual([
            {from: 'r0m0', to: 'r1m0', places: [1, 2]},
            {from: 'r0m1', to: 'r1m0', places: [1, 2]},
            {from: 'r0m0', to: 'r1m1', places: [3, 4]},
            {from: 'r0m1', to: 'r1m1', places: [3, 4]},
        ])
    })

    it('handles six-person and unequal matches after smaller fields run out', () => {
        const model = buildSetupPreview([
            round([match('6'), match('2'), match('1')]),
            round([match('3', [6, 8, 9])]),
        ])
        expect(model.rounds[1].matches[0].sources).toEqual([
            result('r0m0', 3, 6),
            result('r0m0', 5, 8),
            result('r0m0', 6, 9),
        ])
    })

    it('carries waiting participants through intermediate one-slot matches only', () => {
        const model = buildSetupPreview([
            round([match('2'), match('2')]),
            round([match('1', [1]), match('1', [2]), match('2', [3, 4])], {required: true}),
            round([match('2', [1, 2]), match('1', [3])]),
        ])
        expect(model.rounds[1].matches[0].required).toBe(true)
        expect(model.rounds[2].matches[0].sources).toEqual([
            result('r1m0', 1, 1),
            result('r1m1', 1, 2),
        ])
        expect(model.edges.every(e => Number(e.to[1]) - Number(e.from[1]) === 1)).toBe(true)
    })

    it('does not use final placing rules to infer forwarding', () => {
        const rounds = [round([match('2'), match('2')]), round([match('2', [4, 3])])]
        const before = buildSetupPreview(rounds)
        rounds[0].placesOption = 'CUSTOM'
        rounds[0].places = [{roundOutcome: 4, place: 1}]
        expect(buildSetupPreview(rounds)).toEqual(before)
    })

    it('keeps every unlimited match unlimited across successive rounds without invented slots', () => {
        const model = buildSetupPreview([
            round([match(''), match('')]),
            round([match('', [1, 3]), match('', [2, 4])]),
            round([match('', [1]), match('', [2])]),
        ])
        expect(model.rounds.every(r => !r.qualification)).toBe(true)
        expect(
            model.rounds.flatMap(r => r.matches).every(m => m.capacity.kind === 'unlimited'),
        ).toBe(true)
        expect(model.rounds[0].matches.map(m => m.sources)).toEqual([[], []])
        expect(model.rounds[1].matches[0].sources).toEqual([
            {...result('r0m0', 1, 1), conditional: true},
            {...result('r0m1', 2, 3), conditional: true},
        ])
        expect(model.rounds[2].matches[0].sources).toEqual([
            {...result('r1m0', 1, 1), conditional: true},
        ])
        expect(model.edges).toHaveLength(6)
        expect(model.edges.every(edge => edge.conditional)).toBe(true)
        expect(model.rounds[0].minimumParticipantsPerUnlimitedMatch).toBe(2)
    })

    it('shows the Langstrecke template with explicit occupancy assumptions and no invented start slots', () => {
        // Local template: two unlimited preliminary matches followed by an eight-slot mass start.
        const rounds = [
            round([match('', [], 'Vorrunde 1'), match('', [], 'Vorrunde 2')], {name: 'Vorrunde'}),
            round([match('8', [1, 2, 3, 4, 5, 6, 7, 8], 'Massenstart')], {
                name: 'Hauptrunde',
                required: true,
                placesOption: 'ASCENDING',
            }),
        ]
        const snapshot = structuredClone(rounds)
        const model = buildSetupPreview(rounds)
        expect(model.rounds[0].minimumParticipantsPerUnlimitedMatch).toBe(4)
        expect(model.rounds[0].matches.map(m => [m.capacity, m.sources])).toEqual([
            [{kind: 'unlimited'}, []],
            [{kind: 'unlimited'}, []],
        ])
        expect(model.rounds[1].matches[0].sources).toEqual(
            [
                result('r0m0', 1, 1),
                result('r0m1', 1, 2),
                result('r0m1', 2, 3),
                result('r0m0', 2, 4),
                result('r0m0', 3, 5),
                result('r0m1', 3, 6),
                result('r0m1', 4, 7),
                result('r0m0', 4, 8),
            ].map(source => ({...source, conditional: true})),
        )
        expect(model.edges).toEqual([
            {from: 'r0m0', to: 'r1m0', places: [1, 2, 3, 4], conditional: true},
            {from: 'r0m1', to: 'r1m0', places: [1, 2, 3, 4], conditional: true},
        ])
        expect(rounds).toEqual(snapshot)
    })

    it('does not mistake the unlimited calculation horizon for a known or maximum field size', () => {
        const model = buildSetupPreview([
            round([match(''), match('')]),
            round([match('2', [1, 9])]),
        ])
        expect(model.rounds[0].minimumParticipantsPerUnlimitedMatch).toBe(5)
        expect(model.rounds[1].matches[0].sources[1]).toEqual({
            ...result('r0m0', 5, 9),
            conditional: true,
        })
    })

    it('explains why Langstrecke forwarding is conditional when one preliminary field has fewer than four', () => {
        const smallerField = buildSetupPreview([
            round([match('3'), match('5')]),
            round([match('8', [1, 2, 3, 4, 5, 6, 7, 8])]),
        ])
        // The eighth round outcome shifts from place 4 of the first match to place 5 of the second.
        expect(smallerField.rounds[1].matches[0].sources[7]).toEqual(result('r0m1', 5, 8))
        for (const counts of [
            ['4', '4'],
            ['5', '7'],
            ['8', '4'],
        ]) {
            const sufficientlyFilled = buildSetupPreview([
                round(counts.map(count => match(count))),
                round([match('8', [1, 2, 3, 4, 5, 6, 7, 8])]),
            ])
            expect(sufficientlyFilled.rounds[1].matches[0].sources[7]).toEqual(result('r0m0', 4, 8))
        }
    })

    it('keeps inherited uncertainty when a conditional preliminary field feeds fixed later rounds', () => {
        const model = buildSetupPreview([
            round([match(''), match('')]),
            round([match('2', [1, 2]), match('2', [3, 4])]),
            round([match('2', [1, 2])]),
        ])
        expect(model.rounds[2].matches[0].sources).toEqual([
            {...result('r1m0', 1, 1), conditional: true},
            {...result('r1m1', 1, 2), conditional: true},
        ])
    })

    it('uses explicit incoming assignments for unequal unlimited fields in later rounds', () => {
        const model = buildSetupPreview([
            round([match('')]),
            round([match('', [1, 2, 3, 4]), match('', [5, 6])]),
            round([match('2', [5, 6])]),
        ])
        expect(model.rounds[2].matches[0].sources).toEqual([
            result('r1m0', 3, 5),
            result('r1m0', 4, 6),
        ])
        expect(model.rounds[1].minimumParticipantsPerUnlimitedMatch).toBeUndefined()
    })

    it('can resolve positions from a single unlimited match, without assuming qualification', () => {
        const model = buildSetupPreview([round([match('')]), round([match('2', [1, 7])])])
        expect(model.rounds[1].matches[0].sources).toEqual([
            result('r0m0', 1, 1),
            result('r0m0', 7, 7),
        ])
    })

    it('does not imply automatic advancement through empty unlimited participant lists', () => {
        const model = buildSetupPreview([
            round([match('')]),
            round([match('')]),
            round([match('1', [1])]),
        ])
        expect(model.edges).toEqual([])
        expect(model.rounds[2].matches[0].sources[0]).toMatchObject({kind: 'open'})
    })

    it('bounds outcomes of a following unlimited match by its explicit assignments', () => {
        const model = buildSetupPreview([
            round([match('')]),
            round([match('', [1, 7])]),
            round([match('1', [3])]),
        ])
        expect(model.rounds[2].matches[0].sources[0]).toEqual({
            kind: 'open',
            reason: 'unknownOutcome',
            seed: 3,
        })
    })

    it('shows conditional forwarding for mixed unlimited/fixed fields while keeping initial seeds open', () => {
        const model = buildSetupPreview([
            round([match(''), match('2')]),
            round([match('2', [1, 2])]),
        ])
        expect(model.rounds[0].matches[1].sources.every(s => s.kind === 'open')).toBe(true)
        expect(model.edges).toEqual([
            {from: 'r0m0', to: 'r1m0', places: [1], conditional: true},
            {from: 'r0m1', to: 'r1m0', places: [1], conditional: true},
        ])
    })

    it('does not replace contradictory unlimited assignments with optimistic connections', () => {
        const model = buildSetupPreview([
            round([match(''), match('')]),
            round([match('', [1]), match('', [1])]),
            round([match('2', [1, 2])]),
        ])
        expect(model.edges).toEqual([])
        expect(model.rounds[2].matches[0].sources.every(s => s.kind === 'open')).toBe(true)
    })

    it('detects duplicate, invalid, missing and impossible assignments without mutating the form', () => {
        const rounds = [
            round([match('2')]),
            round([match('2', [1]), match('4', [1, 0, 1.5, 99]), match('oops')]),
            round([match('1', [1])]),
        ]
        const snapshot = structuredClone(rounds)
        const model = buildSetupPreview(rounds)
        expect(rounds).toEqual(snapshot)
        expect(model.rounds[1].matches[0].issues).toEqual(['missingSlots'])
        expect(model.rounds[1].matches[0].sources).toEqual([
            {kind: 'open', reason: 'duplicateSeed', seed: 1},
            {kind: 'open', reason: 'missing'},
        ])
        expect(model.rounds[1].matches[1].sources.slice(1)).toEqual([
            {kind: 'open', reason: 'invalidSeed'},
            {kind: 'open', reason: 'invalidSeed'},
            {kind: 'open', reason: 'unknownOutcome', seed: 99},
        ])
        expect(model.rounds[1].matches[2].capacity).toEqual({kind: 'invalid'})
        expect(model.rounds[2].matches[0].sources[0]).toMatchObject({kind: 'open'})
        expect(model.edges).toEqual([])
    })

    it('preserves excess assignments as a visible contradiction', () => {
        const model = buildSetupPreview([round([match('4')]), round([match('1', [1, 2])])])
        expect(model.rounds[1].matches[0].issues).toEqual(['extraSlots'])
        expect(model.rounds[1].matches[0].sources).toHaveLength(2)
    })

    it('does not silently interpret pre-existing group stages as ordinary matches', () => {
        const model = buildSetupPreview([
            round([match('2')], {isGroupRound: true}),
            round([match('1', [1])]),
        ])
        expect(model.rounds[0].matches).toEqual([])
        expect(model.rounds[1].matches[0].sources[0]).toEqual({
            kind: 'open',
            reason: 'groupRound',
            seed: 1,
        })
    })

    it('tolerates empty setup and bounds work for extreme unfinished numeric input', () => {
        expect(buildSetupPreview()).toEqual({rounds: [], edges: []})
        expect(buildSetupPreview([round([])]).rounds[0].matches).toEqual([])
        const m = buildSetupPreview([round([match('1000000000')])]).rounds[0].matches[0]
        expect(m.sources).toHaveLength(MAX_PREVIEW_SLOTS)
        expect(m.omittedSlots).toBe(1000000000 - MAX_PREVIEW_SLOTS)
    })

    it('formats arbitrary non-contiguous placements without including intervening places', () => {
        expect(formatPlaceRanges([6, 2, 1, 4, 6])).toBe('1–2, 4, 6')
    })
})
