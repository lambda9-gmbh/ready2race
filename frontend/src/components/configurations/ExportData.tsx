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
    Stepper,
    Step,
    StepLabel,
    Typography,
    Divider,
} from '@mui/material'
import BaseDialog from '@components/BaseDialog.tsx'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {
    exportDataByWebDav,
    getEvents,
    getWebDavExportStatus,
    getWebDavImportOptionFolders,
    getWebDavImportOptionTypes,
    importDatafromWebDav,
} from '@api/sdk.gen.ts'
import {useState, useEffect} from 'react'
import {format} from 'date-fns'
import Throbber from '@components/Throbber.tsx'
import {WebDAVExportType} from '@api/types.gen.ts'

type ExportForm = {
    name: string
    events: {
        eventId: string
        docExportChecked: boolean
        selectedDocExports: ExportFormCheckType[]
        exportData: boolean
    }[]
    checkedDatabaseExports: ExportFormCheckType[]
}
type ExportFormCheckType = {
    type: WebDAVExportType
    checked: boolean
}

const EVENT_TYPE_OPTIONS: WebDAVExportType[] = [
    'REGISTRATION_RESULTS',
    'INVOICES',
    'DOCUMENTS',
    'RESULTS',
    'START_LISTS',
]

const DATA_TYPE_OPTIONS: WebDAVExportType[] = [
    'DB_USERS',
    'DB_PARTICIPANTS',
    'DB_BANK_ACCOUNTS',
    'DB_CONTACT_INFORMATION',
    'DB_EMAIL_INDIVIDUAL_TEMPLATES',
    'DB_EVENT_DOCUMENT_TYPES',
    'DB_MATCH_RESULT_IMPORT_CONFIGS',
    'DB_STARTLIST_EXPORT_CONFIGS',
    'DB_WORK_TYPES',
    'DB_PARTICIPANT_REQUIREMENTS',
    'DB_RATING_CATEGORIES',
    'DB_COMPETITION_CATEGORIES',
    'DB_FEES',
    'DB_NAMED_PARTICIPANTS',
    'DB_COMPETITION_SETUP_TEMPLATES',
    'DB_COMPETITION_TEMPLATES',
]

