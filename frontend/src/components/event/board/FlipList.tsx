import {CSSProperties, ReactNode, useLayoutEffect, useRef} from 'react'

type FlipListAxis = 'x' | 'y'

interface FlipListProps<T> {
    items: T[]
    keyOf: (item: T) => string
    render: (item: T, index: number) => ReactNode
    /** Bewegungsachse: 'y' für vertikal umsortierende Zeilen (Standard, Lower-Third/
     *  Ergebnis-Panel), 'x' für das horizontal nachrückende Rundenband. */
    axis?: FlipListAxis
    /** Startversatz NEUER Einträge in px — bei 'y' schieben sie von unten herein
     *  (translateY(enterOffset) → 0), bei 'x' von rechts (translateX). */
    enterOffset?: number
    /** Inline-Style für den Wrapper-<div> jedes Eintrags. FlipList selbst rendert nur
     *  ein Fragment — der Wrapper ist aber der tatsächliche Knoten, der als Flex-Item im
     *  umgebenden Container steht (z. B. damit sich Bauchband-Einträge die Breite per
     *  `flex`/`minWidth` gleichmäßig teilen; `flex` auf einem Kind DES Wrappers hätte
     *  keine Wirkung, da nicht dieses Kind, sondern der Wrapper das Flex-Item ist). */
    itemStyle?: CSSProperties
}

const TRANSITION_MS = 350

/**
 * Schlüsselfolge zweier Renderdurchgänge — reihenfolgeempfindlich, damit ein reines
 * Umsortieren (gleiche Schlüssel, andere Reihenfolge) genauso als „geändert" zählt wie
 * ein neuer/entfernter Schlüssel. Bewusst als reine Funktion ausgelagert, ohne
 * React-Bezug, damit sie einzeln testbar ist.
 */
export const sameKeySequence = (a: readonly string[], b: readonly string[]): boolean =>
    a.length === b.length && a.every((key, index) => key === b[index])

/**
 * FLIP-Liste (First, Last, Invert, Play): animiert Positionswechsel UND neue Einträge
 * ausschließlich über CSS-Transforms — nie über Opacity oder Größe. Auf dem
 * chroma-tauglichen Panel dürfen Kanten nie halbtransparent werden, ein Transform
 * verschiebt nur fertig gerenderte, deckende Pixel.
 *
 * Prinzip: eine Ref-Map merkt sich die zuletzt gemessene Position jedes Schlüssels.
 * Nach jedem Rendern (`useLayoutEffect`, läuft synchron vor dem Browser-Paint) wird
 * neu gemessen. Hat sich die Position verschoben, springt das Element per Transform
 * SOFORT — ohne Transition — an die alte Stelle zurück; im nächsten Frame (rAF) wird
 * die Transition wieder aktiviert und der Transform auf `none` gesetzt. Der Browser
 * animiert diesen "Rücksprung zur Identität" als sichtbare Bewegung von alt nach neu.
 * Ein neuer Schlüssel hat keine alte Position und startet direkt mit `enterOffset`
 * statt an seinem Ziel — er schiebt sich sichtbar herein statt einfach aufzutauchen.
 *
 * Zwei Kniffe halten das robust gegen einen Re-Render MITTEN in einer laufenden
 * 350-ms-Animation (z. B. der 1-Sekunden-Countdown-Ticker in UpcomingPanel, der neu
 * rendert, ohne dass sich an Reihenfolge oder Bestand der Liste etwas ändert):
 *
 * a) Vor jeder Messung wird der Knoten neutralisiert: Inline-`transition`/`transform`
 *    sichern, auf `none`/`none` setzen, `getBoundingClientRect()` lesen, Inline-Style
 *    sofort wiederherstellen. So liefert die Messung immer die WAHRE Layout-Position,
 *    unabhängig davon, ob gerade eine Animation läuft — und weil Setzen+Restore
 *    synchron vor dem nächsten Paint passiert, ist davon nichts sichtbar; eine laufende
 *    Animation läuft unbeeinflusst weiter.
 * b) Ist die gerenderte Schlüsselfolge seit dem letzten Durchgang UNVERÄNDERT
 *    (`sameKeySequence`), wird nur die Basislinie (die neu gemessene wahre Position)
 *    nachgeführt — der komplette Sprung-und-Transition-Tanz entfällt. Nur eine
 *    tatsächliche Umsortierung oder ein neuer/entfernter Schlüssel löst eine Animation
 *    aus, ein reiner Inhalts-Re-Render tut das nie.
 */
function FlipList<T>({items, keyOf, render, axis = 'y', enterOffset = 24, itemStyle}: FlipListProps<T>) {
    const nodes = useRef(new Map<string, HTMLDivElement>())
    const lastPositions = useRef(new Map<string, number>())
    const lastKeys = useRef<string[]>([])

    useLayoutEffect(() => {
        const keysNow = items.map(keyOf)
        const keysChanged = !sameKeySequence(keysNow, lastKeys.current)

        items.forEach(item => {
            const key = keyOf(item)
            const node = nodes.current.get(key)
            if (!node) return

            // Neutralisieren VOR dem Messen (siehe KDoc, Punkt a): sonst würde ein
            // Tick-Re-Render während laufender Animation die durch den aktiven Transform
            // verschobene Position statt der wahren Layout-Position lesen.
            const savedTransition = node.style.transition
            const savedTransform = node.style.transform
            node.style.transition = 'none'
            node.style.transform = 'none'
            const rect = node.getBoundingClientRect()
            node.style.transition = savedTransition
            node.style.transform = savedTransform

            const current = axis === 'y' ? rect.top : rect.left
            const previous = lastPositions.current.get(key)

            if (!keysChanged) {
                // Punkt b: Reihenfolge unverändert — nur Basislinie nachführen, keine
                // Animation. Eine noch laufende Transition (Eintritt oder vorheriger
                // FLIP-Sprung) bleibt durch die Wiederherstellung oben unangetastet.
                lastPositions.current.set(key, current)
                return
            }

            const delta = previous === undefined ? enterOffset : previous - current

            if (delta !== 0) {
                node.style.transition = 'none'
                node.style.transform = axis === 'y' ? `translateY(${delta}px)` : `translateX(${delta}px)`
                // Layout erzwingen, bevor die Transition wieder aktiv wird — sonst fasst
                // der Browser Sprung und Rückstellung in einem Frame zusammen und es
                // gibt keine sichtbare Bewegung.
                void node.offsetHeight
                requestAnimationFrame(() => {
                    node.style.transition = `transform ${TRANSITION_MS}ms ease-out`
                    node.style.transform = ''
                })
            }
            lastPositions.current.set(key, current)
        })

        lastKeys.current = keysNow

        // Verwaiste Positionen räumen: fällt ein Eintrag aus der Liste und kommt später
        // zurück, soll er wieder als NEU hereinschieben statt von einer längst
        // vergangenen Stelle aus zu springen.
        const keysNowSet = new Set(keysNow)
        for (const key of lastPositions.current.keys()) {
            if (!keysNowSet.has(key)) lastPositions.current.delete(key)
        }
    })

    return (
        <>
            {items.map((item, index) => {
                const key = keyOf(item)
                return (
                    <div
                        key={key}
                        style={itemStyle}
                        ref={node => {
                            if (node) nodes.current.set(key, node)
                            else nodes.current.delete(key)
                        }}>
                        {render(item, index)}
                    </div>
                )
            })}
        </>
    )
}

export default FlipList
