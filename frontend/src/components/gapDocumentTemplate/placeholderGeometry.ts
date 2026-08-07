/**
 * Die Geometrie eines Platzhalters als Anteile der Seite (0 bis 1). Ziehen im Editor,
 * Zahleneingabe in der Seitenleiste und die Pfeiltasten rechnen alle über diese Funktionen,
 * damit sie sich nicht in Randfällen unterscheiden.
 */
export type PlaceholderRect = {
    relLeft: number
    relTop: number
    relWidth: number
    relHeight: number
}

const NUDGE_SMALL = 0.001
const NUDGE_LARGE = 0.01

const clampUnit = (value: number): number => Math.min(1, Math.max(0, value))

/** Prozenteingabe als Anteil, oder undefined wenn die Eingabe keine Zahl ist. */
export const parsePercent = (value: string): number | undefined => {
    const parsed = Number.parseFloat(value.replace(',', '.'))
    if (Number.isNaN(parsed)) {
        return undefined
    }
    return clampUnit(parsed / 100)
}

/** Hält den Kasten vollständig auf der Seite. */
export const clampRect = (rect: PlaceholderRect): PlaceholderRect => {
    const relWidth = clampUnit(rect.relWidth)
    const relHeight = clampUnit(rect.relHeight)
    return {
        relWidth,
        relHeight,
        relLeft: Math.min(clampUnit(rect.relLeft), 1 - relWidth),
        relTop: Math.min(clampUnit(rect.relTop), 1 - relHeight),
    }
}

export const nudgeRect = (
    rect: PlaceholderRect,
    direction: 'left' | 'right' | 'up' | 'down',
    large: boolean,
): PlaceholderRect => {
    const step = large ? NUDGE_LARGE : NUDGE_SMALL
    switch (direction) {
        case 'left':
            return clampRect({...rect, relLeft: rect.relLeft - step})
        case 'right':
            return clampRect({...rect, relLeft: rect.relLeft + step})
        case 'up':
            return clampRect({...rect, relTop: rect.relTop - step})
        case 'down':
            return clampRect({...rect, relTop: rect.relTop + step})
    }
}
