import {
    Box,
    Button,
    Divider,
    FormControlLabel,
    MenuItem,
    Paper,
    Select,
    Stack,
    Switch,
    TextField,
    Typography,
} from '@mui/material'
import {GapDocumentPlaceholderType, TextAlign} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {Add} from '@mui/icons-material'
import {clampRect, parsePercent} from './placeholderGeometry.ts'

type PlaceholderData = {
    id: string
    name?: string
    type: GapDocumentPlaceholderType
    page: number
    relLeft: number
    relTop: number
    relWidth: number
    relHeight: number
    textAlign: TextAlign
    fontSize?: number
    bold: boolean
    italic: boolean
    staticText?: string
}

type Props = {
    selectedPlaceholder: string | null
    placeholders: PlaceholderData[]
    allowedTypes: GapDocumentPlaceholderType[]
    onPlaceholdersChange: (placeholders: PlaceholderData[]) => void
    onAddPlaceholder: (type: GapDocumentPlaceholderType, page: number) => void
    currentPage: number
}

const PlaceholderSidebar = (props: Props) => {
    const {t} = useTranslation()
    const selectedPlaceholder = props.placeholders.find(p => p.id === props.selectedPlaceholder)

    const handleAddPlaceholder = (type: GapDocumentPlaceholderType) => {
        props.onAddPlaceholder(type, props.currentPage)
    }

    const handlePlaceholderPropertyChange = (id: string, updates: Partial<PlaceholderData>) => {
        props.onPlaceholdersChange(
            props.placeholders.map(p => (p.id === id ? {...p, ...updates} : p)),
        )
    }

    return (
        <Paper sx={{p: 2, width: 300, height: '70vh', overflow: 'auto'}}>
            <Stack spacing={2}>
                <Typography variant="h6">{t('gap.document.placeholder.available')}</Typography>
                <Typography variant="caption" color="text.secondary">
                    {t('gap.document.placeholder.addInstructions')}
                </Typography>

                <Stack spacing={1}>
                    {props.allowedTypes.map(type => (
                        <Button
                            key={type}
                            variant="outlined"
                            startIcon={<Add />}
                            onClick={() => handleAddPlaceholder(type)}
                            fullWidth
                            sx={{justifyContent: 'flex-start'}}>
                            {t(`gap.document.placeholder.type.${type}`)}
                        </Button>
                    ))}
                </Stack>

                {selectedPlaceholder && (
                    <>
                        <Divider />
                        <Typography variant="h6">
                            {t('gap.document.placeholder.properties')}
                        </Typography>

                        <Box>
                            <Typography
                                variant="caption"
                                color="text.secondary"
                                sx={{mb: 0.5, display: 'block'}}>
                                {t('gap.document.placeholder.type.label')}
                            </Typography>
                            <Typography variant="body2" fontWeight="bold">
                                {t(`gap.document.placeholder.type.${selectedPlaceholder.type}`)}
                            </Typography>
                        </Box>

                        <TextField
                            label={t('gap.document.placeholder.name')}
                            value={selectedPlaceholder.name || ''}
                            onChange={e =>
                                handlePlaceholderPropertyChange(selectedPlaceholder.id, {
                                    name: e.target.value || undefined,
                                })
                            }
                            fullWidth
                            size="small"
                            helperText={t('gap.document.placeholder.nameHelp')}
                        />

                        <Box>
                            <Typography
                                variant="caption"
                                color="text.secondary"
                                sx={{mb: 1, display: 'block'}}>
                                {t('gap.document.placeholder.textAlign')}
                            </Typography>
                            <Select
                                value={selectedPlaceholder.textAlign}
                                onChange={e =>
                                    handlePlaceholderPropertyChange(selectedPlaceholder.id, {
                                        textAlign: e.target.value as TextAlign,
                                    })
                                }
                                fullWidth
                                size="small">
                                <MenuItem value="LEFT">
                                    {t('gap.document.placeholder.align.LEFT')}
                                </MenuItem>
                                <MenuItem value="CENTER">
                                    {t('gap.document.placeholder.align.CENTER')}
                                </MenuItem>
                                <MenuItem value="RIGHT">
                                    {t('gap.document.placeholder.align.RIGHT')}
                                </MenuItem>
                            </Select>
                        </Box>

                        <TextField
                            label={t('gap.document.placeholder.fontSize')}
                            type="number"
                            value={selectedPlaceholder.fontSize ?? ''}
                            onChange={e => {
                                const rawValue = e.target.value
                                if (rawValue === '') {
                                    handlePlaceholderPropertyChange(selectedPlaceholder.id, {
                                        fontSize: undefined,
                                    })
                                    return
                                }
                                const parsedValue = Number(rawValue)
                                if (Number.isNaN(parsedValue)) {
                                    return
                                }
                                handlePlaceholderPropertyChange(selectedPlaceholder.id, {
                                    fontSize: Math.max(1, parsedValue),
                                })
                            }}
                            fullWidth
                            size="small"
                            slotProps={{htmlInput: {min: 1}}}
                            helperText={t('gap.document.placeholder.fontSizeHelp')}
                        />

                        <Stack direction="row" spacing={2}>
                            <FormControlLabel
                                control={
                                    <Switch
                                        checked={selectedPlaceholder.bold}
                                        onChange={e =>
                                            handlePlaceholderPropertyChange(
                                                selectedPlaceholder.id,
                                                {bold: e.target.checked},
                                            )
                                        }
                                    />
                                }
                                label={t('gap.document.placeholder.bold')}
                            />
                            <FormControlLabel
                                control={
                                    <Switch
                                        checked={selectedPlaceholder.italic}
                                        onChange={e =>
                                            handlePlaceholderPropertyChange(
                                                selectedPlaceholder.id,
                                                {italic: e.target.checked},
                                            )
                                        }
                                    />
                                }
                                label={t('gap.document.placeholder.italic')}
                            />
                        </Stack>

                        {selectedPlaceholder.type === 'FREE_TEXT' && (
                            <TextField
                                label={t('gap.document.placeholder.staticText')}
                                value={selectedPlaceholder.staticText || ''}
                                onChange={e =>
                                    handlePlaceholderPropertyChange(selectedPlaceholder.id, {
                                        staticText: e.target.value || undefined,
                                    })
                                }
                                fullWidth
                                multiline
                                size="small"
                                helperText={t('gap.document.placeholder.staticTextHelp')}
                            />
                        )}

                        <Box>
                            <Typography variant="caption" color="text.secondary">
                                {t('gap.document.placeholder.page')}: {selectedPlaceholder.page}
                            </Typography>
                        </Box>

                        <Stack direction="row" spacing={1}>
                            <TextField
                                label={`${t('gap.document.placeholder.positionX')} (%)`}
                                type="number"
                                size="small"
                                value={(selectedPlaceholder.relLeft * 100).toFixed(1)}
                                onChange={e => {
                                    const relLeft = parsePercent(e.target.value)
                                    if (relLeft !== undefined) {
                                        handlePlaceholderPropertyChange(
                                            selectedPlaceholder.id,
                                            clampRect({...selectedPlaceholder, relLeft}),
                                        )
                                    }
                                }}
                            />
                            <TextField
                                label={`${t('gap.document.placeholder.positionY')} (%)`}
                                type="number"
                                size="small"
                                value={(selectedPlaceholder.relTop * 100).toFixed(1)}
                                onChange={e => {
                                    const relTop = parsePercent(e.target.value)
                                    if (relTop !== undefined) {
                                        handlePlaceholderPropertyChange(
                                            selectedPlaceholder.id,
                                            clampRect({...selectedPlaceholder, relTop}),
                                        )
                                    }
                                }}
                            />
                        </Stack>

                        <Stack direction="row" spacing={1}>
                            <TextField
                                label={`${t('gap.document.placeholder.width')} (%)`}
                                type="number"
                                size="small"
                                value={(selectedPlaceholder.relWidth * 100).toFixed(1)}
                                onChange={e => {
                                    const relWidth = parsePercent(e.target.value)
                                    if (relWidth !== undefined) {
                                        handlePlaceholderPropertyChange(
                                            selectedPlaceholder.id,
                                            clampRect({...selectedPlaceholder, relWidth}),
                                        )
                                    }
                                }}
                            />
                            <TextField
                                label={`${t('gap.document.placeholder.height')} (%)`}
                                type="number"
                                size="small"
                                value={(selectedPlaceholder.relHeight * 100).toFixed(1)}
                                onChange={e => {
                                    const relHeight = parsePercent(e.target.value)
                                    if (relHeight !== undefined) {
                                        handlePlaceholderPropertyChange(
                                            selectedPlaceholder.id,
                                            clampRect({...selectedPlaceholder, relHeight}),
                                        )
                                    }
                                }}
                            />
                        </Stack>
                    </>
                )}
            </Stack>
        </Paper>
    )
}

export default PlaceholderSidebar
