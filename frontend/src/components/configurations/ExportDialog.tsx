import { memo } from 'react'
import {
    Box,
    Button,
    DialogActions,
    DialogContent,
    DialogTitle,
    TextField,
    Checkbox,
    FormControlLabel,
    CircularProgress,
    Stepper,
    Step,
    StepLabel,
    Divider,
} from '@mui/material'
import Grid2 from '@mui/material/Grid2'
import BaseDialog from '@components/BaseDialog.tsx'
import { useTranslation } from 'react-i18next'
import { ExportForm, ExportFormCheckType, useExportDependencies, DATA_TYPE_OPTIONS } from './common'
import { WebDAVExportType } from '@api/types.gen.ts'

interface ExportDialogProps {
    open: boolean
    onClose: () => void
    formData: ExportForm
    setFormData: React.Dispatch<React.SetStateAction<ExportForm>>
    eventsData: Array<{id: string; name: string}> | undefined
    webDavExportTypeNames: Map<WebDAVExportType, string>
    onSubmit: () => Promise<void>
    submitting: boolean
    nameError: string
    setNameError: (error: string) => void
    activeStep: number
    setActiveStep: (step: number) => void
}

// Memoized checkbox component for event documents
const EventDocumentCheckbox = memo(({ 
    docExport, 
    onChange, 
    label 
}: { 
    docExport: ExportFormCheckType
    onChange: (checked: boolean) => void
    label: string 
}) => (
    <FormControlLabel
        control={
            <Checkbox
                checked={docExport.checked}
                onChange={e => onChange(e.target.checked)}
            />
        }
        label={label}
    />
))

EventDocumentCheckbox.displayName = 'EventDocumentCheckbox'

