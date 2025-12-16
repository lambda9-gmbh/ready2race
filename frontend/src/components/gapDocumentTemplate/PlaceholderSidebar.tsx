import {
    Box,
    Button,
    Divider,
    MenuItem,
    Paper,
    Select,
    Stack,
    TextField,
    Typography,
} from '@mui/material'
import {GapDocumentPlaceholderType, TextAlign} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {Add} from '@mui/icons-material'

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
}

type Props = {
    selectedPlaceholder: string | null
    placeholders: PlaceholderData[]
    onPlaceholdersChange: (placeholders: PlaceholderData[]) => void
    onAddPlaceholder: (type: GapDocumentPlaceholderType, page: number) => void
    currentPage: number
}

const PLACEHOLDER_TYPES: GapDocumentPlaceholderType[] = [
    'FIRST_NAME',
    'LAST_NAME',
    'FULL_NAME',
    'RESULT',
    'EVENT_NAME',
]

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
                    {PLACEHOLDER_TYPES.map(type => (
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

                        <Box>
                            <Typography variant="caption" color="text.secondary">
                                {t('gap.document.placeholder.page')}: {selectedPlaceholder.page}
                            </Typography>
                        </Box>

                        <Box>
                            <Typography
                                variant="caption"
                                color="text.secondary"
                                sx={{display: 'block'}}>
                                {t('gap.document.placeholder.position')}
                            </Typography>
                            <Typography variant="caption">
                                X: {(selectedPlaceholder.relLeft * 100).toFixed(1)}%, Y:{' '}
                                {(selectedPlaceholder.relTop * 100).toFixed(1)}%
                            </Typography>
                        </Box>

                        <Box>
                            <Typography
                                variant="caption"
                                color="text.secondary"
                                sx={{display: 'block'}}>
                                {t('gap.document.placeholder.size')}
                            </Typography>
                            <Typography variant="caption">
                                W: {(selectedPlaceholder.relWidth * 100).toFixed(1)}%, H:{' '}
                                {(selectedPlaceholder.relHeight * 100).toFixed(1)}%
                            </Typography>
                        </Box>
                    </>
                )}
            </Stack>
        </Paper>
    )
}

export default PlaceholderSidebar
