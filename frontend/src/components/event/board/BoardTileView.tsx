import {useEffect, useState} from 'react'
import {Box, Fade} from '@mui/material'
import {BoardTile, BoardViewDto} from '@api/types.gen'
import {tileBackground} from './boardView'
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

    // Die konfigurierte Signalfarbe des aktiven Elements — als rgba-Hintergrund der
    // Zelle, damit die Deckkraft nur die Farbe dämpft und nie den Inhalt ausbleicht.
    // Rotieren mehrere Elemente, wechselt die Farbe mit dem Element im selben Fade.
    const background = tileBackground(element.backgroundColor, element.backgroundOpacity)

    return (
        <Box sx={{height: '100%', minHeight: 0, position: 'relative'}}>
            <Fade key={index % count} in timeout={600}>
                <Box
                    sx={{
                        height: '100%',
                        minHeight: 0,
                        backgroundColor: background,
                        borderRadius: background ? 1 : 0,
                        // Der Lauf-Rahmen (AthleteBoardColumnCard) ist eine deckende
                        // MUI-Card und würde die Kachelfarbe verdecken — mit gesetzter
                        // Farbe wird er durchsichtig, sein Rahmen bleibt.
                        ...(background
                            ? {'& .MuiCard-root': {backgroundColor: 'transparent'}}
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
