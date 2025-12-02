import {useEffect, useState} from 'react'
import {
    Button,
    DialogActions,
    DialogContent,
    DialogTitle,
    Step,
    StepLabel,
    Stepper,
    Stack,
    Box,
    Alert,
} from '@mui/material'
import {Trans, useTranslation} from 'react-i18next'
import BaseDialog from '@components/BaseDialog.tsx'
import {
    CsvImportWizardConfig,
    CsvImportWizardResult,
    CsvImportConfig,
    ParsedCsvData,
    CsvColumnMappings,
    CsvValueMappings,
} from './types'
import {parseCsvFile} from './utils'
import CsvImportWizardStep1 from './CsvImportWizardStep1'
import CsvImportWizardStep2 from './CsvImportWizardStep2'
import CsvImportWizardStep3 from './CsvImportWizardStep3'

type Props = {
    open: boolean
    onClose: () => void
    config: CsvImportWizardConfig
    onComplete: (result: CsvImportWizardResult) => Promise<void>
}

const CsvImportWizard = ({open, onClose, config, onComplete}: Props) => {
    const {t} = useTranslation()

    // Wizard state
    const [activeStep, setActiveStep] = useState(0)
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState<string | null>(null)

    // Step 1 data
    const [importConfig, setImportConfig] = useState<CsvImportConfig | null>(null)

    // Step 2 data
    const [parsedData, setParsedData] = useState<ParsedCsvData | null>(null)
    const [columnMappings, setColumnMappings] = useState<CsvColumnMappings>({})

    // Step 3 data
    const [valueMappings, setValueMappings] = useState<CsvValueMappings>({})

    // Calculate total steps
    const hasValueMappings = config.valueMappings && config.valueMappings.length > 0
    const totalSteps = hasValueMappings ? 3 : 2

    // Reset state when dialog opens
    useEffect(() => {
        if (open) {
            setActiveStep(0)
            setSubmitting(false)
            setError(null)
            setImportConfig(null)
            setParsedData(null)
            setColumnMappings({})
            setValueMappings({})
        }
    }, [open])

    // Step labels
    const getStepLabel = (step: number): string => {
        switch (step) {
            case 0:
                return t('csv.wizard.step1.title')
            case 1:
                return t('csv.wizard.step2.title')
            case 2:
                return t('csv.wizard.step3.title')
            default:
                return ''
        }
    }

    // Handle next button
    const handleNext = async () => {
        setError(null)

        try {
            if (activeStep === 0) {
                // Moving from step 1 to step 2
                // Parse the file with the configured settings
                if (!importConfig) {
                    setError(t('csv.wizard.error.noConfig'))
                    return
                }

                const parsed = await parseCsvFile(importConfig.file, {
                    separator: importConfig.separator,
                    charset: importConfig.charset,
                    hasHeader: importConfig.hasHeader,
                    previewRowCount: config.previewRowCount ?? 5,
                })

                setParsedData(parsed)
                setActiveStep(1)
            } else if (activeStep === 1) {
                // Moving from step 2 to step 3 (or submitting if no step 3)
                // Validate column mappings
                const requiredFields = config.fieldMappings.filter(f => f.required).map(f => f.key)

                const missingFields = requiredFields.filter(
                    key => !columnMappings[key] || columnMappings[key] === '',
                )

                if (missingFields.length > 0) {
                    setError(
                        t('csv.wizard.error.missingMappings', {
                            fields: missingFields
                                .map(key => config.fieldMappings.find(f => f.key === key)?.label)
                                .join(', '),
                        }),
                    )
                    return
                }

                if (hasValueMappings) {
                    setActiveStep(2)
                } else {
                    // No step 3, submit directly
                    await handleSubmit()
                }
            } else if (activeStep === 2) {
                // Submitting from step 3
                await handleSubmit()
            }
        } catch (err) {
            setError(err instanceof Error ? err.message : String(err))
        }
    }

    // Handle back button
    const handleBack = () => {
        setError(null)
        setActiveStep(prev => prev - 1)
    }

    // Handle final submit
    const handleSubmit = async () => {
        if (!importConfig) {
            setError(t('csv.wizard.error.noConfig'))
            return
        }

        // Validate value mappings if they exist
        if (hasValueMappings && config.valueMappings) {
            const requiredValueFields = config.valueMappings.filter(v => v.required).map(v => v.key)

            const missingValueFields = requiredValueFields.filter(
                key => !valueMappings[key] || valueMappings[key] === '',
            )

            if (missingValueFields.length > 0) {
                setError(
                    t('csv.wizard.error.missingValueMappings', {
                        fields: missingValueFields
                            .map(key => config.valueMappings?.find(v => v.key === key)?.label)
                            .join(', '),
                    }),
                )
                return
            }
        }

        setSubmitting(true)

        try {
            await onComplete({
                config: importConfig,
                columnMappings,
                valueMappings,
            })

            // Success - dialog will be closed by parent
        } catch (err) {
            setError(err instanceof Error ? err.message : String(err))
        } finally {
            setSubmitting(false)
        }
    }

    // Render current step content
    const renderStepContent = () => {
        switch (activeStep) {
            case 0:
                return (
                    <CsvImportWizardStep1
                        config={config}
                        importConfig={importConfig}
                        onConfigChange={setImportConfig}
                    />
                )
            case 1:
                return (
                    <CsvImportWizardStep2
                        config={config}
                        parsedData={parsedData}
                        columnMappings={columnMappings}
                        onMappingsChange={setColumnMappings}
                        hasHeader={importConfig?.hasHeader ?? true}
                    />
                )
            case 2:
                return (
                    <CsvImportWizardStep3
                        config={config}
                        valueMappings={valueMappings}
                        onMappingsChange={setValueMappings}
                    />
                )
            default:
                return null
        }
    }

    // Check if Next button should be enabled
    const isNextDisabled = (): boolean => {
        if (submitting) return true

        if (activeStep === 0) {
            return !importConfig?.file
        }

        return false
    }

    return (
        <BaseDialog open={open} onClose={onClose} maxWidth="lg" fullWidth>
            <DialogTitle>{config.title}</DialogTitle>
            <DialogContent>
                <Stack spacing={4}>
                    <Stepper activeStep={activeStep}>
                        {Array.from({length: totalSteps}, (_, i) => (
                            <Step key={i}>
                                <StepLabel>{getStepLabel(i)}</StepLabel>
                            </Step>
                        ))}
                    </Stepper>

                    {error && <Alert severity="error">{error}</Alert>}

                    <Box sx={{minHeight: 400}}>{renderStepContent()}</Box>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose} disabled={submitting}>
                    <Trans i18nKey="common.cancel" />
                </Button>
                {activeStep > 0 && (
                    <Button onClick={handleBack} disabled={submitting}>
                        <Trans i18nKey="common.back" />
                    </Button>
                )}
                <Button
                    onClick={handleNext}
                    disabled={isNextDisabled()}
                    variant="contained"
                    color="primary">
                    {activeStep === totalSteps - 1 ? (
                        <Trans i18nKey="common.submit" />
                    ) : (
                        <Trans i18nKey="common.next" />
                    )}
                </Button>
            </DialogActions>
        </BaseDialog>
    )
}

export default CsvImportWizard
