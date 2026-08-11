import {Box} from '@mui/material'
import {BoardViewDto} from '@api/types.gen'
import {gridPlacement} from './boardView'
import BoardTileView from './BoardTileView'

interface BoardRendererProps {
    view: BoardViewDto
    now: Date
}

/**
 * Das Raster eines Boards: `columns` Spalten, die Kacheln fließen mit ihren
 * Spannweiten (colSpan/rowSpan) in Reihenfolge hinein — dieselbe Anordnung, die der
 * Editor als Vorschau zeigt ([gridPlacement]). Das konfigurierte Raster gilt auf jeder
 * Viewportbreite — es gibt bewusst keinen Breakpoint-Fallback, der die Kacheln stapelt
 * (ein iPad quer lag mit 1180px knapp unter lg und verlor das Raster). Wird eine Zelle
 * inhaltlich zu klein, scrollt sie innen ([overflow: auto] je Zelle), statt dass das
 * Layout kollabiert; für schmale Geräte ist ein eigenes 1-Spalten-Board der Weg.
 */
const BoardRenderer = ({view, now}: BoardRendererProps) => {
    const columns = view.config.columns ?? 3
    const {rows, positions} = gridPlacement(view.config.tiles, columns)

    return (
        <Box
            sx={{
                height: '100%',
                minHeight: 0,
                display: 'grid',
                gap: 'clamp(0.4rem, 0.7vw, 1rem)',
                p: 'clamp(0.5rem, 1vw, 1.5rem)',
                gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
                gridTemplateRows: `repeat(${rows}, minmax(0, 1fr))`,
                overflow: 'hidden',
            }}>
            {view.config.tiles.map((tile, index) => {
                const position = positions[index]
                return (
                    <Box
                        key={index}
                        sx={{
                            minHeight: 0,
                            minWidth: 0,
                            // Passt der Inhalt trotz minimaler Dichte nicht in die Zelle,
                            // scrollt die Zelle selbst — nie die ganze Seite.
                            overflow: 'auto',
                            // Explizite Platzierung statt Auto-Flow: so zeigen Bühne und
                            // Editor-Vorschau garantiert dieselbe Anordnung.
                            gridColumn: `${position.column} / span ${position.colSpan}`,
                            gridRow: `${position.row} / span ${position.rowSpan}`,
                        }}>
                        <BoardTileView
                            tile={tile}
                            view={view}
                            now={now}
                            effectiveColumns={columns / position.colSpan}
                            heightFraction={position.rowSpan / rows}
                        />
                    </Box>
                )
            })}
        </Box>
    )
}

export default BoardRenderer
