import {Box, Button, Stack} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {exportDataByWebDav, getEventsForExport} from '@api/sdk.gen.ts'
import {useState} from 'react'
import {WebDAVExportType} from '@api/types.gen.ts'
import {createInitialExportForm, createInitialImportForm} from './common'
import ExportDialog from './ExportDialog'
import ImportDialog from './ImportDialog'
import ExportStatusDisplay from '@components/configurations/ExportStatusDisplay.tsx'

const WebDavExportImport = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [reloadStatus, setReloadStatus] = useState(false)

    const {data: eventsData} = useFetch(signal => getEventsForExport({signal}), {
        onResponse: ({data, error}) => {
            if (error) {
                feedback.error(
                    t('common.load.error.multiple.short', {
                        entity: t('event.events'),
                    }),
                )
            } else {
                setFormData(createInitialExportForm(data))
            }
        },
        mapData: data => {
            const events = data?.sort((a, b) => (a.name < b.name ? -1 : 1))
            events.forEach(eventDto => {
                eventDto.competitions.sort((a, b) => (a.identifier < b.identifier ? -1 : 1))
            })
            return events
        },

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

    const openImportDialog = () => {
        setImportDialogOpen(true)
        setImportFormData(createInitialImportForm())
    }

    const [activeStep, setActiveStep] = useState(0)

    const openExportDialog = () => {
        setDialogOpen(true)
        setActiveStep(0)
        setFormData(createInitialExportForm(eventsData ?? []))
    }

    const closeExportDialog = () => {
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
                        selectedCompetitions: event.selectedCompetitionIds
                            .filter(comp => comp.checked)
                            .map(comp => comp.competitionId),
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
            closeExportDialog()
            feedback.success(t('webDAV.export.success'))
        }
        setReloadStatus(prev => !prev)
    }

    return (
        <Stack spacing={4} sx={{maxWidth: 600}}>
            <Box>
                <Button variant={'contained'} onClick={openExportDialog}>
                    {t('webDAV.export.export')}
                </Button>
                <Button variant={'contained'} onClick={() => openImportDialog()}>
                    {t('webDAV.import.import')}
                </Button>
            </Box>
            <ExportDialog
                open={dialogOpen}
                onClose={closeExportDialog}
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
                dialogOpen={importDialogOpen}
                onClose={() => setImportDialogOpen(false)}
                formData={importFormData}
                setFormData={setImportFormData}
                webDavExportTypeNames={webDavExportTypeNames}
            />
            <ExportStatusDisplay reloadExportStatus={reloadStatus} />
        </Stack>
    )
}
export default WebDavExportImport
