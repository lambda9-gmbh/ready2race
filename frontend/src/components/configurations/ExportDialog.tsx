import React, {memo, useCallback} from 'react'
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
import {useTranslation} from 'react-i18next'
import {ExportForm, ExportFormCheckType, useExportDependencies, DATA_TYPE_OPTIONS} from './common'
import {EventForExportDto, WebDAVExportType} from '@api/types.gen.ts'

interface ExportDialogProps {
    open: boolean
    onClose: () => void
    formData: ExportForm
    setFormData: React.Dispatch<React.SetStateAction<ExportForm>>
    eventsData?: EventForExportDto[]
    webDavExportTypeNames: Map<WebDAVExportType, string>
    onSubmit: () => Promise<void>
    submitting: boolean
    nameError: string
    setNameError: (error: string) => void
    activeStep: number
    setActiveStep: (step: number) => void
}

// Memoized checkbox component for event documents
const EventDocumentCheckbox = memo(
    ({
        docExport,
        onChange,
        label,
    }: {
        docExport: ExportFormCheckType
        onChange: (checked: boolean) => void
        label: string
    }) => (
        <FormControlLabel
            control={
                <Checkbox checked={docExport.checked} onChange={e => onChange(e.target.checked)} />
            }
            label={label}
        />
    ),
)

EventDocumentCheckbox.displayName = 'EventDocumentCheckbox'

const ExportDialog = memo(
    ({
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
        const {t} = useTranslation()
        const {isRequiredDependency} = useExportDependencies(formData, setFormData, activeStep)

        const steps = [
            t('webDAV.export.steps.name'),
            t('webDAV.export.steps.documents'),
            t('webDAV.export.steps.data'),
        ]

        const handleNext = useCallback(() => {
            setActiveStep(activeStep + 1)
        }, [activeStep, setActiveStep])

        const handleBack = useCallback(() => {
            setActiveStep(activeStep - 1)
        }, [activeStep, setActiveStep])

        const handleNameChange = useCallback(
            (e: React.ChangeEvent<HTMLInputElement>) => {
                setNameError('')
                setFormData(prev => ({...prev, name: e.target.value}))
            },
            [setNameError, setFormData],
        )

        const handleEventDocExportToggle = useCallback(
            (index: number) => (e: React.ChangeEvent<HTMLInputElement>) => {
                setFormData(prev => ({
                    ...prev,
                    events: prev.events.map((evt, i) =>
                        i === index ? {...evt, docExportChecked: e.target.checked} : evt,
                    ),
                }))
            },
            [setFormData],
        )

        const handleDocExportChange = useCallback(
            (index: number, typeIndex: number) => (checked: boolean) => {
                setFormData(prev => ({
                    ...prev,
                    events: prev.events.map((evt, i) =>
                        i === index
                            ? {
                                  ...evt,
                                  selectedDocExports: evt.selectedDocExports.map((doc, j) =>
                                      j === typeIndex ? {...doc, checked} : doc,
                                  ),
                              }
                            : evt,
                    ),
                }))
            },
            [setFormData],
        )

        const handleEventDataExportToggle = useCallback(
            (index: number) => (e: React.ChangeEvent<HTMLInputElement>) => {
                setFormData(prev => ({
                    ...prev,
                    events: prev.events.map((evt, i) =>
                        i === index ? {...evt, exportData: e.target.checked} : evt,
                    ),
                }))
            },
            [setFormData],
        )

        const handleCompetitionToggle = useCallback(
            (index: number, compIndex: number) => (e: React.ChangeEvent<HTMLInputElement>) => {
                setFormData(prev => ({
                    ...prev,
                    events: prev.events.map((evt, i) =>
                        i === index
                            ? {
                                  ...evt,
                                  selectedCompetitionIds: evt.selectedCompetitionIds.map(
                                      (comp, j) =>
                                          j === compIndex
                                              ? {...comp, checked: e.target.checked}
                                              : comp,
                                  ),
                              }
                            : evt,
                    ),
                }))
            },
            [setFormData],
        )

        const handleDatabaseExportToggle = useCallback(
            (index: number) => (e: React.ChangeEvent<HTMLInputElement>) => {
                setFormData(prev => ({
                    ...prev,
                    checkedDatabaseExports: prev.checkedDatabaseExports.map((item, i) =>
                        i === index ? {...item, checked: e.target.checked} : item,
                    ),
                }))
            },
            [setFormData],
        )

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
                            onChange={handleNameChange}
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
                                                onChange={handleEventDocExportToggle(index)}
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
                                                        onChange={handleDocExportChange(
                                                            index,
                                                            typeIndex,
                                                        )}
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
                                    <Grid2 key={event.id} size={{xs: 12}}>
                                        <Box>
                                            <FormControlLabel
                                                control={
                                                    <Checkbox
                                                        checked={
                                                            formData.events[index]?.exportData ||
                                                            false
                                                        }
                                                        onChange={handleEventDataExportToggle(
                                                            index,
                                                        )}
                                                    />
                                                }
                                                label={event.name}
                                            />
                                            {formData.events[index]?.exportData &&
                                                event.competitions &&
                                                event.competitions.length > 0 && (
                                                    <Box sx={{ml: 4, mt: 1}}>
                                                        <Grid2 container spacing={1}>
                                                            {event.competitions.map(
                                                                (competition, compIndex) => (
                                                                    <Grid2
                                                                        key={competition.id}
                                                                        size={{xs: 12, sm: 6}}>
                                                                        <FormControlLabel
                                                                            control={
                                                                                <Checkbox
                                                                                    checked={
                                                                                        formData
                                                                                            .events[
                                                                                            index
                                                                                        ]
                                                                                            ?.selectedCompetitionIds[
                                                                                            compIndex
                                                                                        ]
                                                                                            ?.checked ||
                                                                                        false
                                                                                    }
                                                                                    onChange={handleCompetitionToggle(
                                                                                        index,
                                                                                        compIndex,
                                                                                    )}
                                                                                />
                                                                            }
                                                                            label={`${competition.identifier} | ${competition.name}`}
                                                                        />
                                                                    </Grid2>
                                                                ),
                                                            )}
                                                        </Grid2>
                                                    </Box>
                                                )}
                                        </Box>
                                    </Grid2>
                                ))}
                            </Grid2>
                            <Divider sx={{my: 2}} />
                            <Grid2 container spacing={1}>
                                {DATA_TYPE_OPTIONS.map((exportType, index) => {
                                    const isDisabled = isRequiredDependency(exportType)
                                    return (
                                        <Grid2 key={exportType} size={{xs: 12, sm: 6}}>
                                            <FormControlLabel
                                                control={
                                                    <Checkbox
                                                        checked={
                                                            formData.checkedDatabaseExports[index]
                                                                ?.checked || false
                                                        }
                                                        disabled={isDisabled}
                                                        onChange={handleDatabaseExportToggle(index)}
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
    },
)

ExportDialog.displayName = 'ExportDialog'

export default ExportDialog
