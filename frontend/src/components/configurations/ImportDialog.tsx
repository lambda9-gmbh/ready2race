import React, {memo, useState, useCallback, useMemo} from 'react'
import {
    Button,
    DialogActions,
    DialogContent,
    DialogTitle,
    Checkbox,
    Divider,
    TextField,
    MenuItem,
    CircularProgress,
    Stack,
    Typography,
    Card,
} from '@mui/material'
import Grid2 from '@mui/material/Grid2'
import BaseDialog from '@components/BaseDialog.tsx'
import {useTranslation} from 'react-i18next'
import {WebDAVExportType} from '@api/types.gen.ts'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {
    getWebDavImportOptionFolders,
    getWebDavImportOptionTypes,
    importDataFromWebDav,
} from '@api/sdk.gen.ts'
import Throbber from '@components/Throbber.tsx'
import {ImportForm, useImportDependencies} from '@components/configurations/common.ts'
import SelectAllCheckbox from './SelectAllCheckbox'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'

type ImportDialogProps = {
    dialogOpen: boolean
    onClose: () => void
    formData: ImportForm
    setFormData: React.Dispatch<React.SetStateAction<ImportForm>>
    webDavExportTypeNames: Map<WebDAVExportType, string>
    onSucess: () => void
}

const ImportDialog = memo(
    ({
        dialogOpen,
        webDavExportTypeNames,
        formData,
        setFormData,
        onClose,
        ...props
    }: ImportDialogProps) => {
        const {t} = useTranslation()
        const feedback = useFeedback()
        const {isRequiredDependency} = useImportDependencies(formData, setFormData)

        const [submitting, setSubmitting] = useState<boolean>(false)

        const {data: foldersData, pending: foldersPending} = useFetch(
            signal => getWebDavImportOptionFolders({signal}),
            {
                onResponse: ({error}) => {
                    if (error) {
                        feedback.error(t('webDAV.import.error.loadFolders'))
                    }
                },
                deps: [dialogOpen],
                preCondition: () => dialogOpen,
            },
        )

        const {data: optionsData, pending: optionsPending} = useFetch(
            signal =>
                getWebDavImportOptionTypes({
                    signal,
                    path: {folderName: formData.selectedFolder},
                }),
            {
                onResponse: ({data, error}) => {
                    if (error) {
                        feedback.error(t('webDAV.import.error.loadOptions'))
                        setFormData({
                            selectedFolder: '',
                            checkedResources: [],
                            availableEvents: [],
                        })
                    } else {
                        setFormData({
                            selectedFolder: formData.selectedFolder,
                            checkedResources: data.data.map(type => ({
                                type: type,
                                checked: false,
                            })),
                            availableEvents: data.events.map(event => ({
                                eventFolderName: event.eventFolderName,
                                checked: false,
                                availableCompetitions: event.competitions.map(competition => ({
                                    competitionFolderName: competition.competitionFolderName,
                                    checked: false,
                                })),
                            })),
                        })
                    }
                },
                deps: [formData.selectedFolder],
                preCondition: () =>
                    formData.selectedFolder !== '' && formData.selectedFolder !== undefined,
            },
        )

        const handleFolderChange = useCallback(
            (e: React.ChangeEvent<HTMLInputElement>) => {
                setFormData(prev => ({...prev, selectedFolder: e.target.value}))
            },
            [setFormData],
        )

        const handleResourceToggle = useCallback(
            (index: number) => (e: React.ChangeEvent<HTMLInputElement>) => {
                setFormData(prev => ({
                    ...prev,
                    checkedResources: prev.checkedResources.map((item, i) =>
                        i === index ? {...item, checked: e.target.checked} : item,
                    ),
                }))
            },
            [setFormData],
        )

        const handleEventToggle = useCallback(
            (index: number) => (e: React.ChangeEvent<HTMLInputElement>) => {
                setFormData(prev => ({
                    ...prev,
                    availableEvents: prev.availableEvents.map((evt, i) =>
                        i === index ? {...evt, checked: e.target.checked} : evt,
                    ),
                }))
            },
            [setFormData],
        )

        const handleCompetitionToggle = useCallback(
            (eventIndex: number, compIndex: number) => (e: React.ChangeEvent<HTMLInputElement>) => {
                setFormData(prev => ({
                    ...prev,
                    availableEvents: prev.availableEvents.map((evt, i) =>
                        i === eventIndex
                            ? {
                                  ...evt,
                                  availableCompetitions: evt.availableCompetitions.map((comp, j) =>
                                      j === compIndex ? {...comp, checked: e.target.checked} : comp,
                                  ),
                              }
                            : evt,
                    ),
                }))
            },
            [setFormData],
        )

        const handleClose = () => {
            setFormData({
                selectedFolder: '',
                checkedResources: [],
                availableEvents: [],
            })
            onClose()
        }

        // Select all/deselect all handler
        const handleSelectAll = useCallback(() => {
            const hasAnySelection =
                formData.checkedResources.some(r => r.checked && !isRequiredDependency(r.type)) ||
                formData.availableEvents.some(e => e.checked)

            setFormData(prev => ({
                ...prev,
                checkedResources: prev.checkedResources.map(item => ({
                    ...item,
                    checked: !hasAnySelection,
                })),
                availableEvents: prev.availableEvents.map(evt => ({
                    ...evt,
                    checked: !hasAnySelection,
                    availableCompetitions: evt.availableCompetitions.map(comp => ({
                        ...comp,
                        checked: !hasAnySelection,
                    })),
                })),
            }))
        }, [formData, isRequiredDependency, setFormData])

        // Calculate select all checkbox state
        const selectAllState = useMemo(() => {
            const selectableResources = formData.checkedResources.filter(
                r => !isRequiredDependency(r.type),
            )
            const totalSelectable =
                selectableResources.length +
                formData.availableEvents.reduce(
                    (acc, e) => acc + 1 + e.availableCompetitions.length,
                    0,
                )

            if (totalSelectable === 0) return {checked: false, indeterminate: false}

            const selectedCount =
                selectableResources.filter(r => r.checked).length +
                formData.availableEvents.reduce(
                    (acc, e) =>
                        acc +
                        (e.checked ? 1 : 0) +
                        e.availableCompetitions.filter(c => c.checked).length,
                    0,
                )

            if (selectedCount === 0) return {checked: false, indeterminate: false}
            if (selectedCount === totalSelectable) return {checked: true, indeterminate: false}
            return {checked: false, indeterminate: true}
        }, [formData, isRequiredDependency])

        const handleSubmit = async () => {
            if (!optionsData) {
                feedback.error(t('common.error.unexpected'))
                return
            }

            const selectedTypes = formData.checkedResources
                .filter(resource => resource.checked)
                .map(resource => resource.type)

            const hasEventSelections = formData.availableEvents.some(event => event.checked)

            if (selectedTypes.length === 0 && !hasEventSelections) {
                feedback.error(t('webDAV.import.error.noSelection'))
                return
            }

            setSubmitting(true)
            const {error} = await importDataFromWebDav({
                body: {
                    folderName: formData.selectedFolder,
                    selectedData: selectedTypes,
                    selectedEvents: formData.availableEvents
                        .filter(event => event.checked)
                        .map(event => ({
                            eventFolderName: event.eventFolderName,
                            competitionFolderNames: event.availableCompetitions
                                .filter(comp => comp.checked)
                                .map(comp => comp.competitionFolderName),
                        })),
                },
            })
            setSubmitting(false)

            if (error) {
                feedback.error(t('webDAV.import.error.failed'))
            } else {
                feedback.success(t('webDAV.import.success'))
                props.onSucess()
                handleClose()
            }
        }

        return (
            <BaseDialog open={dialogOpen} onClose={handleClose} maxWidth={'md'}>
                <DialogTitle>{t('webDAV.import.import')}</DialogTitle>
                <DialogContent sx={{display: 'flex', flexDirection: 'column'}}>
                    {foldersPending ? (
                        <Throbber />
                    ) : (
                        <>
                            <TextField
                                select
                                fullWidth
                                value={formData.selectedFolder}
                                onChange={handleFolderChange}
                                label={t('webDAV.import.folderName')}
                                required
                                margin="normal">
                                {foldersData?.map(folder => (
                                    <MenuItem key={folder} value={folder}>
                                        {folder}
                                    </MenuItem>
                                ))}
                            </TextField>
                            {formData.selectedFolder && (
                                <Stack spacing={2}>
                                    {optionsPending ? (
                                        <Throbber />
                                    ) : (
                                        <>
                                            <SelectAllCheckbox
                                                {...selectAllState}
                                                onChange={handleSelectAll}
                                            />

                                            {formData.availableEvents.length > 0 && (
                                                <>
                                                    <Divider sx={{my: 1}} />
                                                    <Typography variant={'subtitle1'} gutterBottom>
                                                        {t('event.events')}
                                                    </Typography>
                                                    <Stack spacing={2}>
                                                        {formData.availableEvents.map(
                                                            (event, eventIndex) => (
                                                                <Card
                                                                    key={event.eventFolderName}
                                                                    sx={{py: 1}}>
                                                                    <FormInputLabel
                                                                        label={
                                                                            event.eventFolderName
                                                                        }
                                                                        required
                                                                        horizontal
                                                                        reverse>
                                                                        <Checkbox
                                                                            checked={
                                                                                event.checked ||
                                                                                false
                                                                            }
                                                                            onChange={handleEventToggle(
                                                                                eventIndex,
                                                                            )}
                                                                        />
                                                                    </FormInputLabel>
                                                                    {event.checked &&
                                                                        event.availableCompetitions
                                                                            .length > 0 && (
                                                                            <Grid2
                                                                                container
                                                                                spacing={1}
                                                                                sx={{px: 4, pt: 1}}>
                                                                                {event.availableCompetitions.map(
                                                                                    (
                                                                                        competition,
                                                                                        compIndex,
                                                                                    ) => (
                                                                                        <Grid2
                                                                                            key={
                                                                                                competition.competitionFolderName
                                                                                            }
                                                                                            size={{
                                                                                                xs: 12,
                                                                                                sm: 6,
                                                                                            }}>
                                                                                            <FormInputLabel
                                                                                                label={
                                                                                                    competition.competitionFolderName
                                                                                                }
                                                                                                horizontal
                                                                                                reverse
                                                                                                required>
                                                                                                <Checkbox
                                                                                                    checked={
                                                                                                        competition.checked ||
                                                                                                        false
                                                                                                    }
                                                                                                    onChange={handleCompetitionToggle(
                                                                                                        eventIndex,
                                                                                                        compIndex,
                                                                                                    )}
                                                                                                />
                                                                                            </FormInputLabel>
                                                                                        </Grid2>
                                                                                    ),
                                                                                )}
                                                                            </Grid2>
                                                                        )}
                                                                </Card>
                                                            ),
                                                        )}
                                                    </Stack>
                                                </>
                                            )}
                                            <Divider />
                                            <Typography variant={'subtitle1'} gutterBottom>
                                                {t('webDAV.export.applicationData')}
                                            </Typography>
                                            {formData.checkedResources.length > 0 && (
                                                <Grid2 container spacing={1}>
                                                    {formData.checkedResources.map(
                                                        (resource, index) => (
                                                            <Grid2
                                                                key={resource.type}
                                                                size={{xs: 12, sm: 6}}>
                                                                <FormInputLabel
                                                                    label={
                                                                        webDavExportTypeNames.get(
                                                                            resource.type,
                                                                        ) ?? '-'
                                                                    }
                                                                    horizontal
                                                                    reverse
                                                                    required>
                                                                    <Checkbox
                                                                        checked={
                                                                            resource.checked ||
                                                                            false
                                                                        }
                                                                        disabled={isRequiredDependency(
                                                                            resource.type,
                                                                        )}
                                                                        onChange={handleResourceToggle(
                                                                            index,
                                                                        )}
                                                                    />
                                                                </FormInputLabel>
                                                            </Grid2>
                                                        ),
                                                    )}
                                                </Grid2>
                                            )}
                                        </>
                                    )}
                                </Stack>
                            )}
                        </>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={handleClose}>{t('common.cancel')}</Button>
                    <Button
                        variant="contained"
                        onClick={handleSubmit}
                        disabled={submitting}
                        startIcon={submitting && <CircularProgress size={20} />}>
                        {t('webDAV.import.confirm')}
                    </Button>
                </DialogActions>
            </BaseDialog>
        )
    },
)

export default ImportDialog
