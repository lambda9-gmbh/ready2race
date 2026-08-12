import {useEffect, useState} from 'react'
import {Box, Fade} from '@mui/material'
import {BoardTile, BoardViewDto} from '@api/types.gen'
import {tileColor} from './boardView'
import BoardElementView from './BoardElementView'

const DEFAULT_ROTATION_SECONDS = 10

interface BoardTileViewProps {
    tile: BoardTile
    view: BoardViewDto
    now: Date
    /** Breitenanteil der Kachel (Rasterspalten ÷ colSpan) — für die Dichteformel. */
    effectiveColumns: number
    /** Höhenanteil der Kachel (rowSpan ÷ Zeilenzahl) — für die Dichteformel. */
    heightFraction: number
}

/**
 * Eine Kachel des Boards. Enthält sie mehrere Elemente, rotiert sie clientseitig durch
 * — der Server weiß von der Rotation nichts, er liefert die Daten aller Elemente in
 * jeder Antwort mit.
 */
const BoardTileView = ({tile, view, now, effectiveColumns, heightFraction}: BoardTileViewProps) => {
    const [index, setIndex] = useState(0)

    const count = tile.elements.length
    const intervalSeconds =
        tile.rotationIntervalSeconds && tile.rotationIntervalSeconds > 0
            ? tile.rotationIntervalSeconds
            : DEFAULT_ROTATION_SECONDS

    useEffect(() => {
        if (count <= 1) return
        const timer = window.setInterval(() => {
            setIndex(prev => (prev + 1) % count)
        }, intervalSeconds * 1000)
        return () => window.clearInterval(timer)
    }, [count, intervalSeconds])

    if (count === 0) return <Box />

    // Nach einer Umkonfiguration kann der gemerkte Index über das Ende zeigen.
    const element = tile.elements[index % count]

    // Die konfigurierten Signalfarben des aktiven Elements — Fläche und Rand unabhängig
    // voneinander, direkt an der Kachelzelle. Rotieren mehrere Elemente, wechseln die
    // Farben mit dem Element im selben Fade.
    const background = tileColor(element.backgroundColor)
    const border = tileColor(element.borderColor)

    return (
        <Box sx={{height: '100%', minHeight: 0, position: 'relative'}}>
            <Fade key={index % count} in timeout={600}>
                <Box
                    sx={{
                        height: '100%',
                        minHeight: 0,
                        backgroundColor: background,
                        // Deutlich sichtbar als Rahmen der Kachelzelle; box-sizing der
                        // MUI-Box ist border-box, die Zellhöhe bleibt also stehen.
                        border: border ? `3px solid ${border}` : undefined,
                        borderRadius: background || border ? 1 : 0,
                        // Der Lauf-Rahmen (AthleteBoardColumnCard) ist eine deckende
                        // MUI-Card und würde die Kachelfarbe verdecken — mit gesetzter
                        // Fläche wird er durchsichtig, …
                        ...(background
                            ? {'& .MuiCard-root': {backgroundColor: 'transparent'}}
                            : {}),
                        // … und mit gesetztem Rand verschwindet seine eigene graue
                        // Umrandung (nur die Farbe, damit das Layout nicht springt):
                        // der Rand soll die Kachelzelle rahmen, nicht die innere Card
                        // doppelt einfassen.
                        ...(border
                            ? {'& .MuiCard-root': {borderColor: 'transparent'}}
                            : {}),
                    }}>
                    <BoardElementView
                        element={element}
                        view={view}
                        now={now}
                        effectiveColumns={effectiveColumns}
                        heightFraction={heightFraction}
                    />
                </Box>
            </Fade>
        </Box>
    )
}

export default BoardTileView
