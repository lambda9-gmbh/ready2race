import {useEffect, useState} from 'react'
import {Box, Fade} from '@mui/material'
import {BoardTile, BoardViewDto} from '@api/types.gen'
import BoardElementView from './BoardElementView'

const DEFAULT_ROTATION_SECONDS = 10

interface BoardTileViewProps {
    tile: BoardTile
    view: BoardViewDto
    now: Date
    columns: number
    rows: number
}

/**
 * Eine Kachel des Boards. Enthält sie mehrere Elemente, rotiert sie clientseitig durch
 * — der Server weiß von der Rotation nichts, er liefert die Daten aller Elemente in
 * jeder Antwort mit.
 */
const BoardTileView = ({tile, view, now, columns, rows}: BoardTileViewProps) => {
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

    return (
        <Box sx={{minHeight: 0, position: 'relative'}}>
            <Fade key={index % count} in timeout={600}>
                <Box sx={{height: '100%', minHeight: 0}}>
                    <BoardElementView element={element} view={view} now={now} columns={columns} rows={rows} />
                </Box>
            </Fade>
        </Box>
    )
}

export default BoardTileView
