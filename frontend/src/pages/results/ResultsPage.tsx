import MatchResults from '@components/results/MatchResults.tsx'
import {Box, Divider, Stack, Tab, useMediaQuery, useTheme} from '@mui/material'
import TabSelectionContainer from '@components/tab/TabSelectionContainer.tsx'
import {a11yProps} from '@utils/helpers.ts'
import {useState} from 'react'
import TabPanel from '@components/tab/TabPanel.tsx'
import CellTowerOutlinedIcon from '@mui/icons-material/CellTowerOutlined'
import EmojiEventsOutlinedIcon from '@mui/icons-material/EmojiEventsOutlined'
import PersonIcon from '@mui/icons-material/Person'
import PercentIcon from '@mui/icons-material/Percent'
import {resultsEventRoute} from '@routes'
import ResultsLiveMatches from '@components/results/ResultsLiveMatches.tsx'
import {useTranslation} from 'react-i18next'
import {CompetitionChoiceDto} from '@api/types.gen.ts'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getEvent} from '@api/sdk.gen.ts'
import Throbber from '@components/Throbber.tsx'
import ResultsConfigurationTopBar from '@components/results/ResultsConfigurationTopBar.tsx'
import ResultsClubRanking from '@components/results/ResultsClubRanking.tsx'
import ResultsIndividualRanking from '@components/results/ResultsIndividualRanking.tsx'

const RESULTS_TABS = ['latest-results', 'live', 'upcoming'] as const
export type ResultsTab = (typeof RESULTS_TABS)[number]

const CHALLENGE_RESULTS_TABS = ['club', 'individual', 'relative'] as const
export type ChallengeResultsTab = (typeof CHALLENGE_RESULTS_TABS)[number]

const ResultsPage = () => {
    const theme = useTheme()
    const {t} = useTranslation()
    const feedback = useFeedback()

    const smallScreenLayout = useMediaQuery(`(max-width:${theme.breakpoints.values.sm}px)`)

    const {eventId} = resultsEventRoute.useParams()

    const [competitionSelected, setCompetitionSelected] = useState<CompetitionChoiceDto | null>(
        null,
    )

    const [activeResultsTab, setActiveResultsTab] = useState<ResultsTab>('latest-results')
    const switchResultsTab = (tab: ResultsTab) => {
        setCompetitionSelected(null)
        setActiveResultsTab(tab)
    }
    const resultsTabProps = (tab: ResultsTab) => a11yProps('results', tab)

    const [activeChallengeTab, setActiveChallengeTab] = useState<ChallengeResultsTab>('club')
    const switchChallengeTab = (tab: ChallengeResultsTab) => {
        setCompetitionSelected(null)
        setActiveChallengeTab(tab)
    }
    const challengeTabProps = (tab: ChallengeResultsTab) => a11yProps('challengeResults', tab)

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
                showBackButton={true}
                competitionSelected={competitionSelected !== null}
                resetSelectedCompetition={() => setCompetitionSelected(null)}
                title={eventData.name}
            />
            <Divider />
            <Box sx={{mb: 2}}>
                {!eventData?.challengeEvent ? (
                    <TabSelectionContainer
                        activeTab={activeResultsTab}
                        setActiveTab={switchResultsTab}>
                        <Tab
                            label={t('results.tabs.results')}
                            icon={<EmojiEventsOutlinedIcon />}
                            iconPosition={smallScreenLayout ? 'top' : 'start'}
                            sx={{flex: 1}}
                            {...resultsTabProps('latest-results')}
                        />
                        <Tab
                            label={t('results.tabs.live')}
                            icon={<CellTowerOutlinedIcon />}
                            iconPosition={smallScreenLayout ? 'top' : 'start'}
                            sx={{flex: 1}}
                            {...resultsTabProps('live')}
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
                    <TabPanel index={'latest-results'} activeTab={activeResultsTab}>
                        <MatchResults
                            eventId={eventId}
                            competitionSelected={competitionSelected}
                            setCompetitionSelected={setCompetitionSelected}
                        />
                    </TabPanel>
                    <TabPanel index={'live'} activeTab={activeResultsTab}>
                        <ResultsLiveMatches eventId={eventId} />
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
