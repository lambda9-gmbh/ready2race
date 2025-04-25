import {Box, Button, Stack, Tab, Typography} from '@mui/material'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import {eventIndexRoute, eventRoute} from '@routes'
import {useTranslation} from 'react-i18next'
import CompetitionTable from '@components/event/competition/CompetitionTable.tsx'
import CompetitionDialog from '@components/event/competition/CompetitionDialog.tsx'
import Throbber from '@components/Throbber.tsx'
import EventDayDialog from '@components/event/eventDay/EventDayDialog.tsx'
import EventDayTable from '@components/event/eventDay/EventDayTable.tsx'
import {getEvent} from '@api/sdk.gen.ts'
import {
    CompetitionDto,
    EventDayDto,
    EventDocumentDto,
    ParticipantForEventDto,
    ParticipantRequirementForEventDto,
} from '@api/types.gen.ts'
import DocumentTable from '@components/event/document/DocumentTable.tsx'
import DocumentDialog from '@components/event/document/DocumentDialog.tsx'
import {Forward} from '@mui/icons-material'
import {Link, useNavigate} from '@tanstack/react-router'
import {useMemo} from 'react'
import TabPanel from '@components/tab/TabPanel.tsx'
import ParticipantRequirementForEventTable from '@components/event/participantRequirement/ParticipantRequirementForEventTable.tsx'
import ParticipantForEventTable from '@components/participant/ParticipantForEventTable.tsx'
import {useUser} from '@contexts/user/UserContext.ts'
import {readEventGlobal, updateEventGlobal} from '@authorization/privileges.ts'
import TabSelectionContainer from '@components/tab/TabSelectionContainer.tsx'
import InlineLink from '@components/InlineLink.tsx'

const EventPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()

    const {tabIndex} = eventIndexRoute.useSearch()
    const activeTab = tabIndex ?? 0

    const navigate = useNavigate()
    const switchTab = (tabIndex: number) => {
        navigate({from: eventIndexRoute.fullPath, search: {tabIndex: tabIndex}}).then()
    }

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

    const a11yProps = (index: number) => {
        return {
            id: `event-tab-${index}`,
            'aria-controls': `event-tabpanel-${index}`,
        }
    }

    const canRegister = useMemo(
        () =>
            user.loggedIn &&
            user.clubId != null &&
            data?.registrationAvailableFrom != null &&
            new Date(data?.registrationAvailableFrom) < new Date() &&
            (data?.registrationAvailableTo == null ||
                new Date(data?.registrationAvailableTo) > new Date()),
        [data, user],
    )

    return (
        <Box>
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
                            <Tab label={t('event.tabs.general')} {...a11yProps(0)} />
                            <Tab label={t('event.tabs.participants')} {...a11yProps(1)} />
                            {user.checkPrivilege(readEventGlobal) && (
                                <Tab label={t('event.tabs.settings')} {...a11yProps(2)} />
                            )}
                        </TabSelectionContainer>
                        <TabPanel index={0} activeTab={activeTab}>
                            <Stack spacing={2}>
                                <CompetitionTable
                                    {...competitionAdministrationProps.table}
                                    title={t('event.competition.competitions')}
                                    hints={
                                        user.checkPrivilege(updateEventGlobal)
                                            ? [
                                                  <>
                                                      {t(
                                                          'event.competition.tableHint.templates.part1',
                                                      ) + ' '}
                                                      <InlineLink
                                                          to={'/config'}
                                                          search={{tabIndex: 0}}>
                                                          {t(
                                                              'event.competition.tableHint.templates.part2Link',
                                                          )}
                                                      </InlineLink>
                                                      {' ' +
                                                          t(
                                                              'event.competition.tableHint.templates.part3',
                                                          )}
                                                  </>,
                                                  <>
                                                      {t(
                                                          'event.competition.tableHint.competitionComponents.part1',
                                                      ) + ' '}
                                                      <InlineLink
                                                          to={'/config'}
                                                          search={{tabIndex: 1}}>
                                                          {t(
                                                              'event.competition.tableHint.competitionComponents.part2Link',
                                                          )}
                                                      </InlineLink>
                                                      {' ' +
                                                          t(
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
                        <TabPanel index={1} activeTab={activeTab}>
                            <ParticipantForEventTable
                                {...participantForEventProps.table}
                                title={t('club.participant.title')}
                            />
                        </TabPanel>
                        <TabPanel index={2} activeTab={activeTab}>
                            <Stack spacing={2}>
                                <DocumentTable
                                    {...documentAdministrationProps.table}
                                    title={t('event.document.documents')}
                                    hints={[
                                        <>
                                            {t('event.document.tableHint.part1') + ' '}
                                            <InlineLink to={'/config'} search={{tabIndex: 2}}>
                                                {t('event.document.tableHint.part2Link')}
                                            </InlineLink>
                                            {' ' + t('event.document.tableHint.part3')}
                                        </>,
                                    ]}
                                />
                                <DocumentDialog {...documentAdministrationProps.dialog} />
                                <ParticipantRequirementForEventTable
                                    {...participantRequirementAdministrationProps.table}
                                    title={t('participantRequirement.participantRequirements')}
                                    hints={[
                                        <>
                                            {t('event.participantRequirement.tableHint.part1') +
                                                ' '}
                                            <InlineLink to={'/config'} search={{tabIndex: 2}}>
                                                {t(
                                                    'event.participantRequirement.tableHint.part2Link',
                                                )}
                                            </InlineLink>
                                            {' ' +
                                                t('event.participantRequirement.tableHint.part3')}
                                        </>,
                                    ]}
                                />
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
