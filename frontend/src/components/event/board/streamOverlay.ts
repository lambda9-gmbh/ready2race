import {AthleteBoardMatch, AthleteBoardResult, BoardElement, BoardViewDto} from '@api/types.gen.ts'

/**
 * Was das Livestream-Overlay einblendet. Genau EIN Lauf oder nichts — ein Lower-Third
 * mit zwei Läufen gibt es nicht (Spec: bei mehreren laufenden gewinnt der zuletzt
 * gestartete, und das erledigt bereits die Slot-Maschinerie des Servers mit Offset 0).
 */
export type StreamOverlayContent =
    | {kind: 'running'; match: AthleteBoardMatch}
    | {kind: 'result'; result: AthleteBoardResult}
    | {kind: 'upcoming'; match: AthleteBoardMatch}
    | null

/** Chroma-Voreinstellung des Stream-Overlays — reines Grün. */
export const STREAM_DEFAULT_BACKGROUND = '#00FF00'

const slotAt = (slots: BoardViewDto['slots'], offset: number) =>
    slots.find(slot => slot.offset === offset)

export const streamOverlayContent = (
    slots: BoardViewDto['slots'],
    mode: BoardElement['streamMode'],
): StreamOverlayContent => {
    const running = slotAt(slots, 0)?.match
    const latestResult = slotAt(slots, -1)?.result
    const upcoming = slotAt(slots, 1)?.match
    switch (mode ?? 'AUTO') {
        case 'RUNNING':
            return running ? {kind: 'running', match: running} : null
        case 'RESULTS':
            return latestResult ? {kind: 'result', result: latestResult} : null
        case 'UPCOMING':
            return upcoming ? {kind: 'upcoming', match: upcoming} : null
        default:
            return running
                ? {kind: 'running', match: running}
                : latestResult
                  ? {kind: 'result', result: latestResult}
                  : null
    }
}
