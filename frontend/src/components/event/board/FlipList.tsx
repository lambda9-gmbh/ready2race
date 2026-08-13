import {ReactNode, useLayoutEffect, useRef} from 'react'

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
}

const TRANSITION_MS = 350

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
 */
function FlipList<T>({items, keyOf, render, axis = 'y', enterOffset = 24}: FlipListProps<T>) {
    const nodes = useRef(new Map<string, HTMLDivElement>())
    const lastPositions = useRef(new Map<string, number>())

    useLayoutEffect(() => {
        const keysNow = new Set(items.map(keyOf))

        items.forEach(item => {
            const key = keyOf(item)
            const node = nodes.current.get(key)
            if (!node) return

            const rect = node.getBoundingClientRect()
            const current = axis === 'y' ? rect.top : rect.left
            const previous = lastPositions.current.get(key)
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

        // Verwaiste Positionen räumen: fällt ein Eintrag aus der Liste und kommt später
        // zurück, soll er wieder als NEU hereinschieben statt von einer längst
        // vergangenen Stelle aus zu springen.
        for (const key of lastPositions.current.keys()) {
            if (!keysNow.has(key)) lastPositions.current.delete(key)
        }
    })

    return (
        <>
            {items.map((item, index) => {
                const key = keyOf(item)
                return (
                    <div
                        key={key}
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
