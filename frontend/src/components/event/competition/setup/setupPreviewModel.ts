import type {FormSetupRound} from './common'

export type PreviewCapacity =
    | {kind: 'fixed'; count: number}
    | {kind: 'unlimited'}
    | {kind: 'invalid'}

export type OpenReason =
    | 'missing'
    | 'invalidSeed'
    | 'duplicateSeed'
    | 'unknownField'
    | 'unknownOutcome'
    | 'groupRound'

export type PreviewSource =
    | {kind: 'seed'; seed: number}
    | {kind: 'result'; seed: number; matchId: string; place: number; conditional?: true}
    | {kind: 'open'; reason: OpenReason; seed?: number}

export type PreviewMatch = {
    id: string
    roundIndex: number
    weighting: number
    name?: string
    executionOrder: number
    capacity: PreviewCapacity
    sources: PreviewSource[]
    omittedSlots: number
    issues: ('invalidCapacity' | 'missingSlots' | 'extraSlots' | 'ignoredSeeds')[]
    required: boolean
}

export type PreviewEdge = {
    from: string
    to: string
    places: number[]
    conditional?: true
}

export type PreviewRound = {
    id: string
    name: string
    qualification: boolean
    group: boolean
    customNaming: boolean
    minimumParticipantsPerUnlimitedMatch?: number
    matches: PreviewMatch[]
}

export type SetupPreview = {rounds: PreviewRound[]; edges: PreviewEdge[]}

// Bound rendering work for unfinished numeric input without imposing a new setup limit.
export const MAX_PREVIEW_SLOTS = 200

function capacity(value: string | undefined): PreviewCapacity {
    if (value === '') return {kind: 'unlimited'}
    const count = Number(value)
    return Number.isSafeInteger(count) && count > 0 ? {kind: 'fixed', count} : {kind: 'invalid'}
}

/** Equivalent to getSeedingList for fixed, fully occupied fields. Array order is weighting,
 * not executionOrder. Each place traverses the remaining matches in alternating directions.
 * Arithmetic avoids allocating a list proportional to a potentially very large form value.
 */
function outcomeAt(counts: number[], index: number, place: number): number {
    const before = counts.reduce((sum, count) => sum + Math.min(count, place - 1), 0)
    const row = counts.map((count, i) => (count >= place ? i : -1)).filter(i => i >= 0)
    if (place % 2 === 0) row.reverse()
    return before + row.indexOf(index) + 1
}

function originOf(counts: number[], seed: number): {index: number; place: number} | undefined {
    if (seed > counts.reduce((sum, count) => sum + count, 0)) return undefined
    let low = 1
    let high = Math.max(...counts)
    while (low < high) {
        const mid = low + Math.floor((high - low) / 2)
        const total = counts.reduce((sum, count) => sum + Math.min(count, mid), 0)
        if (total >= seed) high = mid
        else low = mid + 1
    }
    const index = counts.findIndex((count, i) => count >= low && outcomeAt(counts, i, low) === seed)
    return index < 0 ? undefined : {index, place: low}
}

/** Read-only projection of the current form. Never calls form setters or the bracket predictor.
 * Execution uses actual field sizes; fixed capacities here describe the fully occupied plan.
 * Unknown first-round field sizes use the same snake order, with an explicit occupancy
 * assumption. These conditional origins must never be presented as certain execution results.
 */
