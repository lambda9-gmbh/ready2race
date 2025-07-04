import {
    Box,
    Button,
    Card,
    List,
    ListItem,
    Link as MuiLink,
    Stack,
    Tab,
    Typography,
    ListItemIcon,
    ListItemText,
} from '@mui/material'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import {eventIndexRoute, eventRoute} from '@routes'
import {useTranslation} from 'react-i18next'
import Throbber from '@components/Throbber.tsx'
import {getEvent, getRegistrationResult} from '@api/sdk.gen.ts'
import {
    EventDocumentDto,
    EventRegistrationViewDto,
    ParticipantForEventDto,
    ParticipantRequirementForEventDto,
    TaskDto,
} from '@api/types.gen.ts'
import DocumentTable from '@components/event/document/DocumentTable.tsx'
import DocumentDialog from '@components/event/document/DocumentDialog.tsx'
import {Forward} from '@mui/icons-material'
import {Link, useNavigate} from '@tanstack/react-router'
import {useMemo, useRef, useState} from 'react'
import TabPanel from '@components/tab/TabPanel.tsx'
import ParticipantRequirementForEventTable from '@components/event/participantRequirement/ParticipantRequirementForEventTable.tsx'
import ParticipantForEventTable from '@components/participant/ParticipantForEventTable.tsx'
import {useUser} from '@contexts/user/UserContext.ts'
import {
    readEventGlobal,
    readInvoiceGlobal,
    readRegistrationGlobal,
    readRegistrationOwn,
    readUserGlobal,
} from '@authorization/privileges.ts'
import TabSelectionContainer from '@components/tab/TabSelectionContainer.tsx'
import InlineLink from '@components/InlineLink.tsx'
import TaskTable from '@components/event/task/TaskTable.tsx'
import TaskDialog from '@components/event/task/TaskDialog.tsx'
import {Shiftplan} from '@components/event/shiftplan/Shiftplan.tsx'
import {eventRegistrationPossible} from '@utils/helpers.ts'
import EventRegistrationTable from '@components/eventRegistration/EventRegistrationTable.tsx'
import PlaceIcon from '@mui/icons-material/Place'
import CompetitionsAndEventDays from '@components/event/CompetitionsAndEventDays.tsx'
import AccessTimeIcon from '@mui/icons-material/AccessTime'
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty'
import {format} from 'date-fns'
import InvoicesTabPanel from './tabs/InvoicesTabPanel.tsx'

const EVENT_TABS = [
    'general',
    'competitions',
    'participants',
    'registrations',
    'organization',
    'settings',
    'actions',
    'invoices',
] as const
export type EventTab = (typeof EVENT_TABS)[number]

const EventPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()

    const {tab} = eventIndexRoute.useSearch()
    const activeTab = tab ?? 'general'

    const navigate = useNavigate()
    const switchTab = (tab: EventTab) => {
        navigate({from: eventIndexRoute.fullPath, search: {tab}}).then()
    }

    const downloadRef = useRef<HTMLAnchorElement>(null)

    const {eventId} = eventRoute.useParams()

    const [lastRequested, setLastRequested] = useState(Date.now())
    const reload = () => setLastRequested(Date.now())
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

    const eventRegistrationProps = useEntityAdministration<EventRegistrationViewDto>(
        t('event.registration.registration'),
        {entityCreate: false, entityUpdate: false},
    )

    const taskProps = useEntityAdministration<TaskDto>(t('task.task'))

    const a11yProps = (index: EventTab) => {
        return {
            value: index,
            id: `event-tab-${index}`,
            'aria-controls': `event-tabpanel-${index}`,
        }
    }

    const canRegister = useMemo(
        () =>
            user.loggedIn &&
            user.clubId != null &&
            eventRegistrationPossible(
                data?.registrationAvailableFrom,
                data?.registrationAvailableTo,
            ),
        [data, user],
    )

    // TODO @Duplication: see InvoiceTable download
    const handleReportDownload = async () => {
        const {data, error, response} = await getRegistrationResult({
            path: {eventId},
            query: {
                remake: true,
            },
        })
        const anchor = downloadRef.current

        const disposition = response.headers.get('Content-Disposition')
        const filename = disposition?.match(/attachment; filename="?(.+)"?/)?.[1]

        if (error) {
            feedback.error(t('event.document.download.error'))
        } else if (data !== undefined && anchor) {
            anchor.href = URL.createObjectURL(data)
            anchor.download = filename ?? 'registration_result.pdf'
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    const regAvailableFrom = data?.registrationAvailableFrom
        ? format(new Date(data.registrationAvailableFrom), t('format.datetime'))
        : undefined
    const regAvailableTo = data?.registrationAvailableTo
        ? format(new Date(data.registrationAvailableTo), t('format.datetime'))
        : undefined

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
                            <Tab label={t('event.tabs.general')} {...a11yProps('general')} />
                            <Tab
                                label={t('event.competition.competitions')}
                                {...a11yProps('competitions')}
                            />
                            {(user.checkPrivilege(readRegistrationGlobal) ||
                                user.checkPrivilege(readRegistrationOwn)) && (
                                <Tab
                                    label={t('event.participants')}
                                    {...a11yProps('participants')}
                                />
                            )}
                            {user.checkPrivilege(readEventGlobal) && (
                                <Tab
                                    label={t('event.tabs.registrations')}
                                    {...a11yProps('registrations')}
                                />
                            )}
                            {user.checkPrivilege(readEventGlobal) &&
                                user.checkPrivilege(readUserGlobal) && (
                                    <Tab
                                        label={t('event.tabs.organisation')}
                                        {...a11yProps('organization')}
                                    />
                                )}
                            {user.checkPrivilege(readEventGlobal) && (
                                <Tab label={t('event.tabs.settings')} {...a11yProps('settings')} />
                            )}
                            {user.checkPrivilege(readEventGlobal) && (
                                <Tab label={t('event.tabs.actions')} {...a11yProps('actions')} />
                            )}
                            {user.checkPrivilege(readInvoiceGlobal) && (
                                <Tab label={t('event.tabs.invoices')} {...a11yProps('invoices')} />
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
                                                            regAvailableFrom && regAvailableTo
                                                                ? t(
                                                                      'event.registrationAvailable.timespan',
                                                                  ) +
                                                                  ': ' +
                                                                  regAvailableFrom +
                                                                  ' - ' +
                                                                  regAvailableTo
                                                                : regAvailableFrom
                                                                  ? t(
                                                                        'event.registrationAvailable.timespanFrom',
                                                                    ) + ` ${regAvailableFrom}`
                                                                  : t(
                                                                        'event.registrationAvailable.timespanTo',
                                                                    ) + ` ${regAvailableTo}`
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
                            </Stack>
                        </TabPanel>
                        <TabPanel index={'competitions'} activeTab={activeTab}>
                            <CompetitionsAndEventDays />
                        </TabPanel>
                        <TabPanel index={'registrations'} activeTab={activeTab}>
                            <EventRegistrationTable
                                {...eventRegistrationProps.table}
                                title={t('event.registration.registrations')}
                                eventId={eventId}
                            />
                        </TabPanel>
                        <TabPanel index={'participants'} activeTab={activeTab}>
                            <ParticipantForEventTable
                                {...participantForEventProps.table}
                                title={t('event.participants')}
                            />
                        </TabPanel>
                        <TabPanel index={'organization'} activeTab={activeTab}>
                            <Stack spacing={2}>
                                <Shiftplan />
                                <TaskTable {...taskProps.table} title={t('task.tasks')} />
                                <TaskDialog {...taskProps.dialog} eventId={eventId} />
                            </Stack>
                        </TabPanel>
                        <TabPanel index={'settings'} activeTab={activeTab}>
                            <Stack spacing={2}>
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
                        <TabPanel index={'actions'} activeTab={activeTab}>
                            <Stack spacing={4}>
                                <Button variant={'contained'} onClick={handleReportDownload}>
                                    {t('event.action.registrationsReport.download')}
                                </Button>
                            </Stack>
                        </TabPanel>
                        <InvoicesTabPanel
                            activeTab={activeTab}
                            eventId={eventId}
                            invoicesProducible={
                                !data.invoicesProduced &&
                                !eventRegistrationPossible(
                                    data.registrationAvailableFrom,
                                    data.registrationAvailableTo,
                                )
                            }
                            reloadEvent={reload}
                        />
                    </Stack>
                ) : (
                    pending && <Throbber />
                )}
            </Box>
        </Box>
    )
}

export default EventPage