// Mirror the backend dependency structure
const webDAVExportTypeDependencies: Partial<Record<WebDAVExportType, WebDAVExportType[]>> = {
    'DB_PARTICIPANTS': ['DB_USERS'],
    'DB_BANK_ACCOUNTS': ['DB_USERS'],
    'DB_CONTACT_INFORMATION': ['DB_USERS'],
    'DB_EMAIL_INDIVIDUAL_TEMPLATES': ['DB_USERS'],
    'DB_EVENT_DOCUMENT_TYPES': ['DB_USERS'],
    'DB_MATCH_RESULT_IMPORT_CONFIGS': ['DB_USERS'],
    'DB_STARTLIST_EXPORT_CONFIGS': ['DB_USERS'],
    'DB_WORK_TYPES': ['DB_USERS'],
    'DB_PARTICIPANT_REQUIREMENTS': ['DB_USERS'],
    'DB_RATING_CATEGORIES': ['DB_USERS'],
    'DB_COMPETITION_CATEGORIES': ['DB_USERS'],
    'DB_FEES': ['DB_USERS'],
    'DB_NAMED_PARTICIPANTS': ['DB_USERS'],
    'DB_COMPETITION_SETUP_TEMPLATES': ['DB_USERS'],
    'DB_COMPETITION_TEMPLATES': [
        'DB_USERS',
        'DB_COMPETITION_SETUP_TEMPLATES',
        'DB_COMPETITION_CATEGORIES',
        'DB_FEES',
        'DB_NAMED_PARTICIPANTS'
    ],
    'DB_EVENT': [
        'DB_USERS',
        'DB_CONTACT_INFORMATION',
        'DB_BANK_ACCOUNTS',
        'DB_PARTICIPANT_REQUIREMENTS',
    ],
    'DB_COMPETITION': [
        'DB_USERS',
        'DB_EVENT',
        'DB_COMPETITION_CATEGORIES',
        'DB_FEES',
        'DB_NAMED_PARTICIPANTS'
    ]
} as const

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

    useFetch(signal => getWebDavImportOptionFolders({signal}))

    useFetch(signal => getWebDavImportOptionTypes({path: {folderName: '007'}, signal}))

    const {data: eventsData} = useFetch(signal => getEvents({signal}), {
        onResponse: ({data, error}) => {
            if (error) {
                feedback.error(
                    t('common.load.error.multiple.short', {
                        entity: t('event.events'),
                    }),
                )
            } else {
                formContext.reset({
                    name: '',
                    events: data.data.map(event => ({
                        eventId: event.id,
                        docExportChecked: false,
                        selectedDocExports: EVENT_TYPE_OPTIONS.map(type => ({
                            type: type,
                            checked: false,
                        })),
                        exportData: false,
                    })),
                    checkedDatabaseExports: DATA_TYPE_OPTIONS.map(type => ({
                        type: type,
                        checked: false,
                    })),
                })
            }
        },
        mapData: data => data?.data.sort((a, b) => (a.name < b.name ? -1 : 1)),
        deps: [],
    })

    const webDavExportTypeNames = new Map<WebDAVExportType, string>([
        ['REGISTRATION_RESULTS', t('webDAV.export.types.registrationResults')],
        ['INVOICES', t('webDAV.export.types.invoices')],
        ['DOCUMENTS', t('webDAV.export.types.documents')],
        ['RESULTS', t('webDAV.export.types.results')],
        ['START_LISTS', t('webDAV.export.types.startLists')],
        ['DB_USERS', '[todo] Users'],
        ['DB_PARTICIPANTS', '[todo] Participants'],
        ['DB_BANK_ACCOUNTS', '[todo] Bank Accounts'],
        ['DB_CONTACT_INFORMATION', '[todo] Contact Information'],
        ['DB_EMAIL_INDIVIDUAL_TEMPLATES', '[todo] Email Individual Templates'],
        ['DB_EVENT_DOCUMENT_TYPES', '[todo] Event Document Types'],
        ['DB_MATCH_RESULT_IMPORT_CONFIGS', '[todo] Match Result Import Configs'],
        ['DB_STARTLIST_EXPORT_CONFIGS', '[todo] Startlist Export Configs'],
        ['DB_WORK_TYPES', '[todo] Work Types'],
        ['DB_PARTICIPANT_REQUIREMENTS', '[todo] Participant Requirements'],
        ['DB_RATING_CATEGORIES', '[todo] Rating Categories'],
        ['DB_COMPETITION_CATEGORIES', '[todo] Competition Categories'],
        ['DB_FEES', '[todo] Fees'],
        ['DB_NAMED_PARTICIPANTS', '[todo] Named Participants'],
        ['DB_COMPETITION_SETUP_TEMPLATES', '[todo] Competition Setup Templates'],
        ['DB_COMPETITION_TEMPLATES', '[todo] Competition Templates'],
        ['DB_EVENT', '[todo] Event'],
        ['DB_COMPETITION', '[todo] Competition'],
    ])

    const formContext = useForm<ExportForm>({})

    const watchedEvents = formContext.watch('events')
    const watchedDatabaseExports = formContext.watch('checkedDatabaseExports')
    console.log(watchedEvents)

    const [submitting, setSubmitting] = useState(false)

    const [dialogOpen, setDialogOpen] = useState(false)

    const [activeStep, setActiveStep] = useState(0)

    const openDialog = () => {
        setDialogOpen(true)
        setActiveStep(0)
    }

    const closeDialog = () => {
        setDialogOpen(false)
        setActiveStep(0)
    }

    const handleNext = () => {
        setActiveStep(prevStep => prevStep + 1)
    }

    const handleBack = () => {
        setActiveStep(prevStep => prevStep - 1)
    }

    const steps = ['Name', '[todo] Documents', '[todo] Data']
    
    // Handle dependency checking
    useEffect(() => {
        if (!watchedDatabaseExports || activeStep !== 2) return
        
        const checkedTypes = watchedDatabaseExports
            .filter(item => item?.checked)
            .map(item => item.type)
        
        // Find all required dependencies
        const requiredDependencies = new Set<WebDAVExportType>()
        checkedTypes.forEach(type => {
            const deps = webDAVExportTypeDependencies[type]
            if (deps) {
                deps.forEach(dep => requiredDependencies.add(dep))
            }
        })
        
        // Check and update dependencies
        watchedDatabaseExports.forEach((item, index) => {
            if (requiredDependencies.has(item.type) && !item.checked) {
                formContext.setValue(`checkedDatabaseExports.${index}.checked`, true)
            }
        })
    }, [watchedDatabaseExports, activeStep, formContext])
    
    // Check if a type is a dependency of any checked type
    const isRequiredDependency = (type: WebDAVExportType): boolean => {
        if (!watchedDatabaseExports) return false
        
        const checkedTypes = watchedDatabaseExports
            .filter(item => item?.checked)
            .map(item => item.type)
        
        return checkedTypes.some(checkedType => {
            const deps = webDAVExportTypeDependencies[checkedType]
            return deps?.includes(type) ?? false
        })
    }

    const onSubmit = async (formData: ExportForm) => {
        setSubmitting(true)
        const {error} = await exportDataByWebDav({
            body: {
                name: formData.name,
                events: formData.events
                    .filter(event => event.docExportChecked || event.exportData)
                    .map(event => ({
                        eventId: event.eventId,
                        selectedExports: [
                            ...event.selectedDocExports.map(value => value.type),
                            ...(event.exportData ? ['DB_EVENTS'] : []),
                        ],
                    })),
                selectedDatabaseExports: formData.checkedDatabaseExports
                    .filter(type => type.checked)
                    .map(type => type.type),
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
                <Button
                    variant={'contained'}
                    onClick={() =>
                        importDatafromWebDav({
                            body: {
                                folderName: 'Quali',
                                selectedData: [
                                    'DB_USERS',
                                    'DB_PARTICIPANTS',
                                    'DB_BANK_ACCOUNTS',
                                    'DB_CONTACT_INFORMATION',
                                    'DB_EMAIL_INDIVIDUAL_TEMPLATES',
                                    'DB_EVENT_DOCUMENT_TYPES',
                                    'DB_MATCH_RESULT_IMPORT_CONFIGS',
                                    'DB_STARTLIST_EXPORT_CONFIGS',
                                    'DB_WORK_TYPES',
                                    'DB_PARTICIPANT_REQUIREMENTS',
                                    'DB_RATING_CATEGORIES',
                                    'DB_COMPETITION_CATEGORIES',
                                    'DB_FEES',
                                    'DB_NAMED_PARTICIPANTS',
                                    'DB_COMPETITION_SETUP_TEMPLATES',
                                    'DB_COMPETITION_TEMPLATES',
                                ],
                            },
                        })
                    }>
                    todo IMPORT
                </Button>
            </Box>
            <BaseDialog open={dialogOpen} onClose={closeDialog} maxWidth={'sm'}>
                <DialogTitle>{t('webDAV.export.export')}</DialogTitle>
                <FormContainer formContext={formContext} onSuccess={onSubmit}>
                    <DialogContent>
                        <Stepper activeStep={activeStep} sx={{mb: 3}}>
                            {steps.map(label => (
                                <Step key={label}>
                                    <StepLabel>{label}</StepLabel>
                                </Step>
                            ))}
                        </Stepper>

                        {activeStep === 0 && (
                            <FormInputText
                                name={'name'}
                                required
                                label={t('webDAV.export.folderName')}
                            />
                        )}

                        {activeStep === 1 && (
                            <Box>
                                {eventsData?.map((event, index) => (
                                    <Box key={event.id}>
                                        <FormInputCheckbox
                                            name={`events.${index}.docExportChecked`}
                                            label={event.name}
                                            horizontal
                                            reverse
                                        />
                                        {watchedEvents[index]?.docExportChecked && (
                                            <Box sx={{ml: 4, mt: 1}}>
                                                {watchedEvents[index]?.selectedDocExports.map(
                                                    (docExport, typeIndex) => (
                                                        <FormInputCheckbox
                                                            key={docExport.type}
                                                            name={`events.${index}.selectedExports.${typeIndex}.checked`}
                                                            label={
                                                                webDavExportTypeNames.get(
                                                                    docExport.type,
                                                                ) ?? '-'
                                                            }
                                                            horizontal
                                                            reverse
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
                                {eventsData?.map((event, index) => (
                                    <FormInputCheckbox
                                        key={event.id}
                                        name={`events.${index}.exportData`}
                                        label={event.name}
                                        horizontal
                                        reverse
                                    />
                                ))}
                                <Divider />
                                {DATA_TYPE_OPTIONS.map((exportType, index) => {
                                    const isDisabled = isRequiredDependency(exportType)
                                    return (
                                        <FormInputCheckbox
                                            key={exportType}
                                            name={`checkedDatabaseExports.${index}.checked`}
                                            label={webDavExportTypeNames.get(exportType) ?? '-'}
                                            horizontal
                                            reverse
                                            disabled={isDisabled}
                                        />
                                    )
                                })}
                            </Box>
                        )}
                    </DialogContent>
                    <DialogActions sx={{justifyContent: 'space-between'}}>
                        <Box>
                            <Button onClick={handleBack} disabled={submitting || activeStep === 0}>
                                {'[todo] Back'}
                            </Button>
                            <Button onClick={closeDialog} disabled={submitting}>
                                {t('common.cancel')}
                            </Button>
                        </Box>
                        <Box>
                            {activeStep < steps.length - 1 ? (
                                <Button variant="contained" onClick={handleNext}>
                                    {'[todo] Next'}
                                </Button>
                            ) : (
                                <SubmitButton submitting={submitting}>
                                    {t('webDAV.export.confirm')}
                                </SubmitButton>
                            )}
                        </Box>
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
                                            <Alert severity={'info'} icon={<Throbber />}>
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
                                                        {webDavExportTypeNames.get(type) ?? '-'}
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
                <Throbber />
            ) : (
                <Alert severity={'error'}>
                    {t('common.load.error.single', {entity: t('webDAV.export.status.status')})}
                </Alert>
            )}
        </Stack>
    )
}
export default ExportData
