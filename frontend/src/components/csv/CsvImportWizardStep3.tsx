import {useEffect} from 'react'
import {
    Stack,
    Typography,
    Alert,
    TextField,
    FormControlLabel,
    Checkbox,
    FormGroup,
    FormLabel,
    Box,
} from '@mui/material'
import {Trans} from 'react-i18next'
import {Info} from '@mui/icons-material'
import {
    CsvColumnValue,
    CsvColumnMappings,
    CsvImportWizardConfig,
    CsvValueMapping,
    CsvValueMappings,
    ParsedCsvData,
} from './types'

type Props = {
    config: CsvImportWizardConfig
    valueMappings: CsvValueMappings
    parsedData: ParsedCsvData | null
    columnMappings: CsvColumnMappings
    onMappingsChange: (mappings: CsvValueMappings) => void
}

/**
 * Which values can be offered for this mapping? Empty when the mapping is a plain text field,
 * when its column is not mapped, or when that column holds free text (see collectColumnValues).
 */
const selectableValues = (
    mapping: CsvValueMapping,
    parsedData: ParsedCsvData | null,
    columnMappings: CsvColumnMappings,
): CsvColumnValue[] => {
    if (!mapping.valuesFromColumn || !parsedData) return []
    const column = columnMappings[mapping.valuesFromColumn]
    if (typeof column !== 'string' || column === '') return []
    return parsedData.columnValues[column] ?? []
}

const asArray = (value: string | string[] | undefined): string[] =>
    Array.isArray(value) ? value : value ? [value] : []

const CsvImportWizardStep3 = ({
    config,
    valueMappings,
    parsedData,
    columnMappings,
    onMappingsChange,
}: Props) => {
    // Initialize value mappings with defaults. Selectable values start out fully checked:
    // a requirement column usually lists only variants that all count as fulfilled (the DRV
    // list carries "ja" and "erweitert"), so the common case needs no clicking at all.
    useEffect(() => {
        if (config.valueMappings && Object.keys(valueMappings).length === 0) {
            const initialMappings: CsvValueMappings = {}

            config.valueMappings.forEach(mapping => {
                const values = selectableValues(mapping, parsedData, columnMappings)
                if (values.length > 0) {
                    initialMappings[mapping.key] = values.map(v => v.value)
                } else if (mapping.defaultValue) {
                    initialMappings[mapping.key] = mapping.defaultValue
                }
            })

            onMappingsChange(initialMappings)
        }
    }, [config.valueMappings, parsedData, columnMappings])

    const handleValueChange = (key: string, value: string) => {
        onMappingsChange({
            ...valueMappings,
            [key]: value || undefined,
        })
    }

    const handleToggle = (key: string, value: string, checked: boolean) => {
        const current = asArray(valueMappings[key])
        onMappingsChange({
            ...valueMappings,
            [key]: checked ? [...current, value] : current.filter(v => v !== value),
        })
    }

    if (!config.valueMappings || config.valueMappings.length === 0) {
        return null
    }

    return (
        <Stack spacing={3}>
            <Alert icon={<Info />} severity="info">
                <Trans i18nKey="csv.wizard.step3.info" />
            </Alert>

            <Stack spacing={2}>
                <Typography variant="subtitle1">
                    <Trans i18nKey="csv.wizard.step3.valueMappings" />
                </Typography>

                {config.valueMappings.map(mapping => {
                    const values = selectableValues(mapping, parsedData, columnMappings)

                    if (values.length === 0) {
                        return (
                            <TextField
                                key={mapping.key}
                                label={mapping.label}
                                value={
                                    typeof valueMappings[mapping.key] === 'string'
                                        ? (valueMappings[mapping.key] as string)
                                        : ''
                                }
                                onChange={e => handleValueChange(mapping.key, e.target.value)}
                                required={mapping.required}
                                fullWidth
                            />
                        )
                    }

                    const selected = asArray(valueMappings[mapping.key])

                    return (
                        <Box key={mapping.key}>
                            <FormLabel>{mapping.label}</FormLabel>
                            <FormGroup>
                                {values.map(({value, count}) => (
                                    <FormControlLabel
                                        key={value}
                                        control={
                                            <Checkbox
                                                checked={selected.includes(value)}
                                                onChange={e =>
                                                    handleToggle(
                                                        mapping.key,
                                                        value,
                                                        e.target.checked,
                                                    )
                                                }
                                            />
                                        }
                                        label={
                                            <Trans
                                                i18nKey="csv.wizard.step3.valueWithCount"
                                                values={{value, count}}
                                            />
                                        }
                                    />
                                ))}
                            </FormGroup>
                            <Typography variant="caption" color="text.secondary">
                                {selected.length === 0 ? (
                                    <Trans i18nKey="csv.wizard.step3.nothingSelected" />
                                ) : (
                                    <Trans
                                        i18nKey="csv.wizard.step3.selectedCount"
                                        values={{
                                            selected: selected.length,
                                            total: values.length,
                                        }}
                                    />
                                )}
                            </Typography>
                        </Box>
                    )
                })}
            </Stack>
        </Stack>
    )
}

export default CsvImportWizardStep3
