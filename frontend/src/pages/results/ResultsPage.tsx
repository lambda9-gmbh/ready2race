import MatchResults from '@components/results/MatchResults.tsx'
import {Box, IconButton, Stack, Tab, useMediaQuery, useTheme} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import TabSelectionContainer from '@components/tab/TabSelectionContainer.tsx'
import {a11yProps} from '@utils/helpers.ts'
import {useState} from 'react'
import TabPanel from '@components/tab/TabPanel.tsx'
import CellTowerOutlinedIcon from '@mui/icons-material/CellTowerOutlined'
import EmojiEventsOutlinedIcon from '@mui/icons-material/EmojiEventsOutlined'
import {resultsEventRoute} from '@routes'
import {Link} from '@tanstack/react-router'
import ResultsLiveMatches from '@components/results/ResultsLiveMatches.tsx'
import {useTranslation} from 'react-i18next'
import {CompetitionChoiceDto} from '@api/types.gen.ts'

const RESULTS_TABS = ['latest-results', 'live', 'upcoming'] as const
export type ResultsTab = (typeof RESULTS_TABS)[number]

const ResultsPage = () => {
    const theme = useTheme()
    const {t} = useTranslation()

    const smallScreenLayout = useMediaQuery(`(max-width:${theme.breakpoints.values.sm}px)`)

    const {eventId} = resultsEventRoute.useParams()

    const [competitionSelected, setCompetitionSelected] = useState<CompetitionChoiceDto | null>(
        null,
    )

    const [activeTab, setActiveTab] = useState<ResultsTab>('latest-results')
    const switchTab = (tab: ResultsTab) => {
        setCompetitionSelected(null)
        setActiveTab(tab)
    }
    const tabProps = (tab: ResultsTab) => a11yProps('results', tab)

    return (
        <Box sx={{maxWidth: theme.breakpoints.values.sm}}>
            <Stack direction={'row'} sx={{alignItems: 'center'}}>
                <Box>
                    {competitionSelected ? (
                        <IconButton onClick={() => setCompetitionSelected(null)}>
                            <ArrowBackIcon />
                        </IconButton>
                    ) : (
                        <Link to={'/results'}>
                            <IconButton>
                                <ArrowBackIcon />
                            </IconButton>
                        </Link>
                    )}
                </Box>
                <Box sx={{flex: 1}}>
                    <TabSelectionContainer activeTab={activeTab} setActiveTab={switchTab}>
                        <Tab
                            label={t('results.tabs.results')}
                            icon={<EmojiEventsOutlinedIcon />}
                            iconPosition={smallScreenLayout ? 'top' : 'start'}
                            sx={{flex: 1}}
                            {...tabProps('latest-results')}
                        />
                        <Tab
                            label={t('results.tabs.live')}
                            icon={<CellTowerOutlinedIcon />}
                            iconPosition={smallScreenLayout ? 'top' : 'start'}
                            sx={{flex: 1}}
                            {...tabProps('live')}
                        />
                    </TabSelectionContainer>
                </Box>
            </Stack>
            <TabPanel index={'latest-results'} activeTab={activeTab}>
                <MatchResults
                    eventId={eventId}
                    competitionSelected={competitionSelected}
                    setCompetitionSelected={setCompetitionSelected}
                />
            </TabPanel>
            <TabPanel index={'live'} activeTab={activeTab}>
                <ResultsLiveMatches eventId={eventId} />
            </TabPanel>
        </Box>
    )
}
export default ResultsPage
