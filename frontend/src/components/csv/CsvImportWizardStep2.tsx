import {useEffect} from 'react'
import {
    Stack,
    Typography,
    Alert,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Paper,
    FormControl,
    InputLabel,
    Select,
    MenuItem,
} from '@mui/material'
import {Trans, useTranslation} from 'react-i18next'
import {Info} from '@mui/icons-material'
import {CsvImportWizardConfig, ParsedCsvData, CsvColumnMappings} from './types'

type Props = {
    config: CsvImportWizardConfig
    parsedData: ParsedCsvData | null
    columnMappings: CsvColumnMappings
    onMappingsChange: (mappings: CsvColumnMappings) => void
    hasHeader: boolean
}

/**
 * Checks if a header is a numeric index (e.g., "1", "2", "3")
 */
const isNumericIndex = (header: string): boolean => {
    return /^\d+$/.test(header)
}

/**
 * Gets display name for a column header
 * If we're in "no header" mode and it's a numeric index, returns localized "Column X"
 * Otherwise returns the header as-is
 */
const getDisplayName = (
    header: string,
    hasHeader: boolean,
    t: (key: string, params?: any) => string,
): string => {
    // Only show "Column X" if we're in no-header mode AND it's a numeric index
    if (!hasHeader && isNumericIndex(header)) {
        return t('csv.wizard.step2.columnIndex', {index: header})
    }
    return header
}

const CsvImportWizardStep2 = ({
    config,
    parsedData,
    columnMappings,
    onMappingsChange,
    hasHeader,
}: Props) => {
    const {t} = useTranslation()

    // Initialize mappings with defaults
    useEffect(() => {
        if (parsedData && Object.keys(columnMappings).length === 0) {
            const initialMappings: CsvColumnMappings = {}

            // Try to auto-map based on defaultColumnName
            config.fieldMappings.forEach(field => {
                if (field.defaultColumnName) {
                    const matchingHeader = parsedData.headers.find(
                        header => header.toLowerCase() === field.defaultColumnName?.toLowerCase(),
                    )
                    if (matchingHeader) {
                        initialMappings[field.key] = matchingHeader
                    }
                }
            })

            onMappingsChange(initialMappings)
        }
    }, [parsedData, config.fieldMappings])

    const handleMappingChange = (fieldKey: string, value: string) => {
        onMappingsChange({
            ...columnMappings,
            [fieldKey]: value || undefined,
        })
    }

    if (!parsedData) {
        return (
            <Alert severity="warning">
                <Trans i18nKey="csv.wizard.step2.noParsedData" />
            </Alert>
        )
    }

    return (
        <Stack spacing={3}>
            <Alert icon={<Info />} severity="info">
                <Trans i18nKey="csv.wizard.step2.info" values={{totalRows: parsedData.totalRows}} />
            </Alert>

            <Stack spacing={2}>
                <Typography variant="subtitle1">
                    <Trans i18nKey="csv.wizard.step2.columnMapping" />
                </Typography>

                {config.fieldMappings.map(field => (
                    <FormControl key={field.key} fullWidth required={field.required}>
                        <InputLabel id={`mapping-${field.key}-label`}>
                            {field.label}
                            {field.required && ' *'}
                        </InputLabel>
                        <Select
                            labelId={`mapping-${field.key}-label`}
                            value={columnMappings[field.key] ?? ''}
                            onChange={e =>
                                handleMappingChange(field.key, e.target.value.toString())
                            }
                            label={`${field.label}${field.required ? ' *' : ''}`}>
                            <MenuItem value="">
                                <em>
                                    <Trans i18nKey="csv.wizard.step2.notMapped" />
                                </em>
                            </MenuItem>
                            {parsedData.headers.map((header, index) => (
                                <MenuItem key={index} value={header}>
                                    {getDisplayName(header, hasHeader, t)}
                                </MenuItem>
                            ))}
                        </Select>
                    </FormControl>
                ))}
            </Stack>

            <Stack spacing={2}>
                <Typography variant="subtitle1">
                    <Trans i18nKey="csv.wizard.step2.dataPreview" />
                </Typography>

                <TableContainer component={Paper} sx={{maxHeight: 400}}>
                    <Table stickyHeader size="small">
                        <TableHead>
                            <TableRow>
                                {parsedData.headers.map((header, index) => (
                                    <TableCell key={index}>
                                        <Typography variant="subtitle2" fontWeight="bold">
                                            {getDisplayName(header, hasHeader, t)}
                                        </Typography>
                                    </TableCell>
                                ))}
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {parsedData.previewRows.length === 0 ? (
                                <TableRow>
                                    <TableCell colSpan={parsedData.headers.length} align="center">
                                        <Typography color="text.secondary">
                                            <Trans i18nKey="csv.wizard.step2.noData" />
                                        </Typography>
                                    </TableCell>
                                </TableRow>
                            ) : (
                                parsedData.previewRows.map((row, rowIndex) => (
                                    <TableRow key={rowIndex}>
                                        {row.map((cell, cellIndex) => (
                                            <TableCell key={cellIndex}>
                                                <Typography
                                                    variant="body2"
                                                    sx={{
                                                        maxWidth: 200,
                                                        overflow: 'hidden',
                                                        textOverflow: 'ellipsis',
                                                        whiteSpace: 'nowrap',
                                                    }}>
                                                    {cell}
                                                </Typography>
                                            </TableCell>
                                        ))}
                                    </TableRow>
                                ))
                            )}
                        </TableBody>
                    </Table>
                </TableContainer>

                {parsedData.totalRows > parsedData.previewRows.length && (
                    <Typography variant="caption" color="text.secondary" align="center">
                        <Trans
                            i18nKey="csv.wizard.step2.moreRows"
                            values={{
                                shown: parsedData.previewRows.length,
                                total: parsedData.totalRows,
                            }}
                        />
                    </Typography>
                )}
            </Stack>
        </Stack>
    )
}

export default CsvImportWizardStep2
