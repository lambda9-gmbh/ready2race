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

const COMPETITION_TABS = ['general', 'registrations', 'setup'] as const
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

    const {data: eventData} = useFetch(signal => getEvent({signal, path: {eventId: eventId}}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(t('common.load.error.single', {entity: t('event.event')}))
            }
        },
        deps: [eventId],
    })

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
            deps: [eventId, competitionId, reloadDataTrigger],
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

    const a11yProps = (index: CompetitionTab) => {
        return {
            value: index,
            id: `event-tab-${index}`,
            'aria-controls': `event-tabpanel-${index}`,
        }
    }

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
            deps: [eventId, reloadDataTrigger],
        },
    )

    const selection: AutocompleteOption[] =
        eventDaysData?.data.map(value => ({
            id: value.id,
            label: eventDayName(value.date, value.name),
        })) ?? []

    return (
        <Box sx={{display: 'flex', flexDirection: 'column'}}>
            {(competitionData && (
                <Stack spacing={2}>
                    <Typography variant={'h1'}>
                        {competitionData.properties.identifier +
                            ' | ' +
                            competitionData.properties.name}
                    </Typography>
                    <TabSelectionContainer activeTab={activeTab} setActiveTab={switchTab}>
                        <Tab label={t('event.tabs.general')} {...a11yProps('general')} />
                        {user.loggedIn && (
                            <Tab
                                label={t('event.registration.registrations')}
                                {...a11yProps('registrations')}
                            />
                        )}
                        {user.checkPrivilege(updateEventGlobal) && (
                            <Tab
                                label={t('event.competition.setup.setup')}
                                {...a11yProps('setup')}
                            />
                        )}
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
                                        onSuccess={() => setReloadDataTrigger(!reloadDataTrigger)}
                                    />
                                )) ||
                                    ((eventDaysPending || assignedEventDaysPending) && (
                                        <Throbber />
                                    ))}
                            </Card>
                        </Box>
                    </TabPanel>
                    {user.loggedIn && (
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
                </Stack>
            )) ||
                (competitionPending && <Throbber />)}
        </Box>
    )
}

export default CompetitionPage
