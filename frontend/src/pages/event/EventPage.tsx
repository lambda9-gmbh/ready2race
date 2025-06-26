import {Box, Button, Card, Link as MuiLink, Stack, Tab, Typography} from '@mui/material'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import {eventIndexRoute, eventRoute} from '@routes'
import {Trans, useTranslation} from 'react-i18next'
import CompetitionTable from '@components/event/competition/CompetitionTable.tsx'
import CompetitionDialog from '@components/event/competition/CompetitionDialog.tsx'
import Throbber from '@components/Throbber.tsx'
import EventDayDialog from '@components/event/eventDay/EventDayDialog.tsx'
import EventDayTable from '@components/event/eventDay/EventDayTable.tsx'
import {
    getEvent,
    getRegistrationResult,
    produceInvoicesForEventRegistrations,
} from '@api/sdk.gen.ts'
import {
    CompetitionDto,
    EventDayDto,
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
import {useMemo, useRef} from 'react'
import TabPanel from '@components/tab/TabPanel.tsx'
import ParticipantRequirementForEventTable from '@components/event/participantRequirement/ParticipantRequirementForEventTable.tsx'
import ParticipantForEventTable from '@components/participant/ParticipantForEventTable.tsx'
import {useUser} from '@contexts/user/UserContext.ts'
import {readEventGlobal, readEventOwn, updateEventGlobal} from '@authorization/privileges.ts'
import TabSelectionContainer from '@components/tab/TabSelectionContainer.tsx'
import InlineLink from '@components/InlineLink.tsx'
import TaskTable from '@components/event/task/TaskTable.tsx'
import TaskDialog from '@components/event/task/TaskDialog.tsx'
import {Shiftplan} from '@components/event/shiftplan/Shiftplan.tsx'
import {eventRegistrationPossible} from '@utils/helpers.ts'
import EventRegistrationTable from '@components/eventRegistration/EventRegistrationTable.tsx'
import PlaceIcon from '@mui/icons-material/Place'

const EVENT_TABS = [
    'general',
    'participants',
    'registrations',
    'organization',
    'settings',
    'actions',
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

    const {data, pending} = useFetch(signal => getEvent({signal, path: {eventId: eventId}}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(t('common.load.error.single', {entity: t('event.event')}))
            }
        },
        deps: [eventId],
    })

    const competitionAdministrationProps = useEntityAdministration<CompetitionDto>(
        t('event.competition.competition'),
    )
    const eventDayAdministrationProps = useEntityAdministration<EventDayDto>(
        t('event.eventDay.eventDay'),
    )
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

    const handleReportDownload = async () => {
        const {data, error} = await getRegistrationResult({
            path: {eventId},
            query: {
                remake: true,
            },
        })
        const anchor = downloadRef.current

        if (error) {
            feedback.error(t('event.document.download.error'))
        } else if (data !== undefined && anchor) {
            anchor.href = URL.createObjectURL(data)
            anchor.download = 'registration-result.pdf' // TODO: read from content-disposition header
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    const handleProduceInvoices = async () => {
        const {data, error} = await produceInvoicesForEventRegistrations({
            path: {eventId},
        })

        if (error !== undefined) {
            feedback.error('[todo] could not produce invoices, cause: ...')
        } else if (data !== undefined) {
            feedback.success('[todo] invoice producing jobs created')
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
                            <Tab label={t('event.tabs.general')} {...a11yProps('general')} />
                            {(user.checkPrivilege(readEventGlobal) ||
                                user.checkPrivilege(readEventOwn)) && (
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
                            {user.checkPrivilege(readEventGlobal) && (
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
                                    {data.location && (
                                        <Stack direction={'row'} spacing={1}>
                                            <PlaceIcon />
                                            <Typography>{data.location}</Typography>
                                        </Stack>
                                    )}
                                    {data.description && (
                                        <Typography>{data.description}</Typography>
                                    )}
                                    {(data.registrationAvailableFrom ||
                                        data.registrationAvailableTo) && (
                                        <Typography>
                                            {data.registrationAvailableFrom &&
                                            data.registrationAvailableTo
                                                ? t('event.registrationAvailable.timespan') +
                                                  ': ' +
                                                  data.registrationAvailableFrom +
                                                  ' - ' +
                                                  data.registrationAvailableTo
                                                : data.registrationAvailableFrom
                                                  ? t('event.registrationAvailable.timespanFrom') +
                                                    ` ${data.registrationAvailableFrom}`
                                                  : t('event.registrationAvailable.timespanTo') +
                                                    ` ${data.registrationAvailableTo}`}
                                            {/*todo format dates*/}
                                        </Typography>
                                    )}
                                </Card>
                                <CompetitionTable
                                    {...competitionAdministrationProps.table}
                                    title={t('event.competition.competitions')}
                                    hints={
                                        user.checkPrivilege(updateEventGlobal)
                                            ? [
                                                  <>
                                                      {t(
                                                          'event.competition.tableHint.templates.part1',
                                                      )}
                                                      <InlineLink
                                                          to={'/config'}
                                                          search={{tab: 'competition-templates'}}>
                                                          {t(
                                                              'event.competition.tableHint.templates.part2Link',
                                                          )}
                                                      </InlineLink>
                                                      {t(
                                                          'event.competition.tableHint.templates.part3',
                                                      )}
                                                  </>,
                                                  <>
                                                      {t(
                                                          'event.competition.tableHint.competitionComponents.part1',
                                                      )}
                                                      <InlineLink
                                                          to={'/config'}
                                                          search={{tab: 'competition-elements'}}>
                                                          {t(
                                                              'event.competition.tableHint.competitionComponents.part2Link',
                                                          )}
                                                      </InlineLink>
                                                      {t(
                                                          'event.competition.tableHint.competitionComponents.part3',
                                                      )}
                                                  </>,
                                              ]
                                            : undefined
                                    }
                                />
                                <CompetitionDialog {...competitionAdministrationProps.dialog} />
                                <EventDayTable
                                    {...eventDayAdministrationProps.table}
                                    title={t('event.eventDay.eventDays')}
                                />
                                <EventDayDialog {...eventDayAdministrationProps.dialog} />
                            </Stack>
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
                                <Button variant={'contained'} onClick={handleProduceInvoices}>
                                    <Trans i18nKey={'event.action.produceInvoices'} />
                                </Button>
                            </Stack>
                        </TabPanel>
                    </Stack>
                ) : (
                    pending && <Throbber />
                )}
            </Box>
        </Box>
    )
}

export default EventPage
