import {Box, Button, Divider, Stack, Tab, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import {competitionIndexRoute, competitionRoute, eventRoute} from '@routes'
import {eventDayName} from '@components/event/common.ts'
import {AutocompleteOption} from '@utils/types.ts'
import Throbber from '@components/Throbber.tsx'
import CompetitionAndDayAssignment from '@components/event/competitionAndDayAssignment/CompetitionAndDayAssignment.tsx'
import {useState} from 'react'
import EntityDetailsEntry from '@components/EntityDetailsEntry.tsx'
import {getCompetition, getEventDays} from '@api/sdk.gen.ts'
import CompetitionCountEntry from '@components/event/competition/CompetitionCountEntry.tsx'
import TabPanel from '@components/tab/TabPanel.tsx'
import {CompetitionRegistrationTeamDto} from '@api/types.gen.ts'
import CompetitionRegistrationTable from '@components/event/competition/registration/CompetitionRegistrationTable.tsx'
import CompetitionRegistrationDialog from '@components/event/competition/registration/CompetitionRegistrationDialog.tsx'
import TabSelectionContainer from '@components/tab/TabSelectionContainer'
import {useNavigate, Link} from '@tanstack/react-router'
import {useUser} from '@contexts/user/UserContext.ts'

const CompetitionPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const {tabIndex} = competitionIndexRoute.useSearch()
    const activeTab = tabIndex ?? 0

    const navigate = useNavigate()
    const switchTab = (tabIndex: number) => {
        navigate({from: competitionIndexRoute.fullPath, search: {tabIndex: tabIndex}}).then()
    }

    const [reloadDataTrigger, setReloadDataTrigger] = useState(false)

    const {data: competitionData, pending: competitionPending} = useFetch(
        signal => getCompetition({signal, path: {eventId: eventId, competitionId: competitionId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.single', {entity: t('event.competition.competition')}),
                    )
                }
            },
            deps: [eventId, competitionId],
        },
    )

    const {data: assignedEventDaysData, pending: assignedEventDaysPending} = useFetch(
        signal =>
            getEventDays({signal, path: {eventId: eventId}, query: {competitionId: competitionId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.eventDay.eventDays'),
                        }),
                    )
                }
            },
            deps: [eventId, competitionId, reloadDataTrigger],
        },
    )

    const competitionRegistrationTeamsProps =
        useEntityAdministration<CompetitionRegistrationTeamDto>(
            t('event.registration.registration'),
        )

    const a11yProps = (index: number) => {
        return {
            id: `event-tab-${index}`,
            'aria-controls': `event-tabpanel-${index}`,
        }
    }

    const assignedEventDays =
        assignedEventDaysData?.data.map(value => ({
            id: value.id,
            label: eventDayName(value.date, value.name),
        })) ?? []

    const {data: eventDaysData, pending: eventDaysPending} = useFetch(
        signal => getEventDays({signal, path: {eventId: eventId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.eventDay.eventDays'),
                        }),
                    )
                }
            },
            deps: [eventId, reloadDataTrigger],
        },
    )

    const selection: AutocompleteOption[] =
        eventDaysData?.data.map(value => ({
            id: value.id,
            label: eventDayName(value.date, value.name),
        })) ?? []

    return (
        <Box>
            <Box sx={{display: 'flex', flexDirection: 'column'}}>
                {(competitionData && (
                    <Stack spacing={2}>
                        <EntityDetailsEntry
                            content={
                                competitionData.properties.identifier +
                                ' | ' +
                                competitionData.properties.name
                            }
                            variant="h1"
                        />
                        <TabSelectionContainer activeTab={activeTab} setActiveTab={switchTab}>
                            <Tab label={t('event.tabs.settings')} {...a11yProps(0)} />
                            {user.loggedIn && (
                                <Tab
                                    label={t('event.registration.registrations')}
                                    {...a11yProps(1)}
                                />
                            )}
                        </TabSelectionContainer>
                        <TabPanel index={0} activeTab={activeTab}>
                            <Stack direction={'row'} spacing={2}>
                                <Stack flex={1} spacing={2}>
                                    <EntityDetailsEntry
                                        content={competitionData.properties.shortName}
                                    />
                                    <EntityDetailsEntry
                                        content={competitionData.properties.description}
                                    />
                                    {competitionData.properties.competitionCategory && (
                                        <Box sx={{py: 2}}>
                                            <EntityDetailsEntry
                                                content={
                                                    competitionData.properties.competitionCategory
                                                        .name
                                                }
                                            />
                                            <EntityDetailsEntry
                                                content={
                                                    competitionData.properties.competitionCategory
                                                        .description
                                                }
                                            />
                                        </Box>
                                    )}

                                    <Divider />

                                    {competitionData.properties.namedParticipants.map(
                                        (np, index) => (
                                            <Box key={`box${index}`}>
                                                {/*todo: should have np.id instead of index to prevent updating errors*/}
                                                <Typography variant="subtitle1">
                                                    {np.name}
                                                </Typography>
                                                <Typography>{np.description}</Typography>
                                                <CompetitionCountEntry
                                                    label={t('event.competition.count.males')}
                                                    content={np.countMales}
                                                />
                                                <CompetitionCountEntry
                                                    label={t('event.competition.count.females')}
                                                    content={np.countFemales}
                                                />
                                                <CompetitionCountEntry
                                                    label={t('event.competition.count.nonBinary')}
                                                    content={np.countNonBinary}
                                                />
                                                <CompetitionCountEntry
                                                    label={t('event.competition.count.mixed')}
                                                    content={np.countMixed}
                                                />
                                            </Box>
                                        ),
                                    )}
                                    <Divider />
                                    {competitionData.properties.fees.map((f, index) => (
                                        <Box key={`box${index}`}>
                                            {/*todo: should have fee.id instead of index to prevent updating errors*/}
                                            <Typography variant="subtitle1">{f.name}</Typography>
                                            <Typography>{f.description}</Typography>
                                            <Typography>
                                                {f.required
                                                    ? t('event.competition.fee.required.required')
                                                    : t(
                                                          'event.competition.fee.required.notRequired',
                                                      )}
                                            </Typography>
                                            <CompetitionCountEntry
                                                label={t('event.competition.fee.amount')}
                                                content={f.amount + '€'}
                                            />
                                        </Box>
                                    ))}
                                </Stack>
                                <Stack direction={'column'} spacing={4}>
                                    <Box sx={{flex: 1, maxWidth: 400}}>
                                        {(eventDaysData && assignedEventDaysData && (
                                            <CompetitionAndDayAssignment
                                                entityPathId={competitionId}
                                                options={selection}
                                                assignedEntities={assignedEventDays}
                                                assignEntityLabel={t('event.eventDay.eventDay')}
                                                competitionsToDay={false}
                                                onSuccess={() =>
                                                    setReloadDataTrigger(!reloadDataTrigger)
                                                }
                                            />
                                        )) ||
                                            ((eventDaysPending || assignedEventDaysPending) && (
                                                <Throbber />
                                            ))}
                                    </Box>
                                    <Box sx={{display: 'flex', justifyContent: 'center'}}>
                                        <Link
                                            to="/event/$eventId/competition/$competitionId/competitionSetup"
                                            params={{
                                                eventId: eventId,
                                                competitionId: competitionId,
                                            }}>
                                            <Button variant="contained">
                                                {t('event.competition.setup.setup')}
                                            </Button>
                                        </Link>
                                    </Box>
                                </Stack>
                            </Stack>
                        </TabPanel>
                        {user.loggedIn && (
                            <TabPanel index={1} activeTab={activeTab}>
                                <CompetitionRegistrationDialog
                                    {...competitionRegistrationTeamsProps.dialog}
                                    competition={competitionData}
                                    eventId={eventId}
                                />
                                <CompetitionRegistrationTable
                                    {...competitionRegistrationTeamsProps.table}
                                />
                            </TabPanel>
                        )}
                    </Stack>
                )) ||
                    (competitionPending && <Throbber />)}
            </Box>
        </Box>
    )
}

export default CompetitionPage
