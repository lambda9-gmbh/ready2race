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

/** Kleinste erlaubte Breite/Höhe (als Anteil der Seite) — sowohl beim Ziehen der Resize-Griffe
 * als auch bei Zahleneingabe, damit kein Kasten entstehen kann, den man per Maus nie wieder
 * greifen könnte. */
export const MIN_EXTENT = 0.01

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
    const relWidth = Math.max(MIN_EXTENT, clampUnit(rect.relWidth))
    const relHeight = Math.max(MIN_EXTENT, clampUnit(rect.relHeight))
    return {
        relWidth,
        relHeight,
        relLeft: Math.min(clampUnit(rect.relLeft), 1 - relWidth),
        relTop: Math.min(clampUnit(rect.relTop), 1 - relHeight),
    }
}

/** Größe (Breite/Höhe) in zwei Maßsystemen: einmal wie von der PDF-Seite selbst berichtet
 * (Punkte), einmal wie tatsächlich im Editor gerendert (CSS-Pixel). react-pdf liefert beide über
 * `onLoadSuccess`. */
export type PageSize = {
    width: number
    height: number
}

/**
 * Schriftgröße eines Platzhalters für die Editor-Vorschau, in gerenderten Pixeln.
 *
 * `placeholder.fontSize` steht in PDF-Punkten — so beschriftet ihn die Seitenleiste, und so
 * rendert ihn der Server. `rendered`/`original` sind dagegen zwei verschiedene Maßsysteme
 * derselben Seite: `original` sind die von der PDF selbst berichteten Punkte, `rendered` die im
 * Editor tatsächlich dargestellten Pixel. Ein expliziter Wert muss deshalb erst mit dem Verhältnis
 * von `rendered` zu `original` skaliert werden, bevor er als CSS-`font-size` taugt.
 *
 * Ohne eigene Größe gilt ersatzweise die Kastenhöhe als Schriftgröße (dieselbe Regel wie
 * serverseitig, siehe AdditionalText.kt) — das ist bereits ein Anteil der gerenderten Seite und
 * bleibt deshalb unskaliert.
 */
export const renderedFontSize = (
    placeholder: {fontSize?: number; relHeight: number},
    rendered: PageSize,
    original: PageSize,
): number => {
    if (placeholder.fontSize !== undefined) {
        const scale = original.height > 0 ? rendered.height / original.height : 1
        return placeholder.fontSize * scale
    }
    return placeholder.relHeight * rendered.height
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
