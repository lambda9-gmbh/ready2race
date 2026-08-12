import {Box} from '@mui/material'
import {BoardViewDto} from '@api/types.gen'
import {boardColumns, gridPlacement, rowSizes} from './boardView'
import BoardTileView from './BoardTileView'
import EventNoticeBanner from '@components/eventNotice/EventNoticeBanner.tsx'

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
    // Sprecher-Boards erzwingen ein 1-Spalten-Raster, egal was konfiguriert ist —
    // Begründung und Bedingung bei [boardColumns] (heilt Fehlkonfigurationen im Lesen).
    const columns = boardColumns(view.config)
    const {rows, positions} = gridPlacement(view.config.tiles, columns)

    // Kompakte Zeilen (nur Uhr/Verspätung) schrumpfen auf Inhaltshöhe ('auto'),
    // Inhalts-Zeilen teilen sich den Rest als 1fr — eine Zeile mit nur einer Uhr
    // verschwendete sonst ein Drittel des Bildschirms (Nutzer-Screenshot 12.08.2026).
    // Trägt ein Board ausschließlich Kompakt-Kacheln, sind alle Zeilen 'auto' und der
    // Rest der Höhe bleibt schlicht leer — nichts bläst sich auf 100% auf.
    const sizes = rowSizes(view.config.tiles, positions, rows)
    // Für die Dichteformel zählt nur die Höhe, die wirklich verteilt wird: der
    // Höhenanteil einer Kachel rechnet über die 1fr-Zeilen. Auf einem Board ohne
    // jede Kompakt-Zeile ist das exakt das alte rowSpan/rows; Kacheln, die nur in
    // Kompakt-Zeilen liegen (Uhr/Verspätung), lesen den Wert ohnehin nie — die
    // clamp()-Schriften kennen keine Dichteformel.
    const frRows = sizes.filter(size => size === '1fr').length

    return (
        <Box
            sx={{
                height: '100%',
                minHeight: 0,
                display: 'flex',
                flexDirection: 'column',
            }}>
            {/* Der veranstaltungsweite Hinweis über dem Raster, schmal — die Kacheln darunter
                teilen sich die restliche Höhe, das immer aktive Raster bleibt gewahrt. */}
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
                    gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
                    // minmax(0, …) nur für die Inhalts-Zeilen: deren Zellen scrollen
                    // innen und dürfen deshalb unter ihre Inhaltshöhe schrumpfen.
                    gridTemplateRows: sizes
                        .map(size => (size === '1fr' ? 'minmax(0, 1fr)' : 'auto'))
                        .join(' '),
                    overflow: 'hidden',
                }}>
                {view.config.tiles.map((tile, index) => {
                    const position = positions[index]
                    // Anteil an den 1fr-Zeilen (siehe frRows oben); für Kacheln ohne
                    // 1fr-Anteil bleibt pragmatisch 1 stehen — dort leben nur
                    // Uhr/Verspätung, die den Wert nicht lesen.
                    const spannedFr = sizes
                        .slice(position.row - 1, position.row - 1 + position.rowSpan)
                        .filter(size => size === '1fr').length
                    const heightFraction = frRows > 0 && spannedFr > 0 ? spannedFr / frRows : 1
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
                                heightFraction={heightFraction}
                            />
                        </Box>
                    )
                })}
            </Box>
        </Box>
    )
}

export default BoardRenderer