export function buildSetupPreview(rounds: readonly FormSetupRound[] = []): SetupPreview {
    const edges = new Map<string, PreviewEdge>()
    const previewRounds: PreviewRound[] = []

    rounds.forEach((round, roundIndex) => {
        const matches = round.isGroupRound ? [] : (round.matches ?? [])
        const capacities = matches.map(match => capacity(match.teams))
        const fixedCounts = capacities.every(c => c.kind === 'fixed')
            ? capacities.map(c => (c.kind === 'fixed' ? c.count : 0))
            : undefined
        const occurrences = new Map<number, number>()
        matches.forEach(match =>
            (match.participants ?? []).forEach(({seed}) => {
                occurrences.set(seed, (occurrences.get(seed) ?? 0) + 1)
            }),
        )
        const previous = previewRounds[roundIndex - 1]
        // Even a fixed capacity is not a reliable planned field when its input list is incomplete
        // or contradictory. Do not propagate a fabricated placement through subsequent rounds.
        const previousComplete = previous?.matches.every(
            m =>
                m.issues.length === 0 &&
                m.omittedSlots === 0 &&
                (m.roundIndex === 0 || m.sources.length > 0) &&
                m.sources.every(
                    s => s.kind !== 'open' || (m.roundIndex === 0 && s.reason === 'unknownField'),
                ),
        )
        const highestRequestedSeed = [...occurrences.keys()].reduce(
            (highest, seed) => (Number.isSafeInteger(seed) && seed > highest ? seed : highest),
            0,
        )
        const unknownFirstRoundSize = previous?.matches.some(
            m => m.roundIndex === 0 && m.capacity.kind === 'unlimited',
        )
        const conditionalDistribution = unknownFirstRoundSize && previous.matches.length > 1
        const inheritedCondition = previous?.matches.some(m =>
            m.sources.some(s => s.kind === 'result' && s.conditional),
        )
        const previousCounts =
            previousComplete && previous?.matches.every(m => m.capacity.kind !== 'invalid')
                ? previous.matches.map(m =>
                      m.capacity.kind === 'fixed'
                          ? m.capacity.count
                          : // No actual field size is invented: this is a calculation horizon only.
                            // In later rounds, explicit incoming assignments bound even unlimited fields.
                            m.roundIndex === 0
                            ? highestRequestedSeed
                            : m.sources.length,
                  )
                : undefined
        if (conditionalDistribution && previousCounts && highestRequestedSeed > 0) {
            // All unlimited matches reaching this place is sufficient for every displayed
            // outcome to retain its planned source. Smaller fields may shift those sources.
            previous.minimumParticipantsPerUnlimitedMatch = originOf(
                previousCounts,
                highestRequestedSeed,
            )?.place
        }

        const resolve = (seed: number): PreviewSource => {
            if (!Number.isSafeInteger(seed) || seed < 1)
                return {kind: 'open', reason: 'invalidSeed'}
            if ((occurrences.get(seed) ?? 0) > 1)
                return {kind: 'open', reason: 'duplicateSeed', seed}
            if (previous?.group) return {kind: 'open', reason: 'groupRound', seed}
            if (!previousCounts) return {kind: 'open', reason: 'unknownField', seed}
            const origin = originOf(previousCounts, seed)
            if (!origin) return {kind: 'open', reason: 'unknownOutcome', seed}
            return {
                kind: 'result',
                seed,
                matchId: previous.matches[origin.index].id,
                place: origin.place,
                ...(conditionalDistribution || inheritedCondition
                    ? {conditional: true as const}
                    : {}),
            }
        }

        const previewMatches = matches.map((match, index): PreviewMatch => {
            const id = `r${roundIndex}m${index}`
            const field = capacities[index]
            const participants = match.participants ?? []
            const issues: PreviewMatch['issues'] = []
            if (field.kind === 'invalid') issues.push('invalidCapacity')
            if (roundIndex === 0 && participants.length) issues.push('ignoredSeeds')
            if (roundIndex > 0 && field.kind === 'fixed') {
                if (participants.length < field.count) issues.push('missingSlots')
                if (participants.length > field.count) issues.push('extraSlots')
            }
            const sourceCount =
                roundIndex === 0
                    ? field.kind === 'fixed'
                        ? field.count
                        : 0
                    : Math.max(participants.length, field.kind === 'fixed' ? field.count : 0)
            const sources = Array.from(
                {length: Math.min(sourceCount, MAX_PREVIEW_SLOTS)},
                (_, slotIndex): PreviewSource => {
                    if (roundIndex === 0) {
                        return fixedCounts
                            ? {kind: 'seed', seed: outcomeAt(fixedCounts, index, slotIndex + 1)}
                            : {kind: 'open', reason: 'unknownField'}
                    }
                    const participant = participants[slotIndex]
                    return participant
                        ? resolve(participant.seed)
                        : {kind: 'open', reason: 'missing'}
                },
            )
            sources.forEach(source => {
                if (source.kind !== 'result') return
                const key = `${source.matchId}:${id}`
                const edge = edges.get(key) ?? {from: source.matchId, to: id, places: []}
                if (source.conditional) edge.conditional = true
                edge.places.push(source.place)
                edges.set(key, edge)
            })
            return {
                id,
                roundIndex,
                weighting: index + 1,
                name: match.name,
                executionOrder: match.executionOrder,
                capacity: field,
                sources,
                omittedSlots: Math.max(0, sourceCount - MAX_PREVIEW_SLOTS),
                issues,
                required: round.required,
            }
        })
        previewRounds.push({
            id: `r${roundIndex}`,
            name: round.name,
            qualification: round.isQualification,
            group: round.isGroupRound,
            customNaming: (round.matchNamings ?? []).length > 0,
            matches: previewMatches,
        })
    })
    return {
        rounds: previewRounds,
        edges: [...edges.values()].map(edge => ({
            ...edge,
            places: edge.places.sort((a, b) => a - b),
        })),
    }
}

export function formatPlaceRanges(places: number[]): string {
    const sorted = [...new Set(places)].sort((a, b) => a - b)
    const ranges: string[] = []
    for (let i = 0; i < sorted.length; i++) {
        const start = sorted[i]
        while (sorted[i + 1] === sorted[i] + 1) i++
        ranges.push(start === sorted[i] ? `${start}` : `${start}–${sorted[i]}`)
    }
    return ranges.join(', ')
}
