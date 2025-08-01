import {Box, Card, List, ListItem, Stack, Tab, Typography, useTheme} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {competitionIndexRoute, competitionRoute, eventRoute} from '@routes'
import {eventDayName} from '@components/event/common.ts'
import {AutocompleteOption} from '@utils/types.ts'
import Throbber from '@components/Throbber.tsx'
import CompetitionAndDayAssignment from '@components/event/CompetitionAndDayAssignment.tsx'
import {Fragment, useState} from 'react'
import {getCompetition, getEvent, getEventDays} from '@api/sdk.gen.ts'
import TabPanel from '@components/tab/TabPanel.tsx'
import TabSelectionContainer from '@components/tab/TabSelectionContainer'
import {useNavigate} from '@tanstack/react-router'
import {useUser} from '@contexts/user/UserContext.ts'
import {updateEventGlobal} from '@authorization/privileges.ts'
import CompetitionSetupForEvent from '@components/event/competition/setup/CompetitionSetupForEvent.tsx'
import {Info} from '@mui/icons-material'
import {HtmlTooltip} from '@components/HtmlTooltip.tsx'
import CompetitionTeamCompositionEntry from '@components/event/competition/CompetitionTeamCompositionEntry.tsx'
import CompetitionRegistrations from '@components/event/competition/registration/CompetitionRegistrations.tsx'
import {a11yProps, eventRegistrationPossible} from '@utils/helpers.ts'
import CompetitionExecution from '@components/event/competition/excecution/CompetitionExecution.tsx'
import CompetitionPlaces from '@components/event/competition/excecution/CompetitionPlaces.tsx'

const COMPETITION_TABS = ['general', 'registrations', 'setup', 'execution', 'places'] as const
export type CompetitionTab = (typeof COMPETITION_TABS)[number]

const CompetitionPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const user = useUser()
    const theme = useTheme()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const {tab} = competitionIndexRoute.useSearch()
    const activeTab: CompetitionTab = tab ?? 'general'

    const navigate = useNavigate()
    const switchTab = (tab: CompetitionTab) => {
        navigate({from: competitionIndexRoute.fullPath, search: {tab}}).then()
    }

    const {data: eventData, pending: eventPending} = useFetch(
        signal => getEvent({signal, path: {eventId: eventId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('common.load.error.single', {entity: t('event.event')}))
                }
            },
            deps: [eventId],
        },
    )

    const [reloadData, setReloadData] = useState(false)

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
            deps: [eventId, competitionId, reloadData],
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
            deps: [eventId, competitionId, reloadData],
        },
    )

    const tabProps = (tab: CompetitionTab) =>
        a11yProps('competition', tab)

    const assignedEventDays = assignedEventDaysData?.data.map(value => value.id) ?? []

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
            deps: [eventId, reloadData],
        },
    )

    const selection: AutocompleteOption[] =
        eventDaysData?.data.map(value => ({
            id: value.id,
            label: eventDayName(value.date, value.name),
        })) ?? []

    const showRegistrationsTab =
        (eventData?.registrationCount ?? 0) > 0 ||
        (user.loggedIn && !user.clubId) ||
        eventRegistrationPossible(
            eventData?.registrationAvailableFrom,
            eventData?.registrationAvailableTo,
        )

    return (
        <Box sx={{display: 'flex', flexDirection: 'column'}}>
            {(competitionData && eventData && (
                <Stack spacing={2}>
                    <Typography variant={'h1'}>
                        {competitionData.properties.identifier +
                            ' | ' +
                            competitionData.properties.name}
                    </Typography>
                    <TabSelectionContainer activeTab={activeTab} setActiveTab={switchTab}>
                        <Tab label={t('event.tabs.general')} {...tabProps('general')} />
                        {user.loggedIn && showRegistrationsTab && (
                            <Tab
                                label={t('event.registration.registrations')}
                                {...tabProps('registrations')}
                            />
                        )}
                        {user.checkPrivilege(updateEventGlobal) && (
                            <Tab
                                label={t('event.competition.setup.setup')}
                                {...tabProps('setup')}
                            />
                        )}
                        {user.checkPrivilege(updateEventGlobal) && (
                            <Tab
                                label={t('event.competition.execution.tabTitle')}
                                {...tabProps('execution')}
                            />
                        )}
                        <Tab
                            label={t('event.competition.places.tabTitle')}
                            {...tabProps('places')}
                        />
                    </TabSelectionContainer>
                    <TabPanel index={'general'} activeTab={activeTab}>
                        {(competitionData.properties.description ||
                            competitionData.properties.competitionCategory) && (
                            <Card
                                sx={{
                                    p: 2,
                                    mb: 2,
                                    display: 'flex',
                                    flexDirection: 'column',
                                    gap: 2,
                                }}>
                                {competitionData.properties.competitionCategory && (
                                    <Stack
                                        spacing={1}
                                        direction={'row'}
                                        sx={{alignItems: 'center'}}>
                                        <Typography>
                                            {t('event.competition.category.category')}:{' '}
                                            {competitionData.properties.competitionCategory.name}
                                        </Typography>
                                        {competitionData.properties.competitionCategory
                                            .description && (
                                            <HtmlTooltip
                                                title={
                                                    <Typography>
                                                        {
                                                            competitionData.properties
                                                                .competitionCategory.description
                                                        }
                                                    </Typography>
                                                }>
                                                <Info color={'info'} fontSize={'small'} />
                                            </HtmlTooltip>
                                        )}
                                    </Stack>
                                )}
                                {competitionData.properties.description && (
                                    <Typography>
                                        {competitionData.properties.description}
                                    </Typography>
                                )}
                            </Card>
                        )}
                        <Box
                            sx={{
                                display: 'flex',
                                flexWrap: 'wrap',
                                gap: 2,
                                [theme.breakpoints.down('md')]: {flexDirection: 'column'},
                            }}>
                            <Card sx={{p: 2, flex: 1}}>
                                <Typography sx={{mb: 1}} variant="h6">
                                    {t('event.competition.teamComposition')}
                                </Typography>
                                <List>
                                    {competitionData.properties.namedParticipants.map(np => (
                                        <Fragment key={np.id}>
                                            <CompetitionTeamCompositionEntry
                                                np={np}
                                                gender={'male'}
                                            />
                                            <CompetitionTeamCompositionEntry
                                                np={np}
                                                gender={'female'}
                                            />
                                            <CompetitionTeamCompositionEntry
                                                np={np}
                                                gender={'nonBinary'}
                                            />
                                            <CompetitionTeamCompositionEntry
                                                np={np}
                                                gender={'mixed'}
                                            />
                                        </Fragment>
                                    ))}
                                </List>
                            </Card>
                            {competitionData.properties.fees.length > 0 && (
                                <Card sx={{p: 2, flex: 1}}>
                                    <Typography sx={{mb: 1}} variant="h6">
                                        {t('event.competition.fee.fees')}
                                    </Typography>
                                    <List>
                                        {competitionData.properties.fees.map((f, idx) => (
                                            <ListItem key={f.id + idx}>
                                                <Box>
                                                    <Stack
                                                        direction={'row'}
                                                        spacing={1}
                                                        sx={{alignItems: 'center'}}>
                                                        <Typography fontWeight={'bold'}>
                                                            {f.name}
                                                        </Typography>
                                                        {f.description && (
                                                            <HtmlTooltip
                                                                title={
                                                                    <Typography>
                                                                        {f.description}
                                                                    </Typography>
                                                                }>
                                                                <Info
                                                                    color={'info'}
                                                                    fontSize={'small'}
                                                                />
                                                            </HtmlTooltip>
                                                        )}
                                                    </Stack>
                                                    <Typography>{f.amount}€</Typography>
                                                    {!f.required && (
                                                        <Typography>
                                                            {t('event.registration.optionalFee')}
                                                        </Typography>
                                                    )}
                                                </Box>
                                            </ListItem>
                                        ))}
                                    </List>
                                </Card>
                            )}
                            <Card sx={{p: 2, flex: 1}}>
                                {(eventDaysData && assignedEventDaysData && (
                                    <CompetitionAndDayAssignment
                                        entityPathId={competitionId}
                                        options={selection}
                                        assignedEntities={assignedEventDays}
                                        assignEntityLabel={t('event.eventDay.eventDay')}
                                        competitionsToDay={false}
                                        reloadData={() => setReloadData(!reloadData)}
                                    />
                                )) ||
                                    ((eventDaysPending || assignedEventDaysPending) && (
                                        <Throbber />
                                    ))}
                            </Card>
                        </Box>
                    </TabPanel>
                    {user.loggedIn && showRegistrationsTab && (
                        <TabPanel index={'registrations'} activeTab={activeTab}>
                            <CompetitionRegistrations
                                eventData={eventData}
                                competitionData={competitionData}
                            />
                        </TabPanel>
                    )}
                    {user.checkPrivilege(updateEventGlobal) && (
                        <TabPanel index={'setup'} activeTab={activeTab}>
                            <CompetitionSetupForEvent />
                        </TabPanel>
                    )}
                    {user.checkPrivilege(updateEventGlobal) && (
                        <TabPanel index={'execution'} activeTab={activeTab}>
                            <CompetitionExecution />
                        </TabPanel>
                    )}
                    <TabPanel index={'places'} activeTab={activeTab}>
                        <CompetitionPlaces />
                    </TabPanel>
                </Stack>
            )) ||
                (competitionPending && eventPending && <Throbber />)}
        </Box>
    )
}

export default CompetitionPage
