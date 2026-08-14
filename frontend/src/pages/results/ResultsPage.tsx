import MatchResults from '@components/results/MatchResults.tsx'
import {Box, Divider, Stack, Tab, useMediaQuery, useTheme} from '@mui/material'
import TabSelectionContainer from '@components/tab/TabSelectionContainer.tsx'
import {a11yProps} from '@utils/helpers.ts'
import {useState} from 'react'
import TabPanel from '@components/tab/TabPanel.tsx'
import CellTowerOutlinedIcon from '@mui/icons-material/CellTowerOutlined'
import CalendarMonthOutlinedIcon from '@mui/icons-material/CalendarMonthOutlined'
import EmojiEventsOutlinedIcon from '@mui/icons-material/EmojiEventsOutlined'
import PersonIcon from '@mui/icons-material/Person'
import PercentIcon from '@mui/icons-material/Percent'
import PersonPinIcon from '@mui/icons-material/PersonPin'
import {resultsEventRoute} from '@routes'
import ResultsLiveMatches from '@components/results/ResultsLiveMatches.tsx'
import {useTranslation} from 'react-i18next'
import {CompetitionChoiceDto} from '@api/types.gen.ts'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getEvent, getPublicProgram} from '@api/sdk.gen.ts'
import ResultsProgram from '@components/results/ResultsProgram.tsx'
import {useNavigate} from '@tanstack/react-router'
import Throbber from '@components/Throbber.tsx'
import ResultsConfigurationTopBar from '@components/results/ResultsConfigurationTopBar.tsx'
import ResultsClubRanking from '@components/results/ResultsClubRanking.tsx'
import ResultsIndividualRanking from '@components/results/ResultsIndividualRanking.tsx'
import {MyEventPanel} from '@components/results/myEvent/MyEventPanel.tsx'
import {initialResultsTab, ResultsTab} from './resultsTab.ts'

const CHALLENGE_RESULTS_TABS = ['club', 'individual', 'relative'] as const
export type ChallengeResultsTab = (typeof CHALLENGE_RESULTS_TABS)[number]

