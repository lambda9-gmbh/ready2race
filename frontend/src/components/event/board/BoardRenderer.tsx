import {Box} from '@mui/material'
import {BoardViewDto} from '@api/types.gen'
import {gridForLayout} from './boardView'
import BoardTileView from './BoardTileView'

interface BoardRendererProps {
    view: BoardViewDto
    now: Date
}

/**
 * Das Raster eines Boards: 1/2/3 Spalten oder 3×2 Kacheln. Ab lg gilt das
 * Scroll-Verbot der alten Bühne — das Raster passt sich der Höhe an, statt
 * überzulaufen; darunter stapeln die Kacheln und die Seite scrollt wie jede andere.
 */
const BoardRenderer = ({view, now}: BoardRendererProps) => {
    const {columns, rows} = gridForLayout(view.config.layout)

    return (
        <Box
            sx={{
                height: {xs: 'auto', lg: '100%'},
                minHeight: 0,
                display: 'grid',
                gap: 'clamp(0.4rem, 0.7vw, 1rem)',
                p: 'clamp(0.5rem, 1vw, 1.5rem)',
                gridTemplateColumns: {xs: '1fr', lg: `repeat(${columns}, minmax(0, 1fr))`},
                gridTemplateRows: {xs: 'none', lg: `repeat(${rows}, minmax(0, 1fr))`},
                overflow: {xs: 'auto', lg: 'hidden'},
            }}>
            {view.config.tiles.map((tile, index) => (
                <BoardTileView key={index} tile={tile} view={view} now={now} columns={columns} rows={rows} />
            ))}
        </Box>
    )
}

export default BoardRenderer
