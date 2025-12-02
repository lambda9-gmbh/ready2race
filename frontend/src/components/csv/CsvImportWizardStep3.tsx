import {useEffect} from 'react'
import {Stack, Typography, Alert, TextField} from '@mui/material'
import {Trans} from 'react-i18next'
import {Info} from '@mui/icons-material'
import {CsvImportWizardConfig, CsvValueMappings} from './types'

type Props = {
    config: CsvImportWizardConfig
    valueMappings: CsvValueMappings
    onMappingsChange: (mappings: CsvValueMappings) => void
}

const CsvImportWizardStep3 = ({config, valueMappings, onMappingsChange}: Props) => {
    // Initialize value mappings with defaults
    useEffect(() => {
        if (config.valueMappings && Object.keys(valueMappings).length === 0) {
            const initialMappings: CsvValueMappings = {}

            config.valueMappings.forEach(mapping => {
                if (mapping.defaultValue) {
                    initialMappings[mapping.key] = mapping.defaultValue
                }
            })

            onMappingsChange(initialMappings)
        }
    }, [config.valueMappings])

    const handleValueChange = (key: string, value: string) => {
        onMappingsChange({
            ...valueMappings,
            [key]: value || undefined,
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

                {config.valueMappings.map(mapping => (
                    <TextField
                        key={mapping.key}
                        label={mapping.label}
                        value={valueMappings[mapping.key] ?? ''}
                        onChange={e => handleValueChange(mapping.key, e.target.value)}
                        required={mapping.required}
                        fullWidth
                    />
                ))}
            </Stack>
        </Stack>
    )
}

export default CsvImportWizardStep3
