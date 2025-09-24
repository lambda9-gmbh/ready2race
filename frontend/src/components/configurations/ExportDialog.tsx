import React, {useCallback, useMemo} from 'react'
import {
    Box,
    Button,
    DialogActions,
    DialogContent,
    DialogTitle,
    TextField,
    Checkbox,
    CircularProgress,
    Stepper,
    Step,
    StepLabel,
    Divider,
} from '@mui/material'
import Grid2 from '@mui/material/Grid2'
import BaseDialog from '@components/BaseDialog.tsx'
import {useTranslation} from 'react-i18next'
import {ExportForm, useExportDependencies, DATA_TYPE_OPTIONS} from './common'
import {EventForExportDto, WebDAVExportType} from '@api/types.gen.ts'
import SelectAllCheckbox from './SelectAllCheckbox'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'

type ExportDialogProps = {
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

const ExportDialog = ({
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
                              selectedCompetitionIds: evt.selectedCompetitionIds.map((comp, j) =>
                                  j === compIndex ? {...comp, checked: e.target.checked} : comp,
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

    // Select all/deselect all for document exports (step 1)
    const handleSelectAllDocuments = useCallback(() => {
        const hasAnySelected = formData.events.some(event => event.docExportChecked)
        setFormData(prev => ({
            ...prev,
            events: prev.events.map(evt => ({
                ...evt,
                docExportChecked: !hasAnySelected,
                selectedDocExports: evt.selectedDocExports.map(doc => ({
                    ...doc,
                    checked: !hasAnySelected,
                })),
            })),
        }))
    }, [formData.events, setFormData])

    // Combined select all/deselect all for step 2 (both event data and database exports)
    const handleSelectAllDataExport = useCallback(() => {
        const hasAnyEventSelected = formData.events.some(event => event.exportData)
        const availableExports = formData.checkedDatabaseExports.filter(
            (_, index) => !isRequiredDependency(DATA_TYPE_OPTIONS[index]),
        )
        const hasAnyDatabaseSelected = availableExports.some(item => item.checked)
        const shouldSelectAll = !(hasAnyEventSelected || hasAnyDatabaseSelected)

        setFormData(prev => ({
            ...prev,
            events: prev.events.map(evt => ({
                ...evt,
                exportData: shouldSelectAll,
                selectedCompetitionIds: evt.selectedCompetitionIds.map(comp => ({
                    ...comp,
                    checked: shouldSelectAll,
                })),
            })),
            checkedDatabaseExports: prev.checkedDatabaseExports.map((item, index) => {
                if (shouldSelectAll && isRequiredDependency(DATA_TYPE_OPTIONS[index])) {
                    return item // When selecting all, keep dependencies as they are
                }
                return {...item, checked: shouldSelectAll}
            }),
        }))
    }, [formData.events, formData.checkedDatabaseExports, isRequiredDependency, setFormData])

    // Helper to compute checkbox state
    const getCheckboxState = (selectedCount: number, totalCount: number) => {
        if (selectedCount === 0) return {checked: false, indeterminate: false}
        if (selectedCount === totalCount) return {checked: true, indeterminate: false}
        return {checked: false, indeterminate: true}
    }

    const documentsSelectState = useMemo(() => {
        const totalItems = formData.events.reduce(
            (acc, event) => acc + 1 + event.selectedDocExports.length,
            0,
        )
        const selectedItems = formData.events.reduce(
            (acc, event) =>
                acc +
                (event.docExportChecked ? 1 : 0) +
                (event.docExportChecked
                    ? event.selectedDocExports.filter(doc => doc.checked).length
                    : 0),
            0,
        )
        return getCheckboxState(selectedItems, totalItems)
    }, [formData.events])

    const dataExportSelectState = useMemo(() => {
        const availableExportTypes = formData.checkedDatabaseExports.filter(
            (_, i) => !isRequiredDependency(DATA_TYPE_OPTIONS[i]),
        )
        const totalSelected =
            formData.events.filter(e => e.exportData).length +
            availableExportTypes.filter(item => item.checked).length
        const totalItems = formData.events.length + availableExportTypes.length

        return getCheckboxState(totalSelected, totalItems)
    }, [formData.events, formData.checkedDatabaseExports, isRequiredDependency])

    return (
        <BaseDialog open={open} onClose={onClose} maxWidth={'md'}>
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
                        <SelectAllCheckbox
                            {...documentsSelectState}
                            onChange={handleSelectAllDocuments}
                        />
                        <Divider sx={{my: 1}} />
                        {eventsData?.map((event, index) => (
                            <Box key={event.id}>
                                <FormInputLabel label={event.name} horizontal reverse required>
                                    <Checkbox
                                        checked={formData.events[index]?.docExportChecked || false}
                                        onChange={handleEventDocExportToggle(index)}
                                    />
                                </FormInputLabel>
                                {formData.events[index]?.docExportChecked && (
                                    <Grid2 container sx={{px: 4, pt: 1, width: 1}} spacing={1}>
                                        {formData.events[index]?.selectedDocExports.map(
                                            (docExport, typeIndex) => (
                                                <Grid2 size={{xs: 12, sm: 6}} key={docExport.type}>
                                                    <FormInputLabel
                                                        label={
                                                            webDavExportTypeNames.get(
                                                                docExport.type,
                                                            ) ?? '-'
                                                        }
                                                        horizontal
                                                        reverse
                                                        required>
                                                        <Checkbox
                                                            checked={docExport.checked}
                                                            onChange={e =>
                                                                handleDocExportChange(
                                                                    index,
                                                                    typeIndex,
                                                                )(e.target.checked)
                                                            }
                                                        />
                                                    </FormInputLabel>
                                                </Grid2>
                                            ),
                                        )}
                                    </Grid2>
                                )}
                            </Box>
                        ))}
                    </Box>
                )}

                {activeStep === 2 && (
                    <Box>
                        <SelectAllCheckbox
                            {...dataExportSelectState}
                            onChange={handleSelectAllDataExport}
                        />
                        <Divider sx={{my: 1}} />
                        {eventsData?.map((event, index) => (
                            <Box>
                                <FormInputLabel label={event.name} horizontal reverse required>
                                    <Checkbox
                                        checked={formData.events[index]?.exportData || false}
                                        onChange={handleEventDataExportToggle(index)}
                                    />
                                </FormInputLabel>
                                {formData.events[index]?.exportData &&
                                    event.competitions &&
                                    event.competitions.length > 0 && (
                                        <Grid2 container spacing={1} sx={{px: 4, pt: 1, width: 1}}>
                                            {event.competitions.map((competition, compIndex) => (
                                                <Grid2 key={competition.id} size={{xs: 12, sm: 6}}>
                                                    <FormInputLabel
                                                        label={`${competition.identifier} | ${competition.name}`}
                                                        horizontal
                                                        reverse
                                                        required>
                                                        <Checkbox
                                                            checked={
                                                                formData.events[index]
                                                                    ?.selectedCompetitionIds[
                                                                    compIndex
                                                                ]?.checked || false
                                                            }
                                                            onChange={handleCompetitionToggle(
                                                                index,
                                                                compIndex,
                                                            )}
                                                        />
                                                    </FormInputLabel>
                                                </Grid2>
                                            ))}
                                        </Grid2>
                                    )}
                            </Box>
                        ))}
                        <Divider sx={{my: 2}} />
                        <Grid2 container spacing={1}>
                            {DATA_TYPE_OPTIONS.map((exportType, index) => {
                                const isDisabled = isRequiredDependency(exportType)
                                return (
                                    <Grid2 key={exportType} size={{xs: 12, sm: 6}}>
                                        <FormInputLabel
                                            label={webDavExportTypeNames.get(exportType) ?? '-'}
                                            horizontal
                                            reverse
                                            required>
                                            <Checkbox
                                                checked={
                                                    formData.checkedDatabaseExports[index]
                                                        ?.checked || false
                                                }
                                                disabled={isDisabled}
                                                onChange={handleDatabaseExportToggle(index)}
                                            />
                                        </FormInputLabel>
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
}

export default ExportDialog
