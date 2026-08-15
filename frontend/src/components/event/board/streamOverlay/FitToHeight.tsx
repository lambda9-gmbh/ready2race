import {ReactNode, useLayoutEffect, useRef, useState} from 'react'
import {Box} from '@mui/material'
import {fitScale, steadyScale} from './fitScale.ts'

interface FitToHeightProps {
    children: ReactNode
}

/**
 * Der schrumpfbare Block eines Stream-Panels: nimmt den Platz, der nach Kopf- und
 * Unterzeile übrig bleibt, und verkleinert seinen Inhalt so weit, dass er vollständig
 * hineinpasst. Passt er von selbst, bleibt alles in Originalgröße (Maßstab 1).
 *
 * Gemessen wird ausschließlich in Layoutwerten (`clientHeight`/`offsetHeight`) — ein
 * `transform` verändert die nicht, deshalb kann sich der Maßstab nicht selbst aufschaukeln.
 * Die innere Fläche bekommt die Gegenbreite `100 / Maßstab`, damit die Zeilen nach dem
 * Verkleinern wieder exakt die Panelbreite füllen statt rechts eine Lücke zu lassen.
 *
 * Gemessen wird dabei immer bei Originalbreite (100 %): Seit die Crew-Zeilen zweizeilig
 * umbrechen dürfen, hängt die natürliche Höhe an der Breite — eine Messung mit Gegenbreite
 * ließe die Umbrüche wieder verschwinden, die Messung spränge zurück und der Maßstab
 * pendelte endlos zwischen zwei Werten. Bei der breiteren Anzeige kann der Inhalt nur
 * niedriger ausfallen als gemessen, nie höher — er passt also garantiert.
 *
 * Chroma-Regel bleibt gewahrt: skaliert wird per `transform`, es entsteht keine
 * Halbtransparenz, die sich mit der Key-Farbe mischen könnte.
 */
const FitToHeight = ({children}: FitToHeightProps) => {
    const outerRef = useRef<HTMLDivElement>(null)
    const innerRef = useRef<HTMLDivElement>(null)
    const [scale, setScale] = useState(1)

    useLayoutEffect(() => {
        const outer = outerRef.current
        const inner = innerRef.current
        if (!outer || !inner) return

        const measure = () => {
            // Für die Messung kurz zurück auf 100 % Breite (siehe KDoc) — innerhalb des
            // Frames zurückgesetzt, der ResizeObserver sieht die Zwischengröße nicht.
            const styledWidth = inner.style.width
            inner.style.width = '100%'
            const natural = inner.offsetHeight
            inner.style.width = styledWidth
            const next = fitScale(outer.clientHeight, natural)
            setScale(previous => steadyScale(previous, next))
        }

        measure()
        // Beide Seiten können sich unabhängig ändern: die Fläche beim Fensterwechsel
        // (OBS-Quelle in anderer Auflösung), der Inhalt bei jedem Poll mit neuen Zeilen.
        const observer = new ResizeObserver(measure)
        observer.observe(outer)
        observer.observe(inner)
        return () => observer.disconnect()
    }, [])

    return (
        <Box ref={outerRef} sx={{flex: '1 1 auto', minHeight: 0, overflow: 'hidden'}}>
            <Box
                ref={innerRef}
                sx={{
                    transformOrigin: 'top left',
                    transform: scale < 1 ? `scale(${scale})` : undefined,
                    width: scale < 1 ? `${100 / scale}%` : 1,
                }}>
                {children}
            </Box>
        </Box>
    )
}

export default FitToHeight
