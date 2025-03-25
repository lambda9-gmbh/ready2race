import {Box, Button, Stack, Tab, Tabs, Typography} from '@mui/material'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import {eventRoute} from '@routes'
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
    ParticipantRequirementCheckForEventConfigDto,
    ParticipantRequirementForEventDto,
} from '@api/types.gen.ts'
import DocumentTable from '@components/event/document/DocumentTable.tsx'
import DocumentDialog from '@components/event/document/DocumentDialog.tsx'
import {Forward} from '@mui/icons-material'
import {Link} from '@tanstack/react-router'
import {useState} from 'react'
import TabPanel from '@components/TabPanel.tsx'
import ParticipantRequirementForEventTable from '@components/event/participantRequirement/ParticipantRequirementForEventTable.tsx'
import ParticipantForEventTable from '@components/participant/ParticipantForEventTable.tsx'
import ParticipantRequirementCheckForEventDialog from '@components/event/participantRequirement/ParticipantRequirementCheckForEventDialog.tsx'

const EventPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [activeTab, setActiveTab] = useState(1)

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
            t('event.participantRequirement'),
            {entityCreate: false, entityUpdate: false},
        )
    const participantForEventProps = useEntityAdministration<ParticipantForEventDto>(
        t('club.participant.title'),
        {entityCreate: false, entityUpdate: false},
    )

    const participantRequirementCheckForEventProps =
        useEntityAdministration<ParticipantRequirementCheckForEventConfigDto>(
            t('club.participant.title'),
            {entityCreate: false, entityUpdate: false},
        )

    const a11yProps = (index: number) => {
        return {
            id: `event-tab-${index}`,
            'aria-controls': `event-tabpanel-${index}`,
        }
    }

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
                            <Link to={'/event/$eventId/register'} params={{eventId}}>
                                <Button endIcon={<Forward />} variant={'contained'}>
                                    {t('event.registerNow')}
                                </Button>
                            </Link>
                        </Stack>
                        <Box sx={{borderBottom: 1, borderColor: 'divider'}}>
                            <Tabs value={activeTab} onChange={(_, v) => setActiveTab(v)}>
                                <Tab label="Allgemein" {...a11yProps(0)} />
                                <Tab label="Teilnehmer" {...a11yProps(1)} />
                                <Tab label="Einstellungen" {...a11yProps(2)} />
                            </Tabs>
                        </Box>
                        <TabPanel index={0} activeTab={activeTab}>
                            <Stack spacing={2}>
                                <CompetitionTable
                                    {...competitionAdministrationProps.table}
                                    title={t('event.competition.competitions')}
                                />
                                <CompetitionDialog {...competitionAdministrationProps.dialog} />
                                <EventDayTable
                                    {...eventDayAdministrationProps.table}
                                    title={t('event.eventDay.eventDays')}
                                />
                                <EventDayDialog {...eventDayAdministrationProps.dialog} />
                                <DocumentTable
                                    {...documentAdministrationProps.table}
                                    title={t('event.document.documents')}
                                />
                                <DocumentDialog {...documentAdministrationProps.dialog} />
                            </Stack>
                        </TabPanel>
                        <TabPanel index={1} activeTab={activeTab}>
                            <ParticipantRequirementCheckForEventDialog
                                {...participantRequirementCheckForEventProps.dialog}
                            />
                            <ParticipantForEventTable
                                {...participantForEventProps.table}
                                openRequirementsCheck={() =>
                                    participantRequirementCheckForEventProps.table.openDialog(
                                        undefined,
                                    )
                                }
                                title={t('club.participant.title')}
                            />
                        </TabPanel>
                        <TabPanel index={2} activeTab={activeTab}>
                            <ParticipantRequirementForEventTable
                                {...participantRequirementAdministrationProps.table}
                                title={t('event.participantRequirements')}
                            />
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