const ResultsPage = () => {
    const theme = useTheme()
    const {t} = useTranslation()
    const feedback = useFeedback()

    const smallScreenLayout = useMediaQuery(`(max-width:${theme.breakpoints.values.sm}px)`)

    const {eventId} = resultsEventRoute.useParams()
    const navigate = useNavigate()

    const [competitionSelected, setCompetitionSelected] = useState<CompetitionChoiceDto | null>(
        null,
    )

    // Der gewählte Wettkampf spiegelt sich in der Adresse (?competition=…), damit ein Link auf
    // einen Wettkampf teilbar ist — dieselbe Idee wie ?tab=… für die QR-Aushänge. replace statt
    // push: der Zurück-Knopf soll die Seite verlassen, nicht durch jede Wettkampfwahl zurücklaufen.
    const selectCompetition = (value: CompetitionChoiceDto | null) => {
        setCompetitionSelected(value)
        void navigate({
            to: '.',
            search: prev => ({...prev, competition: value?.id}),
            replace: true,
        })
    }

    // Direkteinstieg über die URL (siehe resultsTab.ts): Der QR-Einstieg leitet mit
    // ?tab=my-event weiter, QR-Aushänge zeigen mit ?tab=live direkt auf den Live-Reiter, und
    // ?tab=results ist das ausgeschriebene Default-Ziel.
    const {tab: tabFromSearch, competition: competitionFromSearch} =
        resultsEventRoute.useSearch()
    const [activeResultsTab, setActiveResultsTab] = useState<ResultsTab>(
        initialResultsTab(tabFromSearch),
    )
    const switchResultsTab = (tab: ResultsTab) => {
        selectCompetition(null)
        setActiveResultsTab(tab)
    }
    const resultsTabProps = (tab: ResultsTab) => a11yProps('results', tab)

    const [activeChallengeTab, setActiveChallengeTab] = useState<ChallengeResultsTab>('club')
    const switchChallengeTab = (tab: ChallengeResultsTab) => {
        selectCompetition(null)
        setActiveChallengeTab(tab)
    }
    const challengeTabProps = (tab: ChallengeResultsTab) => a11yProps('challengeResults', tab)

    const {data: programData} = useFetch(
        signal => getPublicProgram({signal, path: {eventId}}),
        {deps: [eventId]},
    )
    const hasProgram = (programData?.length ?? 0) > 0
    // Solange das Programm noch lädt (oder es keines gibt), existiert der Zeitplan-Reiter nicht —
    // ein ?tab=schedule-Einstieg fällt dann sauber auf die Ergebnisse zurück, statt die Tabs auf
    // einen nicht gerenderten Wert zu stellen.
    const effectiveResultsTab: ResultsTab =
        activeResultsTab === 'schedule' && !hasProgram ? 'latest-results' : activeResultsTab

    const {data: eventData, pending: eventPending} = useFetch(
        signal =>
            getEvent({
                signal,
                path: {eventId: eventId},
            }),
        {
            onResponse: ({error}) => {
                if (error)
                    feedback.error(
                        t('common.load.error.single', {
                            entity: t('event.event'),
                        }),
                    )
            },
            deps: [eventId],
        },
    )

    return eventPending ? (
        <Stack sx={{display: 'flex', flex: 1, justifyContent: 'center'}}>
            <Throbber />
        </Stack>
    ) : eventData ? (
        <>
            <ResultsConfigurationTopBar
                competitionSelected={competitionSelected !== null}
                resetSelectedCompetition={() => selectCompetition(null)}
                title={eventData.name}
            />
            <Divider />
            <Box sx={{mb: 2}}>
                {!eventData?.challengeEvent ? (
                    <TabSelectionContainer
                        activeTab={effectiveResultsTab}
                        setActiveTab={switchResultsTab}>
                        <Tab
                            label={t('results.tabs.results')}
                            icon={<EmojiEventsOutlinedIcon />}
                            iconPosition={smallScreenLayout ? 'top' : 'start'}
                            sx={{flex: 1, maxWidth: 'unset'}}
                            {...resultsTabProps('latest-results')}
                        />
                        <Tab
                            label={t('results.tabs.live')}
                            icon={<CellTowerOutlinedIcon />}
                            iconPosition={smallScreenLayout ? 'top' : 'start'}
                            sx={{flex: 1, maxWidth: 'unset'}}
                            {...resultsTabProps('live')}
                        />
                        {hasProgram && (
                            <Tab
                                label={t('results.tabs.schedule')}
                                icon={<CalendarMonthOutlinedIcon />}
                                iconPosition={smallScreenLayout ? 'top' : 'start'}
                                sx={{flex: 1, maxWidth: 'unset'}}
                                {...resultsTabProps('schedule')}
                            />
                        )}
                        <Tab
                            label={t('myEvent.tab')}
                            icon={<PersonPinIcon />}
                            iconPosition={smallScreenLayout ? 'top' : 'start'}
                            sx={{flex: 1, maxWidth: 'unset'}}
                            {...resultsTabProps('my-event')}
                        />
                    </TabSelectionContainer>
                ) : (
                    <TabSelectionContainer
                        activeTab={activeChallengeTab}
                        setActiveTab={switchChallengeTab}>
                        <Tab
                            label={t('results.challengeTabs.club')}
                            icon={<EmojiEventsOutlinedIcon />}
                            iconPosition={smallScreenLayout ? 'top' : 'start'}
                            sx={{flex: 1, maxWidth: 'unset'}}
                            {...challengeTabProps('club')}
                        />
                        <Tab
                            label={t('results.challengeTabs.relative')}
                            icon={<PercentIcon />}
                            iconPosition={smallScreenLayout ? 'top' : 'start'}
                            sx={{flex: 1, maxWidth: 'unset'}}
                            {...challengeTabProps('relative')}
                        />
                        <Tab
                            label={t('results.challengeTabs.individual')}
                            icon={<PersonIcon />}
                            iconPosition={smallScreenLayout ? 'top' : 'start'}
                            sx={{flex: 1, maxWidth: 'unset'}}
                            {...challengeTabProps('individual')}
                        />
                    </TabSelectionContainer>
                )}
            </Box>
            {!eventData.challengeEvent ? (
                <>
                    <TabPanel index={'latest-results'} activeTab={effectiveResultsTab}>
                        <MatchResults
                            eventId={eventId}
                            competitionSelected={competitionSelected}
                            setCompetitionSelected={selectCompetition}
                            initialCompetitionId={competitionFromSearch}
                            notice={eventData.notice}
                        />
                    </TabPanel>
                    <TabPanel index={'live'} activeTab={effectiveResultsTab}>
                        <ResultsLiveMatches eventId={eventId} />
                    </TabPanel>
                    {hasProgram && (
                        <TabPanel index={'schedule'} activeTab={effectiveResultsTab}>
                            <ResultsProgram eventId={eventId} />
                        </TabPanel>
                    )}
                    <TabPanel index={'my-event'} activeTab={effectiveResultsTab}>
                        <MyEventPanel eventId={eventId} />
                    </TabPanel>
                </>
            ) : (
                <>
                    <TabPanel index={'club'} activeTab={activeChallengeTab}>
                        <ResultsClubRanking eventData={eventData} totalRanking={true} />
                    </TabPanel>
                    <TabPanel index={'relative'} activeTab={activeChallengeTab}>
                        <ResultsClubRanking eventData={eventData} totalRanking={false} />
                    </TabPanel>
                    <TabPanel index={'individual'} activeTab={activeChallengeTab}>
                        <ResultsIndividualRanking eventData={eventData} />
                    </TabPanel>
                </>
            )}
        </>
    ) : (
        <></>
    )
}
export default ResultsPage
