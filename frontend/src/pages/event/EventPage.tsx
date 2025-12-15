import {
    Box,
    Button,
    Card,
    List,
    ListItem,
    Stack,
    Tab,
    Typography,
    ListItemIcon,
    ListItemText,
    Link as MuiLink,
} from '@mui/material'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import {eventIndexRoute, eventRoute} from '@routes'
import {Trans, useTranslation} from 'react-i18next'
import Throbber from '@components/Throbber.tsx'
import {downloadEventResults, getEvent, sendCertificatesToParticipants} from '@api/sdk.gen.ts'
import {
    EventDocumentDto,
    ParticipantForEventDto,
    ParticipantRequirementForEventDto,
    ParticipantTrackingDto,
    TaskDto,
} from '@api/types.gen.ts'
import DocumentTable from '@components/event/document/DocumentTable.tsx'
import DocumentDialog from '@components/event/document/DocumentDialog.tsx'
import {Forward, InfoOutlined, PlayCircleOutlined} from '@mui/icons-material'
import {Link, useNavigate} from '@tanstack/react-router'
import {useMemo, useRef, useState} from 'react'
import TabPanel from '@components/tab/TabPanel.tsx'
import ParticipantRequirementForEventTable from '@components/event/participantRequirement/ParticipantRequirementForEventTable.tsx'
import ParticipantForEventTable from '@components/participant/ParticipantForEventTable.tsx'
import {useUser} from '@contexts/user/UserContext.ts'
import {
    createInvoiceGlobal,
    readEventGlobal,
    readRegistrationGlobal,
    readRegistrationOwn,
    readUserGlobal,
    updateEventGlobal,
} from '@authorization/privileges.ts'
import TabSelectionContainer from '@components/tab/TabSelectionContainer.tsx'
import InlineLink from '@components/InlineLink.tsx'
import TaskTable from '@components/event/task/TaskTable.tsx'
import TaskDialog from '@components/event/task/TaskDialog.tsx'
import {Shiftplan} from '@components/event/shiftplan/Shiftplan.tsx'
import {
    a11yProps,
    getFilename,
    getRegistrationPeriods,
    getRegistrationState,
} from '@utils/helpers.ts'
import PlaceIcon from '@mui/icons-material/Place'
import CompetitionsAndEventDays from '@components/event/CompetitionsAndEventDays.tsx'
import AccessTimeIcon from '@mui/icons-material/AccessTime'
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty'
import {format} from 'date-fns'
import AppUserWithQrCodeTable from '@components/event/appUser/AppUserWithQrCodeTable.tsx'
import InvoicesTab from './tabs/InvoicesTab.tsx'
import {AppUserWithQrCodeDto} from '@api/types.gen.ts'
import ParticipantTrackingLogTable from '@components/event/participantTracking/ParticipantTrackingLogTable.tsx'
import EventRegistrations from '@components/event/competition/registration/EventRegistrations.tsx'
import ManageRunningMatchesDialog from '@components/event/match/ManageRunningMatchesDialog.tsx'
import RatingCategoriesForEvent from '@components/ratingCategory/RatingCategoriesForEvent.tsx'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext.ts'

const EVENT_TABS = [
    'general',
    'competitions',
    'participants',
    'registrations',
    'organization',
    'settings',
    'invoices',
] as const
export type EventTab = (typeof EVENT_TABS)[number]

const EventPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()
    const {confirmAction} = useConfirmation()

    const downloadRef = useRef<HTMLAnchorElement>(null)

    const {tab} = eventIndexRoute.useSearch()
    const activeTab = tab ?? 'general'

    const navigate = useNavigate()
    const switchTab = (tab: EventTab) => {
        navigate({from: eventIndexRoute.fullPath, search: {tab}}).then()
    }

    const {eventId} = eventRoute.useParams()

    const [lastRequested, setLastRequested] = useState(Date.now())
    const reload = () => setLastRequested(Date.now())

    const [manageRunningMatchesOpen, setManageRunningMatchesOpen] = useState(false)
    const {data, pending} = useFetch(signal => getEvent({signal, path: {eventId: eventId}}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(t('common.load.error.single', {entity: t('event.event')}))
            }
        },
        deps: [eventId, lastRequested],
    })

    const documentAdministrationProps = useEntityAdministration<EventDocumentDto>(
        t('event.document.document'),
    )
    const participantRequirementAdministrationProps =
        useEntityAdministration<ParticipantRequirementForEventDto>(
            t('participantRequirement.participantRequirements'),
            {entityCreate: false, entityUpdate: false},
        )

    const participantForEventProps = useEntityAdministration<ParticipantForEventDto>(
        t('club.participant.title'),
        {entityCreate: false, entityUpdate: false},
    )

    const participantTrackingProps = useEntityAdministration<ParticipantTrackingDto>(
        t('club.participant.tracking.entry'),
        {entityCreate: false, entityUpdate: false},
    )

    const taskProps = useEntityAdministration<TaskDto>(t('task.task'))

    const appUserWithQrCodeProps = useEntityAdministration<AppUserWithQrCodeDto>(
        t('qrCode.appUsersWithQrCode'),
        {entityCreate: false, entityUpdate: false},
    )

    const tabProps = (tab: EventTab) => a11yProps('event', tab)

    const registrationState = getRegistrationState(data ?? {})

    const canRegister = useMemo(
        () => user.loggedIn && user.clubId && registrationState !== 'CLOSED',
        [data, user],
    )

    const {registrationPeriod, lateRegistrationPeriod} = getRegistrationPeriods(data ?? {}, t)

    const handleResultsDownload = async () => {
        const {data, error, response} = await downloadEventResults({
            path: {eventId},
        })
        const anchor = downloadRef.current

        if (error) {
            feedback.error(t('event.results.downloadError'))
        } else if (data !== undefined && anchor) {
            anchor.href = URL.createObjectURL(data)
            anchor.download = getFilename(response) ?? 'event-results.pdf'
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    const handleCertificateSending = async () => {
        const {error} = await sendCertificatesToParticipants({
            path: {eventId},
        })

        if (error) {
            feedback.error(t('common.error.unexpected'))
        } else {
            feedback.success(t('event.action.sendCertificatesSucceeded'))
        }
    }

    return (
        <Box>
            <MuiLink ref={downloadRef} display={'none'}></MuiLink>
            <Box sx={{display: 'flex', flexDirection: 'column'}}>
                {data ? (
                    <Stack spacing={4}>
                        <Stack
                            direction={'row'}
                            justifyContent={'space-between'}
                            alignItems={'center'}>
                            <Typography variant="h1">{data.name}</Typography>
                            <Link
                                to={'/event/$eventId/register'}
                                params={{eventId}}
                                hidden={!canRegister}>
                                <Button endIcon={<Forward />} variant={'contained'}>
                                    {t('event.registerNow')}
                                </Button>
                            </Link>
                        </Stack>
                        <TabSelectionContainer activeTab={activeTab} setActiveTab={switchTab}>
                            <Tab label={t('event.tabs.general')} {...tabProps('general')} />
                            <Tab
                                label={t('event.competition.competitions')}
                                {...tabProps('competitions')}
                            />
                            {(user.checkPrivilege(readRegistrationGlobal) ||
                                user.checkPrivilege(readRegistrationOwn)) && (
                                <Tab
                                    label={t('event.participants')}
                                    {...tabProps('participants')}
                                />
                            )}
                            {user.checkPrivilege(readEventGlobal) && (
                                <Tab
                                    label={t('event.tabs.registrations')}
                                    {...tabProps('registrations')}
                                />
                            )}
                            {user.checkPrivilege(readEventGlobal) &&
                                user.checkPrivilege(readUserGlobal) && (
                                    <Tab
                                        label={t('event.tabs.organisation')}
                                        {...tabProps('organization')}
                                    />
                                )}
                            {user.checkPrivilege(readEventGlobal) && (
                                <Tab label={t('event.tabs.settings')} {...tabProps('settings')} />
                            )}
                            {((user.getPrivilegeScope('READ', 'INVOICE') && true) ||
                                user.checkPrivilege(createInvoiceGlobal)) && (
                                <Tab label={t('event.tabs.invoices')} {...tabProps('invoices')} />
                            )}
                        </TabSelectionContainer>
                        <TabPanel index={'general'} activeTab={activeTab}>
                            <Stack spacing={4}>
                                <Card
                                    sx={{
                                        p: 2,
                                        display: 'flex',
                                        flexDirection: 'column',
                                        gap: 2,
                                    }}>
                                    {user.checkPrivilege(readEventGlobal) && (
                                        <Typography variant={'overline'}>
                                            {data.published
                                                ? t('event.published.published')
                                                : t('event.published.not')}
                                        </Typography>
                                    )}
                                    {data.description && (
                                        <Typography>{data.description}</Typography>
                                    )}
                                    <Box>
                                        <List>
                                            {data.location && (
                                                <ListItem>
                                                    <ListItemIcon>
                                                        <PlaceIcon />
                                                    </ListItemIcon>
                                                    <ListItemText primary={data.location} />
                                                </ListItem>
                                            )}
                                            {(data.registrationAvailableFrom ||
                                                data.registrationAvailableTo) && (
                                                <ListItem>
                                                    <ListItemIcon>
                                                        <AccessTimeIcon />
                                                    </ListItemIcon>
                                                    <ListItemText
                                                        primary={
                                                            t(
                                                                'event.registrationAvailable.timespan',
                                                            ) +
                                                            ': ' +
                                                            registrationPeriod
                                                        }
                                                        secondary={
                                                            lateRegistrationPeriod &&
                                                            t(
                                                                'event.registrationAvailable.lateTimespan',
                                                            ) +
                                                                ': ' +
                                                                lateRegistrationPeriod
                                                        }
                                                    />
                                                </ListItem>
                                            )}
                                            {data.paymentDueBy && (
                                                <ListItem>
                                                    <ListItemIcon>
                                                        <HourglassEmptyIcon />
                                                    </ListItemIcon>
                                                    <ListItemText
                                                        primary={
                                                            t('event.invoice.paymentDueBy') +
                                                            ': ' +
                                                            format(
                                                                new Date(data.paymentDueBy),
                                                                t('format.datetime'),
                                                            )
                                                        }
                                                    />
                                                </ListItem>
                                            )}
                                        </List>
                                    </Box>
                                </Card>
                                <Card sx={{p: 2, display: 'flex', gap: 2, flexWrap: 'wrap'}}>
                                    <Link
                                        to={'/results/event/$eventId'}
                                        params={{eventId: data.id}}>
                                        <Button variant={'outlined'}>
                                            <Trans i18nKey={'event.toResults'} />
                                        </Button>
                                    </Link>
                                    {(user.checkPrivilege(readEventGlobal) || data.published) &&
                                        !data.challengeEvent && (
                                            <Button
                                                variant={'outlined'}
                                                onClick={handleResultsDownload}>
                                                <Trans i18nKey={'event.results.download'} />
                                            </Button>
                                        )}
                                    {user.checkPrivilege(updateEventGlobal) &&
                                        data.challengeEvent &&
                                        data.challengesFinished && (
                                            <Button
                                                variant={'outlined'}
                                                onClick={() =>
                                                    confirmAction(handleCertificateSending)
                                                }>
                                                <Trans i18nKey={'event.action.sendCertificates'} />
                                            </Button>
                                        )}
                                </Card>
                                {user.checkPrivilege(readEventGlobal) && !data.challengeEvent && (
                                    <Card sx={{p: 2}}>
                                        <Typography variant="h6" sx={{mb: 1}}>
                                            {t('event.info.sectionTitle')}
                                        </Typography>
                                        <Typography
                                            variant="body2"
                                            color="text.secondary"
                                            sx={{mb: 2}}>
                                            {t('event.info.pageDescription')}
                                        </Typography>
                                        <Link to={'/event/$eventId/info'} params={{eventId}}>
                                            <Button
                                                startIcon={<InfoOutlined />}
                                                variant="outlined"
                                                fullWidth>
                                                {t('event.info.manageInfoViews')}
                                            </Button>
                                        </Link>
                                        <Button
                                            startIcon={<PlayCircleOutlined />}
                                            variant="outlined"
                                            fullWidth
                                            sx={{mt: 1}}
                                            onClick={() => setManageRunningMatchesOpen(true)}>
                                            {t('event.competition.execution.match.manageRunning')}
                                        </Button>
                                    </Card>
                                )}
                            </Stack>
                        </TabPanel>
                        <TabPanel index={'competitions'} activeTab={activeTab}>
                            <CompetitionsAndEventDays isChallengeEvent={data.challengeEvent} />
                        </TabPanel>
                        <TabPanel index={'registrations'} activeTab={activeTab}>
                            <EventRegistrations
                                registrationsFinalized={data.registrationsFinalized}
                                eventDto={data}
                            />
                        </TabPanel>
                        <TabPanel index={'participants'} activeTab={activeTab}>
                            <Stack spacing={2}>
                                <ParticipantForEventTable
                                    eventData={data}
                                    {...participantForEventProps.table}
                                    title={t('event.participants')}
                                />
                                <ParticipantTrackingLogTable
                                    {...participantTrackingProps.table}
                                    title={t('club.participant.tracking.log')}
                                />
                            </Stack>
                        </TabPanel>
                        <TabPanel index={'organization'} activeTab={activeTab}>
                            <Stack spacing={2}>
                                <AppUserWithQrCodeTable
                                    {...appUserWithQrCodeProps.table}
                                    title={t('qrCode.appUsersWithQrCode')}
                                />
                                <TaskTable {...taskProps.table} title={t('task.tasks')} />
                                <TaskDialog {...taskProps.dialog} eventId={eventId} />
                                <Shiftplan />
                            </Stack>
                        </TabPanel>
                        <TabPanel index={'settings'} activeTab={activeTab}>
                            <Stack spacing={4}>
                                <RatingCategoriesForEvent />
                                <DocumentTable
                                    {...documentAdministrationProps.table}
                                    title={t('event.document.documents')}
                                    hints={[
                                        <>{t('event.document.tableHint.description')}</>,
                                        <>
                                            {t('event.document.tableHint.part1')}
                                            <InlineLink
                                                to={'/config'}
                                                search={{
                                                    tab: 'event-elements',
                                                }}>
                                                {t('event.document.tableHint.part2Link')}
                                            </InlineLink>
                                            {t('event.document.tableHint.part3')}
                                        </>,
                                    ]}
                                />
                                <DocumentDialog {...documentAdministrationProps.dialog} />
                                <ParticipantRequirementForEventTable
                                    {...participantRequirementAdministrationProps.table}
                                    title={t('participantRequirement.participantRequirements')}
                                    hints={[
                                        <>
                                            {t('event.participantRequirement.tableHint.part1')}
                                            <InlineLink
                                                to={'/config'}
                                                search={{tab: 'event-elements'}}>
                                                {t(
                                                    'event.participantRequirement.tableHint.part2Link',
                                                )}
                                            </InlineLink>
                                            {t('event.participantRequirement.tableHint.part3')}
                                        </>,
                                    ]}
                                />
                            </Stack>
                        </TabPanel>
                        <TabPanel index={'invoices'} activeTab={activeTab}>
                            <InvoicesTab event={data} reloadEvent={reload} />
                        </TabPanel>
                    </Stack>
                ) : (
                    pending && <Throbber />
                )}
            </Box>
            {manageRunningMatchesOpen && (
                <ManageRunningMatchesDialog
                    open={manageRunningMatchesOpen}
                    onClose={() => setManageRunningMatchesOpen(false)}
                    eventId={eventId}
                />
            )}
        </Box>
    )
}

export default EventPage
