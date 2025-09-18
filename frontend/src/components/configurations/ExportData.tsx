import {Alert, Box, Button, Stack} from '@mui/material'
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
import {useState} from 'react'
import Throbber from '@components/Throbber.tsx'
import {WebDAVExportType} from '@api/types.gen.ts'
import {createInitialExportForm, createInitialImportForm} from './common'
import ExportDialog from './ExportDialog'
import ImportDialog from './ImportDialog'
import ExportStatusCard from './ExportStatusCard'

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
                setFormData(createInitialExportForm(data.data))
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
        ['DB_USERS', t('webDAV.export.types.users')],
        ['DB_PARTICIPANTS', t('webDAV.export.types.participants')],
        ['DB_BANK_ACCOUNTS', t('webDAV.export.types.bankAccounts')],
        ['DB_CONTACT_INFORMATION', t('webDAV.export.types.contactInformation')],
        ['DB_EMAIL_INDIVIDUAL_TEMPLATES', t('webDAV.export.types.emailIndividualTemplates')],
        ['DB_EVENT_DOCUMENT_TYPES', t('webDAV.export.types.eventDocumentTypes')],
        ['DB_MATCH_RESULT_IMPORT_CONFIGS', t('webDAV.export.types.matchResultImportConfigs')],
        ['DB_STARTLIST_EXPORT_CONFIGS', t('webDAV.export.types.startlistExportConfigs')],
        ['DB_WORK_TYPES', t('webDAV.export.types.workTypes')],
        ['DB_PARTICIPANT_REQUIREMENTS', t('webDAV.export.types.participantRequirements')],
        ['DB_RATING_CATEGORIES', t('webDAV.export.types.ratingCategories')],
        ['DB_COMPETITION_CATEGORIES', t('webDAV.export.types.competitionCategories')],
        ['DB_FEES', t('webDAV.export.types.fees')],
        ['DB_NAMED_PARTICIPANTS', t('webDAV.export.types.namedParticipants')],
        ['DB_COMPETITION_SETUP_TEMPLATES', t('webDAV.export.types.competitionSetupTemplates')],
        ['DB_COMPETITION_TEMPLATES', t('webDAV.export.types.competitionTemplates')],
        ['DB_EVENT', t('webDAV.export.types.event')],
        ['DB_COMPETITION', t('webDAV.export.types.competition')],
    ])

    const [formData, setFormData] = useState(() => createInitialExportForm([]))

    const [submitting, setSubmitting] = useState(false)

    const [dialogOpen, setDialogOpen] = useState(false)
    const [importDialogOpen, setImportDialogOpen] = useState(false)
    const [importFormData, setImportFormData] = useState(() => createInitialImportForm())

    const [activeStep, setActiveStep] = useState(0)

    const openDialog = () => {
        setDialogOpen(true)
        setActiveStep(0)
    }

    const closeDialog = () => {
        setDialogOpen(false)
        setActiveStep(0)
    }

    const [nameError, setNameError] = useState<string>('')

    const handleSubmit = async () => {
        // Clear previous errors
        setNameError('')

        // Validate name
        if (!formData.name.trim()) {
            setNameError(t('common.form.required'))
            setActiveStep(0)
            return
        }

        setSubmitting(true)
        const {error} = await exportDataByWebDav({
            body: {
                name: formData.name,
                events: formData.events
                    .filter(event => event.docExportChecked || event.exportData)
                    .map(event => ({
                        eventId: event.eventId,
                        selectedExports: [
                            ...event.selectedDocExports
                                .filter(e => e.checked)
                                .map(value => value.type),
                            ...(event.exportData ? ['DB_EVENT'] : []),
                        ],
                        selectedCompetitions: [
                            'a4f3edf0-b08f-4f92-8e10-df37d5cc981b',
                            '05fdb8a6-687e-4990-8d78-698e09e1db9f',
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
                setNameError(t('webDAV.export.error.nameConflict'))
                setActiveStep(0)
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
            setFormData(createInitialExportForm(eventsData ?? []))
        }
        reloadExportStatus()
    }

    return (
        <Stack spacing={4} sx={{maxWidth: 600}}>
            <Box>
                <Button variant={'contained'} onClick={openDialog}>
                    {t('webDAV.export.export')}
                </Button>
                <Button variant={'contained'} onClick={() => setImportDialogOpen(true)}>
                    {t('webDAV.import.import')}
                </Button>
            </Box>
            <ExportDialog
                open={dialogOpen}
                onClose={closeDialog}
                formData={formData}
                setFormData={setFormData}
                eventsData={eventsData ?? undefined}
                webDavExportTypeNames={webDavExportTypeNames}
                onSubmit={handleSubmit}
                submitting={submitting}
                nameError={nameError}
                setNameError={setNameError}
                activeStep={activeStep}
                setActiveStep={setActiveStep}
            />

            <ImportDialog
                open={importDialogOpen}
                onClose={() => setImportDialogOpen(false)}
                formData={importFormData}
                setFormData={setImportFormData}
                webDavExportTypeNames={webDavExportTypeNames}
                onSubmit={async () => {
                    if (!importFormData.folderName.trim()) {
                        feedback.error(t('common.form.required'))
                        return
                    }
                    const selectedTypes = importFormData.selectedData
                        .filter(item => item.checked)
                        .map(item => item.type)

                    if (selectedTypes.length === 0) {
                        feedback.error(t('webDAV.import.error.noSelection'))
                        return
                    }

                    const {error} = await importDatafromWebDav({
                        body: {
                            folderName: importFormData.folderName,
                            selectedData: selectedTypes,
                        },
                    })

                    if (error) {
                        feedback.error(t('webDAV.import.error.failed'))
                    } else {
                        feedback.success(t('webDAV.import.success'))
                        setImportDialogOpen(false)
                        setImportFormData(createInitialImportForm())
                    }
                }}
            />
            {exportStatusData ? (
                <Stack spacing={2}>
                    {exportStatusData
                        .sort((a, b) => (a.exportInitializedAt > b.exportInitializedAt ? -1 : 1))
                        .map(exportStatus => (
                            <ExportStatusCard
                                key={exportStatus.processId}
                                exportStatus={exportStatus}
                                webDavExportTypeNames={webDavExportTypeNames}
                            />
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
