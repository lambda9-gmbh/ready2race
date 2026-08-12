import {useState} from 'react'
import {
    Box,
    Button,
    Card,
    Checkbox,
    DialogActions,
    DialogContent,
    DialogTitle,
    FormControlLabel,
    IconButton,
    MenuItem,
    Slider,
    Stack,
    TextField,
    ToggleButton,
    ToggleButtonGroup,
    Tooltip,
    Typography,
} from '@mui/material'
import {
    Add as AddIcon,
    ArrowDownward as ArrowDownwardIcon,
    ArrowUpward as ArrowUpwardIcon,
    Delete as DeleteIcon,
} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import {useFetch} from '@utils/hooks'
import {getAwardCeremonies} from '@api/sdk.gen'
import {
    BoardConfig,
    BoardDto,
    BoardElement,
    BoardElementType,
    BoardListMode,
    BoardRequest,
    BoardScheduleMode,
    BoardTile,
} from '@api/types.gen'
import {gridPlacement, hasMatchDetail, tileColor} from './boardView'

/** Grenzen wie im Backend (BoardLimits) — die Maske soll zeigen, was tatsächlich gilt. */
const MAX_OFFSET = 6
const MIN_ROTATION_SECONDS = 3
const MIN_REFRESH_SECONDS = 3
const MAX_COLUMNS = 4
// 16, damit ein volles 4×4-Raster aus 1×1-Kacheln möglich ist (wie BoardLimits.MAX_TILES).
const MAX_TILES = 16
const MAX_ROW_SPAN = 3

const defaultElement = (): BoardElement => ({
    type: 'MATCH',
    offset: 0,
    showCrew: true,
    showCountdown: true,
    showTimes: true,
    contrastColors: true,
    autoFit: true,
})

const defaultTile = (): BoardTile => ({
    rotationIntervalSeconds: 10,
    colSpan: 1,
    rowSpan: 1,
    elements: [defaultElement()],
})

/** Beim Typwechsel bekommt das Element die Vorgaben seines neuen Typs — keine Restfelder. */
const elementForType = (type: BoardElementType): BoardElement => {
    switch (type) {
        case 'MATCH':
            return defaultElement()
        case 'MATCH_DETAIL':
            // Sprecher-Kachel: nur die Slot-Wahl — alle Details sind dort immer an.
            return {type, offset: 0}
        case 'MATCH_LIST':
            return {type, listMode: 'UPCOMING', limit: 10, useShortNames: false}
        case 'CLOCK':
            return {type, showEventName: true}
        case 'DELAY':
            // Die Verspätung rechnet der Server — das Element hat keine Optionen.
            return {type}
        case 'TEXT':
            return {type, text: ''}
        case 'AWARD_CEREMONY':
            // Die Ehrung wählt das Formular; ohne Auswahl lehnt der Server das Board ab.
            return {type}
    }
}

interface BoardEditorProps {
    eventId: string
    board: BoardDto | null
    onSubmit: (request: BoardRequest) => void
    onCancel: () => void
}

/**
 * Der Board-Editor: Spaltenzahl wählen, Kacheln mit Breite/Höhe ins Raster legen,
 * Elemente konfigurieren. Die Kachel-Karten stehen im selben Raster wie auf dem
 * Bildschirm ([gridPlacement]) — der Editor ist damit zugleich die Vorschau der
 * Anordnung. Bewusst kontrollierter State statt react-hook-form: die Struktur ist
 * verschachtelt und dynamisch, gespeichert wird immer das ganze Board.
 */
