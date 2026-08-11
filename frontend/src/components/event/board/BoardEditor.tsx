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
    Typography,
} from '@mui/material'
import {
    Add as AddIcon,
    ArrowDownward as ArrowDownwardIcon,
    ArrowUpward as ArrowUpwardIcon,
    Delete as DeleteIcon,
} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import {
    BoardConfig,
    BoardDto,
    BoardElement,
    BoardElementType,
    BoardLayout,
    BoardListMode,
    BoardRequest,
    BoardTile,
} from '@api/types.gen'
import {gridForLayout} from './boardView'

/** Grenzen wie im Backend (BoardLimits) — die Maske soll zeigen, was tatsächlich gilt. */
const MAX_OFFSET = 6
const MIN_ROTATION_SECONDS = 3
const MIN_REFRESH_SECONDS = 10

const LAYOUTS: BoardLayout[] = ['ONE_COLUMN', 'TWO_COLUMNS', 'THREE_COLUMNS', 'SIX_TILES']

const tileCountFor = (layout: BoardLayout): number => {
    const {columns, rows} = gridForLayout(layout)
    return columns * rows
}

const defaultElement = (): BoardElement => ({
    type: 'MATCH',
    offset: 0,
    showCrew: true,
    showCountdown: true,
    showTimes: true,
    contrastColors: true,
    autoFit: true,
})

const defaultTile = (): BoardTile => ({rotationIntervalSeconds: 10, elements: [defaultElement()]})

/** Beim Typwechsel bekommt das Element die Vorgaben seines neuen Typs — keine Restfelder. */
const elementForType = (type: BoardElementType): BoardElement => {
    switch (type) {
        case 'MATCH':
            return defaultElement()
        case 'MATCH_LIST':
            return {type, listMode: 'UPCOMING', limit: 10}
        case 'CLOCK':
            return {type, showEventName: true}
        case 'TEXT':
            return {type, text: ''}
    }
}

interface BoardEditorProps {
    board: BoardDto | null
    onSubmit: (request: BoardRequest) => void
    onCancel: () => void
}

/**
 * Der Board-Editor: Layout wählen, Kacheln füllen, Elemente konfigurieren. Bewusst
 * kontrollierter State statt react-hook-form — die Struktur ist verschachtelt und
 * dynamisch (Kacheln × Elemente), gespeichert wird immer das ganze Board.
 */
const BoardEditor = ({board, onSubmit, onCancel}: BoardEditorProps) => {
    const {t} = useTranslation()

    const [name, setName] = useState(board?.name ?? '')
    const [config, setConfig] = useState<BoardConfig>(
        board?.config ?? {
            layout: 'THREE_COLUMNS',
            refreshIntervalSeconds: 15,
            tiles: [defaultTile(), defaultTile(), defaultTile()],
        },
    )

    const changeLayout = (layout: BoardLayout) => {
        const count = tileCountFor(layout)
        // Bestehende Kacheln bleiben erhalten; fehlende werden mit der Vorgabe gefüllt,
        // überzählige abgeschnitten (der Zuschnitt ist erst mit dem Speichern endgültig).
        const tiles = [
            ...config.tiles.slice(0, count),
            ...Array.from({length: Math.max(0, count - config.tiles.length)}, defaultTile),
        ]
        setConfig({...config, layout, tiles})
    }

    const updateTile = (index: number, tile: BoardTile) =>
        setConfig({...config, tiles: config.tiles.map((old, i) => (i === index ? tile : old))})

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

    const {columns} = gridForLayout(config.layout)

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
        field: 'showCrew' | 'showCountdown' | 'showTimes' | 'contrastColors' | 'autoFit',
    ) => (
        <FormControlLabel
            key={field}
            control={
                <Checkbox
                    size="small"
                    checked={element[field] !== false}
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
            sx={{border: '1px solid', borderColor: 'divider', borderRadius: 1, p: 1.5}}>
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
                    <MenuItem value="MATCH_LIST">
                        {t('event.boards.element.type.matchList')}
                    </MenuItem>
                    <MenuItem value="CLOCK">{t('event.boards.element.type.clock')}</MenuItem>
                    <MenuItem value="TEXT">{t('event.boards.element.type.text')}</MenuItem>
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
                </Stack>
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
                    </TextField>
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
                </Stack>
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

                    <Box>
                        <Typography gutterBottom>{t('event.boards.layout.label')}</Typography>
                        <ToggleButtonGroup
                            exclusive
                            value={config.layout}
                            onChange={(_, value) => value && changeLayout(value as BoardLayout)}>
                            {LAYOUTS.map(layout => (
                                <ToggleButton key={layout} value={layout}>
                                    {t(
                                        `event.boards.layout.${
                                            layout === 'ONE_COLUMN'
                                                ? 'oneColumn'
                                                : layout === 'TWO_COLUMNS'
                                                  ? 'twoColumns'
                                                  : layout === 'THREE_COLUMNS'
                                                    ? 'threeColumns'
                                                    : 'sixTiles'
                                        }`,
                                    )}
                                </ToggleButton>
                            ))}
                        </ToggleButtonGroup>
                    </Box>

                    <Box>
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

                    {/* Die Kacheln im selben Raster wie auf dem Bildschirm — der Editor
                        ist damit zugleich die Vorschau der Anordnung. */}
                    <Box
                        sx={{
                            display: 'grid',
                            gap: 2,
                            gridTemplateColumns: {
                                xs: '1fr',
                                md: `repeat(${columns}, minmax(0, 1fr))`,
                            },
                        }}>
                        {config.tiles.map((tile, tileIndex) => (
                            <Card key={tileIndex} variant="outlined" sx={{p: 1.5}}>
                                <Stack gap={1.5}>
                                    <Typography variant="subtitle2">
                                        {t('event.boards.tile.title', {index: tileIndex + 1})}
                                    </Typography>

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
                        ))}
                    </Box>
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
