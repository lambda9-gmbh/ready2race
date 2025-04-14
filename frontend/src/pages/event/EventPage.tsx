import {Box, Button, Link as MuiLink, Stack, Tab, Tabs, Typography} from '@mui/material'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import {eventRoute} from '@routes'
import {useTranslation} from 'react-i18next'
import CompetitionTable from '@components/event/competition/CompetitionTable.tsx'
import CompetitionDialog from '@components/event/competition/CompetitionDialog.tsx'
import Throbber from '@components/Throbber.tsx'
import EventDayDialog from '@components/event/eventDay/EventDayDialog.tsx'
import EventDayTable from '@components/event/eventDay/EventDayTable.tsx'
import {getEvent, getRegistrationResult} from '@api/sdk.gen.ts'
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
import {Link} from '@tanstack/react-router'
import {useMemo, useRef, useState} from 'react'
import TabPanel from '@components/TabPanel.tsx'
import ParticipantRequirementForEventTable from '@components/event/participantRequirement/ParticipantRequirementForEventTable.tsx'
import ParticipantForEventTable from '@components/participant/ParticipantForEventTable.tsx'
import {useUser} from '@contexts/user/UserContext.ts'
import {readEventGlobal} from '@authorization/privileges.ts'

const EventPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()

    const [activeTab, setActiveTab] = useState(0)

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

    const a11yProps = (index: number) => {
        return {
            id: `event-tab-${index}`,
            'aria-controls': `event-tabpanel-${index}`,
        }
    }

    const canRegister = useMemo(
        () =>
            data?.registrationAvailableFrom != null &&
            new Date(data?.registrationAvailableFrom) < new Date() &&
            (data?.registrationAvailableTo == null ||
                new Date(data?.registrationAvailableTo) > new Date()),

        [data],
    )

    const handleReportDownload = async () => {
        const {data, error} = await getRegistrationResult({
            path: { eventId },
            query: {
                remake: true
            }
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
                        <Box sx={{borderBottom: 1, borderColor: 'divider'}}>
                            <Tabs value={activeTab} onChange={(_, v) => setActiveTab(v)}>
                                <Tab label={t('event.tabs.general')} {...a11yProps(0)} />
                                <Tab label={t('event.tabs.participants')} {...a11yProps(1)} />
                                {user.checkPrivilege(readEventGlobal) && (
                                    <Tab label={t('event.tabs.settings')} {...a11yProps(2)} />
                                )}
                                {user.checkPrivilege(readEventGlobal) && (
                                    <Tab label={'[todo] Actions'} {...a11yProps(3)} />
                                )}

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
                            </Stack>
                        </TabPanel>
                        <TabPanel index={1} activeTab={activeTab}>
                            <ParticipantForEventTable
                                {...participantForEventProps.table}
                                title={t('club.participant.title')}
                            />
                        </TabPanel>
                        <TabPanel index={2} activeTab={activeTab}>
                            <DocumentTable
                                {...documentAdministrationProps.table}
                                title={t('event.document.documents')}
                            />
                            <DocumentDialog {...documentAdministrationProps.dialog} />
                            <ParticipantRequirementForEventTable
                                {...participantRequirementAdministrationProps.table}
                                title={t('participantRequirement.participantRequirements')}
                            />
                        </TabPanel>
                        <TabPanel index={3} activeTab={activeTab}>
                            <Button variant={'contained'} onClick={handleReportDownload}>
                                [todo] Download registrations report
                            </Button>
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
