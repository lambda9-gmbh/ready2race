import {
    Alert,
    AlertTitle,
    Box,
    Button,
    Card,
    CardContent,
    DialogActions,
    DialogContent,
    DialogTitle,
    LinearProgress,
    List,
    ListItemText,
    Stack,
    Typography,
} from '@mui/material'
import BaseDialog from '@components/BaseDialog.tsx'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {exportDataByWebDav, getEvents, getWebDavExportStatus, getWebDavImportOptions} from '@api/sdk.gen.ts'
import {AutocompleteOption} from '@utils/types.ts'
import {useState} from 'react'
import {WebDAVExportType} from '@api/types.gen.ts'
import {format} from 'date-fns'
import Throbber from '@components/Throbber.tsx'
import FormInputMultiselect from '@components/form/input/FormInputMultiselect.tsx'

type ExportForm = {
    name: string
    selectedEvents: string[]
    selectedResources: WebDAVExportType[]
}

const WEBDAV_EXPORT_TYPES: WebDAVExportType[] = [
    'REGISTRATION_RESULTS',
    'INVOICES',
    'DOCUMENTS',
    'RESULTS',
    'START_LISTS',
    'DB_USERS',
    'DB_CLUBS'
] as const

const ExportData = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [reloadFrequently, setReloadFrequently] = useState(true)

    const {
        data: exportStatusData,
        pending: exportStatusPending,
        reload: reloadExportStatus,
    } = useFetch(signal => getWebDavExportStatus({signal}), {
        onResponse: ({data, error}) => {
            if (error) {
                feedback.error(
                    t('common.load.error.single', {entity: t('webDAV.export.status.status')}),
                )
            } else {
                if (
                    data.some(
                        process =>
                            process.totalFilesToExport !==
                            process.filesExported + process.filesWithError,
                    )
                ) {
                    setReloadFrequently(true)
                } else {
                    setReloadFrequently(false)
                }
            }
        },
        deps: [reloadFrequently],
        autoReloadInterval: reloadFrequently ? 1000 : 6000,
    })

    useFetch(signal => getWebDavImportOptions({signal}))

    const {data: eventsData} = useFetch(signal => getEvents({signal}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(
                    t('common.load.error.multiple.short', {
                        entity: t('event.events'),
                    }),
                )
            }
        },
        deps: [],
    })

    const eventOptions: AutocompleteOption[] =
        eventsData?.data.map(value => ({
            id: value.id,
            label: value.name,
        })) ?? []

    const webDavExportTypes = WEBDAV_EXPORT_TYPES.map(type => ({
        id: type,
        label: (() => {
            switch (type) {
                case 'REGISTRATION_RESULTS':
                    return t('webDAV.export.types.registrationResults');
                case 'INVOICES':
                    return t('webDAV.export.types.invoices');
                case 'DOCUMENTS':
                    return t('webDAV.export.types.documents');
                case 'RESULTS':
                    return t('webDAV.export.types.results');
                case 'START_LISTS':
                    return t('webDAV.export.types.startLists');
                case 'DB_USERS':
                    return "[todo] Users";
                case 'DB_CLUBS':
                    return "[todo] Clubs";
                default:
                    return '';
            }
        })()
    }))

    const formContext = useForm<ExportForm>({})

    const [submitting, setSubmitting] = useState(false)

    const [dialogOpen, setDialogOpen] = useState(false)

    const openDialog = () => {
        setDialogOpen(true)
    }

    const closeDialog = () => {
        setDialogOpen(false)
    }

    const onSubmit = async (formData: ExportForm) => {
        setSubmitting(true)
        const {error} = await exportDataByWebDav({
            body: {
                name: formData.name,
                selectedResources: formData.selectedResources,
                events: formData.selectedEvents,
            },
        })
        setSubmitting(false)

        if (error) {
            if (error.status.value === 409) {
                formContext.setError('name', {
                    type: 'validate',
                    message: t('webDAV.export.error.nameConflict'),
                })
            } else {
                if (error.status.value === 502) {
                    feedback.error(t('webDAV.export.error.thirdPartyError'))
                } else {
                    feedback.error(t('common.error.unexpected'))
                }
            }
        } else {
            closeDialog()
            feedback.success(t('webDAV.export.success'))
            formContext.reset()
        }
        reloadExportStatus()
    }

    return (
        <Stack spacing={4} sx={{maxWidth: 600}}>
            <Box>
                <Button variant={'contained'} onClick={openDialog}>
                    {t('webDAV.export.export')}
                </Button>
            </Box>
            <BaseDialog open={dialogOpen} onClose={closeDialog} maxWidth={'xs'}>
                <DialogTitle>{t('webDAV.export.export')}</DialogTitle>
                <FormContainer formContext={formContext} onSuccess={onSubmit}>
                    <DialogContent>
                        <Stack spacing={2}>
                            <FormInputText
                                name={'name'}
                                required
                                label={t('webDAV.export.folderName')}
                            />
                            <FormInputMultiselect
                                name={'selectedEvents'}
                                label={t('webDAV.export.events')}
                                options={eventOptions}
                                showCheckbox
                                showChips
                                required
                                fullWidth
                            />
                            <FormInputMultiselect
                                name={'selectedResources'}
                                label={t('webDAV.export.data')}
                                options={webDavExportTypes}
                                showCheckbox
                                showChips
                                required
                                fullWidth
                            />
                        </Stack>
                    </DialogContent>
                    <DialogActions>
                        <Button onClick={closeDialog} disabled={submitting}>
                            {t('common.cancel')}
                        </Button>
                        <SubmitButton submitting={submitting}>
                            {t('webDAV.export.confirm')}
                        </SubmitButton>
                    </DialogActions>
                </FormContainer>
            </BaseDialog>
            {exportStatusData ? (
                <Stack spacing={2}>
                    {exportStatusData
                        .sort((a, b) => (a.exportInitializedAt > b.exportInitializedAt ? -1 : 1))
                        .map(exportStatus => (
                            <Card key={exportStatus.processId}>
                                <CardContent>
                                    {exportStatus.filesExported + exportStatus.filesWithError ===
                                    exportStatus.totalFilesToExport ? (
                                        exportStatus.filesWithError > 0 ? (
                                            <Alert severity={'error'}>
                                                <AlertTitle>
                                                    {t('webDAV.export.status.error.title')}
                                                </AlertTitle>
                                                {t('webDAV.export.status.error.body', {
                                                    exported: exportStatus.filesExported,
                                                    total: exportStatus.totalFilesToExport,
                                                })}
                                            </Alert>
                                        ) : (
                                            <Alert severity={'success'}>
                                                <AlertTitle>
                                                    {t('webDAV.export.status.success.title')}
                                                </AlertTitle>
                                                {t('webDAV.export.status.success.body', {
                                                    count: exportStatus.filesExported,
                                                })}
                                            </Alert>
                                        )
                                    ) : (
                                        <Box sx={{width: 1}}>
                                            <LinearProgress
                                                variant="determinate"
                                                value={
                                                    (exportStatus.filesExported /
                                                        exportStatus.totalFilesToExport) *
                                                    100
                                                }
                                            />
                                            <Alert severity={'info'} icon={<Throbber/>}>
                                                <AlertTitle>
                                                    {t('webDAV.export.status.pending.title')}
                                                </AlertTitle>
                                                {t('webDAV.export.status.pending.body', {
                                                    exported: exportStatus.filesExported,
                                                    total: exportStatus.totalFilesToExport,
                                                })}
                                            </Alert>
                                        </Box>
                                    )}
                                    <Box
                                        sx={{
                                            display: 'flex',
                                            flexWrap: 'wrap',
                                            gap: 2,
                                            mt: 1,
                                            mx: 2,
                                        }}>
                                        <Box sx={{flex: 1}}>
                                            <Typography variant={'h3'}>
                                                {exportStatus.exportFolderName}
                                            </Typography>

                                            <List>
                                                {exportStatus.events.map(event => (
                                                    <ListItemText
                                                        key={exportStatus.processId + event}>
                                                        {event}
                                                    </ListItemText>
                                                ))}
                                            </List>
                                            <List>
                                                {exportStatus.exportTypes.map(type => (
                                                    <ListItemText
                                                        key={exportStatus.processId + type}>
                                                        {webDavExportTypes.find(t => t.id === type)
                                                            ?.label ?? '-'}
                                                    </ListItemText>
                                                ))}
                                            </List>
                                        </Box>
                                        <Box sx={{textAlign: 'right'}}>
                                            <Typography variant={'subtitle2'}>
                                                {format(
                                                    new Date(exportStatus.exportInitializedAt),
                                                    t('format.datetime'),
                                                )}
                                            </Typography>
                                            {exportStatus.exportInitializedBy && (
                                                <Typography variant={'subtitle2'}>
                                                    {exportStatus.exportInitializedBy.firstname}{' '}
                                                    {exportStatus.exportInitializedBy.lastname}
                                                </Typography>
                                            )}
                                        </Box>
                                    </Box>
                                </CardContent>
                            </Card>
                        ))}
                </Stack>
            ) : exportStatusPending ? (
                <Throbber/>
            ) : (
                <Alert severity={'error'}>
                    {t('common.load.error.single', {entity: t('webDAV.export.status.status')})}
                </Alert>
            )}
        </Stack>
    )
}
export default ExportData
