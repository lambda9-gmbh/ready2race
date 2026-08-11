import {Box} from '@mui/material'
import {BoardViewDto} from '@api/types.gen'
import {gridPlacement} from './boardView'
import BoardTileView from './BoardTileView'
import EventNoticeBanner from '@components/eventNotice/EventNoticeBanner.tsx'

interface BoardRendererProps {
    view: BoardViewDto
    now: Date
}

/**
 * Das Raster eines Boards: `columns` Spalten, die Kacheln fließen mit ihren
 * Spannweiten (colSpan/rowSpan) in Reihenfolge hinein — dieselbe Anordnung, die der
 * Editor als Vorschau zeigt ([gridPlacement]). Ab lg gilt das Scroll-Verbot der alten
 * Bühne — das Raster passt sich der Höhe an, statt überzulaufen; darunter stapeln die
 * Kacheln und die Seite scrollt wie jede andere.
 */
const BoardRenderer = ({view, now}: BoardRendererProps) => {
    const columns = view.config.columns ?? 3
    const {rows, positions} = gridPlacement(view.config.tiles, columns)

    return (
        <Box
            sx={{
                height: {xs: 'auto', lg: '100%'},
                minHeight: 0,
                display: 'flex',
                flexDirection: 'column',
            }}>
            {/* Der veranstaltungsweite Hinweis über dem Raster, schmal — die Kacheln darunter
                teilen sich die restliche Höhe, das Scroll-Verbot ab lg bleibt gewahrt. */}
            <EventNoticeBanner
                notice={view.notice}
                dense
                sx={{
                    mx: 'clamp(0.5rem, 1vw, 1.5rem)',
                    mt: 'clamp(0.5rem, 1vw, 1.5rem)',
                    justifyContent: 'center',
                }}
            />
            <Box
                sx={{
                    flex: 1,
                    minHeight: 0,
                    display: 'grid',
                    gap: 'clamp(0.4rem, 0.7vw, 1rem)',
                    p: 'clamp(0.5rem, 1vw, 1.5rem)',
                    gridTemplateColumns: {xs: '1fr', lg: `repeat(${columns}, minmax(0, 1fr))`},
                    gridTemplateRows: {xs: 'none', lg: `repeat(${rows}, minmax(0, 1fr))`},
                    overflow: {xs: 'auto', lg: 'hidden'},
                }}>
                {view.config.tiles.map((tile, index) => {
                    const position = positions[index]
                    return (
                        <Box
                            key={index}
                            sx={{
                                minHeight: 0,
                                minWidth: 0,
                                // Explizite Platzierung statt Auto-Flow: so zeigen Bühne und
                                // Editor-Vorschau garantiert dieselbe Anordnung.
                                gridColumn: {
                                    xs: 'auto',
                                    lg: `${position.column} / span ${position.colSpan}`,
                                },
                                gridRow: {
                                    xs: 'auto',
                                    lg: `${position.row} / span ${position.rowSpan}`,
                                },
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
        </Box>
    )
}

export default BoardRenderer
