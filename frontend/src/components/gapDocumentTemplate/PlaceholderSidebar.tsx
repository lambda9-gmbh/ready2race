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
    TextFieldProps,
    Typography,
} from '@mui/material'
import {GapDocumentPlaceholderType, TextAlign} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {Add} from '@mui/icons-material'
import {useEffect, useState} from 'react'
import {clampRect, parsePercent, PlaceholderRect} from './placeholderGeometry.ts'

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

type GeometryField = keyof PlaceholderRect

const formatPercent = (value: number) => (value * 100).toFixed(1)

const PlaceholderSidebar = (props: Props) => {
    const {t} = useTranslation()
    const selectedPlaceholder = props.placeholders.find(p => p.id === props.selectedPlaceholder)

    // Solange ein Geometrie-Feld fokussiert ist, zeigt es den rohen Eingabetext statt des
    // formatierten Werts an — sonst überschreibt jedes Neurendern (z. B. nach jedem Tastendruck)
    // das, was der Nutzer gerade tippt. Committet wird erst bei Blur oder Enter.
    const [editingGeometryField, setEditingGeometryField] = useState<GeometryField | null>(null)
    const [geometryFieldBuffer, setGeometryFieldBuffer] = useState('')

    useEffect(() => {
        setEditingGeometryField(null)
    }, [selectedPlaceholder?.id])

    const handleAddPlaceholder = (type: GapDocumentPlaceholderType) => {
        props.onAddPlaceholder(type, props.currentPage)
    }

    const handlePlaceholderPropertyChange = (id: string, updates: Partial<PlaceholderData>) => {
        props.onPlaceholdersChange(
            props.placeholders.map(p => (p.id === id ? {...p, ...updates} : p)),
        )
    }

    const getGeometryFieldProps = (
        field: GeometryField,
    ): Pick<TextFieldProps, 'value' | 'onFocus' | 'onChange' | 'onBlur' | 'onKeyDown'> => {
        if (!selectedPlaceholder) {
            return {value: ''}
        }
        const isEditing = editingGeometryField === field
        return {
            value: isEditing ? geometryFieldBuffer : formatPercent(selectedPlaceholder[field]),
            onFocus: () => {
                setEditingGeometryField(field)
                setGeometryFieldBuffer(formatPercent(selectedPlaceholder[field]))
            },
            onChange: e => setGeometryFieldBuffer(e.target.value),
            onBlur: () => {
                const parsed = parsePercent(geometryFieldBuffer)
                if (parsed !== undefined) {
                    handlePlaceholderPropertyChange(
                        selectedPlaceholder.id,
                        clampRect({...selectedPlaceholder, [field]: parsed}),
                    )
                }
                setEditingGeometryField(null)
            },
            onKeyDown: e => {
                if (e.key === 'Enter') {
                    e.currentTarget.blur()
                }
            },
        }
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
                                type="text"
                                size="small"
                                slotProps={{htmlInput: {inputMode: 'decimal'}}}
                                {...getGeometryFieldProps('relLeft')}
                            />
                            <TextField
                                label={`${t('gap.document.placeholder.positionY')} (%)`}
                                type="text"
                                size="small"
                                slotProps={{htmlInput: {inputMode: 'decimal'}}}
                                {...getGeometryFieldProps('relTop')}
                            />
                        </Stack>

                        <Stack direction="row" spacing={1}>
                            <TextField
                                label={`${t('gap.document.placeholder.width')} (%)`}
                                type="text"
                                size="small"
                                slotProps={{htmlInput: {inputMode: 'decimal'}}}
                                {...getGeometryFieldProps('relWidth')}
                            />
                            <TextField
                                label={`${t('gap.document.placeholder.height')} (%)`}
                                type="text"
                                size="small"
                                slotProps={{htmlInput: {inputMode: 'decimal'}}}
                                {...getGeometryFieldProps('relHeight')}
                            />
                        </Stack>
                    </>
                )}
            </Stack>
        </Paper>
    )
}

export default PlaceholderSidebar