const ExportDialog = memo(({
    open,
    onClose,
    formData,
    setFormData,
    eventsData,
    webDavExportTypeNames,
    onSubmit,
    submitting,
    nameError,
    setNameError,
    activeStep,
    setActiveStep,
}: ExportDialogProps) => {
    const { t } = useTranslation()
    const { isRequiredDependency } = useExportDependencies(formData, setFormData, activeStep)
    
    const steps = ['Name', 'Documents', 'Data']

    const handleNext = () => {
        setActiveStep(activeStep + 1)
    }

    const handleBack = () => {
        setActiveStep(activeStep - 1)
    }

    return (
        <BaseDialog open={open} onClose={onClose} maxWidth={'sm'}>
            <DialogTitle>{t('webDAV.export.export')}</DialogTitle>
            <DialogContent>
                <Stepper activeStep={activeStep} sx={{mb: 3}}>
                    {steps.map(label => (
                        <Step key={label}>
                            <StepLabel>{label}</StepLabel>
                        </Step>
                    ))}
                </Stepper>

                {activeStep === 0 && (
                    <TextField
                        fullWidth
                        value={formData.name}
                        onChange={e => {
                            setNameError('')
                            setFormData(prev => ({...prev, name: e.target.value}))
                        }}
                        label={t('webDAV.export.folderName')}
                        required
                        error={!!nameError}
                        helperText={nameError}
                        margin="normal"
                    />
                )}

                {activeStep === 1 && (
                    <Box>
                        {eventsData?.map((event, index) => (
                            <Box key={event.id}>
                                <FormControlLabel
                                    control={
                                        <Checkbox
                                            checked={
                                                formData.events[index]?.docExportChecked ||
                                                false
                                            }
                                            onChange={e => {
                                                const newEvents = [...formData.events]
                                                newEvents[index] = {
                                                    ...newEvents[index],
                                                    docExportChecked: e.target.checked,
                                                }
                                                setFormData(prev => ({
                                                    ...prev,
                                                    events: newEvents,
                                                }))
                                            }}
                                        />
                                    }
                                    label={event.name}
                                />
                                {formData.events[index]?.docExportChecked && (
                                    <Box sx={{ml: 4, mt: 1}}>
                                        {formData.events[index]?.selectedDocExports.map(
                                            (docExport, typeIndex) => (
                                                <EventDocumentCheckbox
                                                    key={docExport.type}
                                                    docExport={docExport}
                                                    onChange={(checked) => {
                                                        setFormData(prev => ({
                                                            ...prev,
                                                            events: prev.events.map((evt, i) => 
                                                                i === index 
                                                                    ? {
                                                                        ...evt,
                                                                        selectedDocExports: evt.selectedDocExports.map((doc, j) =>
                                                                            j === typeIndex
                                                                                ? {...doc, checked}
                                                                                : doc
                                                                        )
                                                                    }
                                                                    : evt
                                                            )
                                                        }))
                                                    }}
                                                    label={
                                                        webDavExportTypeNames.get(
                                                            docExport.type,
                                                        ) ?? '-'
                                                    }
                                                />
                                            ),
                                        )}
                                    </Box>
                                )}
                            </Box>
                        ))}
                    </Box>
                )}

                {activeStep === 2 && (
                    <Box>
                        <Grid2 container spacing={1}>
                            {eventsData?.map((event, index) => (
                                <Grid2 key={event.id} size={{ xs: 12, sm: 6 }}>
                                    <FormControlLabel
                                        control={
                                            <Checkbox
                                                checked={formData.events[index]?.exportData || false}
                                                onChange={e => {
                                                    const newEvents = [...formData.events]
                                                    newEvents[index] = {
                                                        ...newEvents[index],
                                                        exportData: e.target.checked,
                                                    }
                                                    setFormData(prev => ({...prev, events: newEvents}))
                                                }}
                                            />
                                        }
                                        label={event.name}
                                    />
                                </Grid2>
                            ))}
                        </Grid2>
                        <Divider sx={{my: 2}} />
                        <Grid2 container spacing={1}>
                            {DATA_TYPE_OPTIONS.map((exportType, index) => {
                                const isDisabled = isRequiredDependency(exportType)
                                return (
                                    <Grid2 key={exportType} size={{ xs: 12, sm: 6 }}>
                                        <FormControlLabel
                                            control={
                                                <Checkbox
                                                    checked={
                                                        formData.checkedDatabaseExports[index]
                                                            ?.checked || false
                                                    }
                                                    disabled={isDisabled}
                                                    onChange={e => {
                                                        const newDatabaseExports = [
                                                            ...formData.checkedDatabaseExports,
                                                        ]
                                                        newDatabaseExports[index] = {
                                                            ...newDatabaseExports[index],
                                                            checked: e.target.checked,
                                                        }
                                                        setFormData(prev => ({
                                                            ...prev,
                                                            checkedDatabaseExports: newDatabaseExports,
                                                        }))
                                                    }}
                                                />
                                            }
                                            label={webDavExportTypeNames.get(exportType) ?? '-'}
                                        />
                                    </Grid2>
                                )
                            })}
                        </Grid2>
                    </Box>
                )}
            </DialogContent>
            <DialogActions sx={{justifyContent: 'space-between'}}>
                <Box>
                    <Button onClick={handleBack} disabled={submitting || activeStep === 0}>
                        {t('common.back')}
                    </Button>
                    <Button onClick={onClose} disabled={submitting}>
                        {t('common.cancel')}
                    </Button>
                </Box>
                <Box>
                    {activeStep < steps.length - 1 ? (
                        <Button variant="contained" onClick={handleNext}>
                            {t('common.next')}
                        </Button>
                    ) : (
                        <Button
                            variant="contained"
                            onClick={onSubmit}
                            disabled={submitting}
                            startIcon={submitting && <CircularProgress size={20} />}>
                            {t('webDAV.export.confirm')}
                        </Button>
                    )}
                </Box>
            </DialogActions>
        </BaseDialog>
    )
})

ExportDialog.displayName = 'ExportDialog'

export default ExportDialog