const BoardEditor = ({eventId, board, onSubmit, onCancel}: BoardEditorProps) => {
    const {t} = useTranslation()

    const [name, setName] = useState(board?.name ?? '')

    // Die wählbaren Ehrungen (je Wettkampf und Wertung eine) — nur im Editor geladen,
    // die öffentliche Anzeige bekommt die aufgelösten Podien über die Board-Antwort.
    const {data: ceremonies} = useFetch(signal => getAwardCeremonies({signal, path: {eventId}}), {
        deps: [eventId],
    })
    const [config, setConfig] = useState<BoardConfig>(
        board?.config ?? {
            columns: 3,
            refreshIntervalSeconds: 15,
            tiles: [defaultTile(), defaultTile(), defaultTile()],
        },
    )

    const columns = config.columns ?? 3

    const changeColumns = (value: number) =>
        setConfig({
            ...config,
            columns: value,
            // Breiter als das Raster geht nicht — betroffene Kacheln einkürzen.
            tiles: config.tiles.map(tile => ({
                ...tile,
                colSpan: Math.min(tile.colSpan ?? 1, value),
            })),
        })

    const updateTile = (index: number, tile: BoardTile) =>
        setConfig({...config, tiles: config.tiles.map((old, i) => (i === index ? tile : old))})

    const removeTile = (index: number) =>
        setConfig({...config, tiles: config.tiles.filter((_, i) => i !== index)})

    const moveTile = (index: number, direction: -1 | 1) => {
        const target = index + direction
        if (target < 0 || target >= config.tiles.length) return
        const tiles = [...config.tiles]
        ;[tiles[index], tiles[target]] = [tiles[target], tiles[index]]
        setConfig({...config, tiles})
    }

    const updateElement = (tileIndex: number, elementIndex: number, element: BoardElement) => {
        const tile = config.tiles[tileIndex]
        updateTile(tileIndex, {
            ...tile,
            elements: tile.elements.map((old, i) => (i === elementIndex ? element : old)),
        })
    }

    const moveElement = (tileIndex: number, elementIndex: number, direction: -1 | 1) => {
        const tile = config.tiles[tileIndex]
        const target = elementIndex + direction
        if (target < 0 || target >= tile.elements.length) return
        const elements = [...tile.elements]
        ;[elements[elementIndex], elements[target]] = [elements[target], elements[elementIndex]]
        updateTile(tileIndex, {...tile, elements})
    }

    const removeElement = (tileIndex: number, elementIndex: number) => {
        const tile = config.tiles[tileIndex]
        updateTile(tileIndex, {
            ...tile,
            elements: tile.elements.filter((_, i) => i !== elementIndex),
        })
    }

    const placement = gridPlacement(config.tiles, columns)

    // Der einzige ungültige Zustand, den der Editor überhaupt erreichen lässt: ein leeres
    // TEXT-Element (alle anderen Felder haben gültige Vorgaben oder begrenzte Eingaben). Der
    // Server lehnt das mit 422 ab; hier wird der Speichern-Knopf so lange gesperrt.
    const hasBlankText = config.tiles.some(tile =>
        tile.elements.some(el => el.type === 'TEXT' && (el.text ?? '').trim() === ''),
    )

    const offsetLabel = (offset: number) =>
        offset === 0
            ? t('event.boards.element.offsetCurrent')
            : offset > 0
              ? t('event.boards.element.offsetAfter', {count: offset})
              : t('event.boards.element.offsetBefore', {count: -offset})

    const booleanOption = (
        tileIndex: number,
        elementIndex: number,
        element: BoardElement,
        field:
            | 'showCrew'
            | 'showCountdown'
            | 'showTimes'
            | 'contrastColors'
            | 'autoFit'
            | 'showCrewDetails'
            | 'showBirthYears'
            | 'showAdvancement'
            | 'showRegisteringClub',
        // Die Sprecherinnen-Optionen sind bewusst standardmäßig aus (Zusatzdaten nur auf
        // Anforderung); die Anzeige-Optionen standardmäßig an.
        defaultOn: boolean = true,
    ) => (
        <FormControlLabel
            key={field}
            control={
                <Checkbox
                    size="small"
                    checked={defaultOn ? element[field] !== false : element[field] === true}
                    onChange={e =>
                        updateElement(tileIndex, elementIndex, {
                            ...element,
                            [field]: e.target.checked,
                        })
                    }
                />
            }
            label={t(`event.boards.element.${field}`)}
        />
    )

    const renderElement = (tileIndex: number, elementIndex: number, element: BoardElement) => (
        <Box
            key={elementIndex}
            sx={{
                // Die Vorschau der Färbung — derselbe Helfer wie auf der Bühne
                // (tileColor), damit Editor und Anzeige dasselbe zeigen: Fläche als
                // Hintergrund, Rand als dickere Umrandung anstelle der grauen.
                border: tileColor(element.borderColor) ? '3px solid' : '1px solid',
                borderColor: tileColor(element.borderColor) ?? 'divider',
                borderRadius: 1,
                p: 1.5,
                backgroundColor: tileColor(element.backgroundColor),
            }}>
            <Stack direction="row" alignItems="center" gap={1} sx={{mb: 1}}>
                <TextField
                    select
                    size="small"
                    sx={{minWidth: 160}}
                    label={t('event.boards.element.type.label')}
                    value={element.type}
                    onChange={e =>
                        updateElement(
                            tileIndex,
                            elementIndex,
                            elementForType(e.target.value as BoardElementType),
                        )
                    }>
                    <MenuItem value="MATCH">{t('event.boards.element.type.match')}</MenuItem>
                    {/* Die Sprecher-Kachel gilt nur als einzige Kachel des Boards
                        (Backend-Validierung) — mit Nachbarn taucht sie gar nicht erst
                        in der Auswahl auf. */}
                    {(config.tiles.length === 1 || element.type === 'MATCH_DETAIL') && (
                        <MenuItem value="MATCH_DETAIL">
                            {t('event.boards.element.type.matchDetail')}
                        </MenuItem>
                    )}
                    <MenuItem value="MATCH_LIST">
                        {t('event.boards.element.type.matchList')}
                    </MenuItem>
                    <MenuItem value="CLOCK">{t('event.boards.element.type.clock')}</MenuItem>
                    <MenuItem value="DELAY">{t('event.boards.element.type.delay')}</MenuItem>
                    <MenuItem value="TEXT">{t('event.boards.element.type.text')}</MenuItem>
                    <MenuItem value="AWARD_CEREMONY">
                        {t('event.boards.element.type.awardCeremony')}
                    </MenuItem>
                </TextField>
                <Box sx={{flex: 1}} />
                <IconButton
                    size="small"
                    disabled={elementIndex === 0}
                    onClick={() => moveElement(tileIndex, elementIndex, -1)}>
                    <ArrowUpwardIcon fontSize="small" />
                </IconButton>
                <IconButton
                    size="small"
                    disabled={elementIndex === config.tiles[tileIndex].elements.length - 1}
                    onClick={() => moveElement(tileIndex, elementIndex, 1)}>
                    <ArrowDownwardIcon fontSize="small" />
                </IconButton>
                <IconButton
                    size="small"
                    disabled={config.tiles[tileIndex].elements.length === 1}
                    onClick={() => removeElement(tileIndex, elementIndex)}>
                    <DeleteIcon fontSize="small" />
                </IconButton>
            </Stack>

            {element.type === 'MATCH' && (
                <Stack gap={1}>
                    <TextField
                        select
                        size="small"
                        label={t('event.boards.element.offset')}
                        value={element.offset ?? 0}
                        onChange={e =>
                            updateElement(tileIndex, elementIndex, {
                                ...element,
                                offset: Number(e.target.value),
                            })
                        }>
                        {Array.from({length: MAX_OFFSET * 2 + 1}, (_, i) => i - MAX_OFFSET).map(
                            offset => (
                                <MenuItem key={offset} value={offset}>
                                    {offsetLabel(offset)}
                                </MenuItem>
                            ),
                        )}
                    </TextField>
                    <Box>
                        {(
                            [
                                'showCrew',
                                'showCountdown',
                                'showTimes',
                                'contrastColors',
                                'autoFit',
                            ] as const
                        ).map(field => booleanOption(tileIndex, elementIndex, element, field))}
                    </Box>
                    <Box>
                        <Typography variant="caption" color="text.secondary">
                            {t('event.boards.element.announcerSection')}
                        </Typography>
                        <Box>
                            {(
                                [
                                    'showCrewDetails',
                                    'showBirthYears',
                                    'showAdvancement',
                                    'showRegisteringClub',
                                ] as const
                            ).map(field =>
                                booleanOption(tileIndex, elementIndex, element, field, false),
                            )}
                        </Box>
                    </Box>
                </Stack>
            )}

            {element.type === 'MATCH_DETAIL' && (
                // Nur die Slot-Wahl wie bei MATCH — die Sprecher-Kachel kennt keine
                // Abschalt-Optionen, sie zeigt immer die volle Detailtiefe.
                <TextField
                    select
                    size="small"
                    label={t('event.boards.element.offset')}
                    value={element.offset ?? 0}
                    onChange={e =>
                        updateElement(tileIndex, elementIndex, {
                            ...element,
                            offset: Number(e.target.value),
                        })
                    }>
                    {Array.from({length: MAX_OFFSET * 2 + 1}, (_, i) => i - MAX_OFFSET).map(
                        offset => (
                            <MenuItem key={offset} value={offset}>
                                {offsetLabel(offset)}
                            </MenuItem>
                        ),
                    )}
                </TextField>
            )}

            {element.type === 'MATCH_LIST' && (
                <Stack gap={1}>
                    <TextField
                        select
                        size="small"
                        label={t('event.boards.element.listMode.label')}
                        value={element.listMode ?? 'UPCOMING'}
                        onChange={e =>
                            updateElement(tileIndex, elementIndex, {
                                ...element,
                                listMode: e.target.value as BoardListMode,
                                // scheduleMode gehört nur zum Tagesprogramm — beim Wechsel
                                // auf eine andere Liste abräumen, sonst lehnt die
                                // Backend-Validierung die Konfiguration ab.
                                scheduleMode:
                                    e.target.value === 'SCHEDULE' ? element.scheduleMode : undefined,
                            })
                        }>
                        <MenuItem value="UPCOMING">
                            {t('event.boards.element.listMode.upcoming')}
                        </MenuItem>
                        <MenuItem value="RESULTS">
                            {t('event.boards.element.listMode.results')}
                        </MenuItem>
                        <MenuItem value="RUNNING">
                            {t('event.boards.element.listMode.running')}
                        </MenuItem>
                        <MenuItem value="SCHEDULE">
                            {t('event.boards.element.listMode.schedule')}
                        </MenuItem>
                    </TextField>
                    {/* Nur das Tagesprogramm hat zwei Zuschnitt-Modi: mitlaufendes Fenster
                        um „jetzt" (FOLLOW, Default und Alt-Verhalten) oder der ganze Tag,
                        wobei die Kachel scrollt (FULL). */}
                    {element.listMode === 'SCHEDULE' && (
                        <TextField
                            select
                            size="small"
                            label={t('event.boards.element.scheduleMode.label')}
                            value={element.scheduleMode ?? 'FOLLOW'}
                            onChange={e =>
                                updateElement(tileIndex, elementIndex, {
                                    ...element,
                                    scheduleMode: e.target.value as BoardScheduleMode,
                                })
                            }>
                            <MenuItem value="FOLLOW">
                                {t('event.boards.element.scheduleMode.follow')}
                            </MenuItem>
                            <MenuItem value="FULL">
                                {t('event.boards.element.scheduleMode.full')}
                            </MenuItem>
                        </TextField>
                    )}
                    <FormControlLabel
                        control={
                            <Checkbox
                                size="small"
                                checked={element.useShortNames === true}
                                onChange={e =>
                                    updateElement(tileIndex, elementIndex, {
                                        ...element,
                                        useShortNames: e.target.checked,
                                    })
                                }
                            />
                        }
                        label={t('event.boards.element.useShortNames')}
                    />
                    {/* Bei FULL wird das Limit ausgeblendet statt genullt: der Wert bleibt
                        gespeichert (das Backend verlangt ihn ohnehin) und gilt wieder,
                        sobald jemand auf FOLLOW zurückwechselt. */}
                    {!(element.listMode === 'SCHEDULE' && element.scheduleMode === 'FULL') && (
                        <Box>
                            <Typography variant="caption" color="text.secondary">
                                {t('event.boards.element.limit')}: {element.limit ?? 10}
                            </Typography>
                            <Slider
                                size="small"
                                value={element.limit ?? 10}
                                min={1}
                                max={20}
                                onChange={(_, value) =>
                                    updateElement(tileIndex, elementIndex, {
                                        ...element,
                                        limit: value as number,
                                    })
                                }
                            />
                        </Box>
                    )}
                </Stack>
            )}

            {element.type === 'AWARD_CEREMONY' && (
                <TextField
                    select
                    size="small"
                    fullWidth
                    label={t('event.boards.element.ceremonyPick')}
                    value={
                        element.competitionId
                            ? `${element.competitionId}|${element.ratingCategoryId ?? ''}`
                            : ''
                    }
                    onChange={e => {
                        const [competitionId, ratingCategoryId] = e.target.value.split('|')
                        updateElement(tileIndex, elementIndex, {
                            ...element,
                            competitionId,
                            ratingCategoryId: ratingCategoryId || undefined,
                        })
                    }}>
                    {(ceremonies ?? []).map(choice => (
                        <MenuItem
                            key={`${choice.competitionId}|${choice.ratingCategoryId ?? ''}`}
                            value={`${choice.competitionId}|${choice.ratingCategoryId ?? ''}`}>
                            {[
                                choice.competitionIdentifier,
                                choice.competitionShortName ?? choice.competitionName,
                                choice.ratingCategoryName,
                            ]
                                .filter(Boolean)
                                .join(' · ')}
                        </MenuItem>
                    ))}
                </TextField>
            )}

            {element.type === 'CLOCK' && (
                <FormControlLabel
                    control={
                        <Checkbox
                            size="small"
                            checked={element.showEventName !== false}
                            onChange={e =>
                                updateElement(tileIndex, elementIndex, {
                                    ...element,
                                    showEventName: e.target.checked,
                                })
                            }
                        />
                    }
                    label={t('event.boards.element.showEventName')}
                />
            )}

            {element.type === 'TEXT' && (
                <TextField
                    fullWidth
                    multiline
                    minRows={2}
                    size="small"
                    label={t('event.boards.element.text')}
                    value={element.text ?? ''}
                    onChange={e =>
                        updateElement(tileIndex, elementIndex, {...element, text: e.target.value})
                    }
                    // Leerer Text ist serverseitig ungültig; ohne diese Markierung sperrte er nur
                    // stumm das Speichern (der Knopf unten ist dann deaktiviert).
                    error={(element.text ?? '').trim() === ''}
                    helperText={
                        (element.text ?? '').trim() === ''
                            ? t('event.boards.element.textRequired')
                            : undefined
                    }
                />
            )}

            {/* Signalfarben der Kachel — für jeden Elementtyp erlaubt (z. B. rot für
                „Letztes Ergebnis", grün für „Im Rennen"). Fläche und Rand unabhängig
                voneinander; native Farbwahl in MUI-Verpackung, ohne Farbe bleibt das
                bisherige Aussehen. */}
            <Stack gap={0.5} sx={{mt: 1.5}}>
                <Stack direction="row" alignItems="center" gap={1} flexWrap="wrap">
                    <TextField
                        type="color"
                        size="small"
                        sx={{width: 110}}
                        label={t('event.boards.element.backgroundColor')}
                        // Der native Farbwähler kennt nur die #RRGGBB-Langform; ohne
                        // gesetzte Farbe zeigt er Weiß, gespeichert wird erst die Wahl.
                        value={element.backgroundColor ?? '#ffffff'}
                        onChange={e =>
                            updateElement(tileIndex, elementIndex, {
                                ...element,
                                backgroundColor: e.target.value,
                            })
                        }
                    />
                    {element.backgroundColor != null && (
                        <Button
                            size="small"
                            onClick={() =>
                                updateElement(tileIndex, elementIndex, {
                                    ...element,
                                    backgroundColor: undefined,
                                })
                            }>
                            {t('event.boards.element.colorRemove')}
                        </Button>
                    )}
                    <TextField
                        type="color"
                        size="small"
                        sx={{width: 110}}
                        label={t('event.boards.element.borderColor')}
                        value={element.borderColor ?? '#ffffff'}
                        onChange={e =>
                            updateElement(tileIndex, elementIndex, {
                                ...element,
                                borderColor: e.target.value,
                            })
                        }
                    />
                    {element.borderColor != null && (
                        <Button
                            size="small"
                            onClick={() =>
                                updateElement(tileIndex, elementIndex, {
                                    ...element,
                                    borderColor: undefined,
                                })
                            }>
                            {t('event.boards.element.colorRemove')}
                        </Button>
                    )}
                </Stack>
                {/* Bewusst nur ein Hinweis statt einer Kontrast-Automatik. */}
                {element.backgroundColor != null && (
                    <Typography variant="caption" color="text.secondary" component="div">
                        {t('event.boards.element.backgroundColorHint')}
                    </Typography>
                )}
            </Stack>
        </Box>
    )

    return (
        <>
            <DialogTitle>
                {board ? t('event.boards.edit') : t('event.boards.create')}
            </DialogTitle>
            <DialogContent>
                <Stack gap={3} sx={{pt: 1}}>
                    <TextField
                        label={t('event.boards.name')}
                        value={name}
                        onChange={e => setName(e.target.value)}
                        required
                        fullWidth
                    />

                    <Stack direction="row" gap={4} alignItems="center" flexWrap="wrap">
                        <Box>
                            <Typography gutterBottom>{t('event.boards.columns')}</Typography>
                            <ToggleButtonGroup
                                exclusive
                                size="small"
                                value={columns}
                                onChange={(_, value) => value && changeColumns(value as number)}>
                                {Array.from({length: MAX_COLUMNS}, (_, i) => i + 1).map(n => (
                                    <ToggleButton key={n} value={n}>
                                        {n}
                                    </ToggleButton>
                                ))}
                            </ToggleButtonGroup>
                        </Box>
                        <FormControlLabel
                            control={
                                <Checkbox
                                    checked={config.showHeader !== false}
                                    onChange={e =>
                                        setConfig({...config, showHeader: e.target.checked})
                                    }
                                />
                            }
                            label={t('event.boards.showHeader')}
                        />
                        <Box sx={{minWidth: 220, flex: 1}}>
                            <Typography gutterBottom>
                                {t('event.boards.refreshInterval')}:{' '}
                                {config.refreshIntervalSeconds ?? 15}s
                            </Typography>
                            <Slider
                                value={config.refreshIntervalSeconds ?? 15}
                                min={MIN_REFRESH_SECONDS}
                                max={60}
                                step={5}
                                onChange={(_, value) =>
                                    setConfig({...config, refreshIntervalSeconds: value as number})
                                }
                            />
                        </Box>
                    </Stack>

                    {/* Die Kacheln im selben Raster wie auf dem Bildschirm — der Editor
                        ist damit zugleich die Vorschau der Anordnung. Wie die Bühne gilt
                        das Raster auf jeder Viewportbreite, ohne Breakpoint-Fallback. */}
                    <Box
                        sx={{
                            display: 'grid',
                            gap: 2,
                            gridTemplateColumns: `repeat(${columns}, minmax(0, 1fr))`,
                        }}>
                        {config.tiles.map((tile, tileIndex) => {
                            const position = placement.positions[tileIndex]
                            return (
                                <Card
                                    key={tileIndex}
                                    variant="outlined"
                                    sx={{
                                        p: 1.5,
                                        gridColumn: `${position.column} / span ${position.colSpan}`,
                                        gridRow: `${position.row} / span ${position.rowSpan}`,
                                    }}>
                                    <Stack gap={1.5}>
                                        <Stack direction="row" alignItems="center" gap={1}>
                                            <Typography variant="subtitle2" sx={{flex: 1}}>
                                                {t('event.boards.tile.title', {
                                                    index: tileIndex + 1,
                                                })}
                                            </Typography>
                                            <IconButton
                                                size="small"
                                                disabled={tileIndex === 0}
                                                onClick={() => moveTile(tileIndex, -1)}>
                                                <ArrowUpwardIcon fontSize="small" />
                                            </IconButton>
                                            <IconButton
                                                size="small"
                                                disabled={tileIndex === config.tiles.length - 1}
                                                onClick={() => moveTile(tileIndex, 1)}>
                                                <ArrowDownwardIcon fontSize="small" />
                                            </IconButton>
                                            <IconButton
                                                size="small"
                                                disabled={config.tiles.length === 1}
                                                onClick={() => removeTile(tileIndex)}>
                                                <DeleteIcon fontSize="small" />
                                            </IconButton>
                                        </Stack>

                                        <Stack direction="row" gap={1}>
                                            <TextField
                                                select
                                                size="small"
                                                sx={{flex: 1}}
                                                label={t('event.boards.tile.width')}
                                                value={Math.min(tile.colSpan ?? 1, columns)}
                                                onChange={e =>
                                                    updateTile(tileIndex, {
                                                        ...tile,
                                                        colSpan: Number(e.target.value),
                                                    })
                                                }>
                                                {Array.from({length: columns}, (_, i) => i + 1).map(
                                                    n => (
                                                        <MenuItem key={n} value={n}>
                                                            {n}
                                                        </MenuItem>
                                                    ),
                                                )}
                                            </TextField>
                                            <TextField
                                                select
                                                size="small"
                                                sx={{flex: 1}}
                                                label={t('event.boards.tile.height')}
                                                value={tile.rowSpan ?? 1}
                                                onChange={e =>
                                                    updateTile(tileIndex, {
                                                        ...tile,
                                                        rowSpan: Number(e.target.value),
                                                    })
                                                }>
                                                {Array.from(
                                                    {length: MAX_ROW_SPAN},
                                                    (_, i) => i + 1,
                                                ).map(n => (
                                                    <MenuItem key={n} value={n}>
                                                        {n}
                                                    </MenuItem>
                                                ))}
                                            </TextField>
                                        </Stack>

                                        {tile.elements.map((element, elementIndex) =>
                                            renderElement(tileIndex, elementIndex, element),
                                        )}

                                        {tile.elements.length > 1 && (
                                            <TextField
                                                type="number"
                                                size="small"
                                                label={t('event.boards.tile.rotationInterval')}
                                                value={tile.rotationIntervalSeconds ?? 10}
                                                onChange={e => {
                                                    const value = Number(e.target.value)
                                                    if (Number.isFinite(value)) {
                                                        updateTile(tileIndex, {
                                                            ...tile,
                                                            rotationIntervalSeconds: Math.max(
                                                                MIN_ROTATION_SECONDS,
                                                                Math.round(value),
                                                            ),
                                                        })
                                                    }
                                                }}
                                                inputProps={{min: MIN_ROTATION_SECONDS}}
                                            />
                                        )}

                                        <Button
                                            size="small"
                                            startIcon={<AddIcon />}
                                            onClick={() =>
                                                updateTile(tileIndex, {
                                                    ...tile,
                                                    elements: [...tile.elements, defaultElement()],
                                                })
                                            }>
                                            {t('event.boards.tile.addElement')}
                                        </Button>
                                    </Stack>
                                </Card>
                            )
                        })}
                    </Box>

                    {/* Solange eine Sprecher-Kachel existiert, gibt es keine zweite Kachel —
                        der Tooltip erklärt das, statt den Knopf wortlos zu sperren. Das span
                        ist nötig, weil ein disabled-Button keine Hover-Events feuert. */}
                    <Tooltip
                        title={
                            hasMatchDetail(config.tiles)
                                ? t('event.boards.tile.addDisabledMatchDetail')
                                : ''
                        }>
                        <span>
                            <Button
                                startIcon={<AddIcon />}
                                disabled={
                                    config.tiles.length >= MAX_TILES || hasMatchDetail(config.tiles)
                                }
                                onClick={() =>
                                    setConfig({...config, tiles: [...config.tiles, defaultTile()]})
                                }>
                                {t('event.boards.tile.add')}
                            </Button>
                        </span>
                    </Tooltip>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onCancel}>{t('common.cancel')}</Button>
                <Button
                    variant="contained"
                    disabled={name.trim() === '' || hasBlankText}
                    onClick={() => onSubmit({name: name.trim(), config})}>
                    {board ? t('common.update') : t('common.create')}
                </Button>
            </DialogActions>
        </>
    )
}

export default BoardEditor
