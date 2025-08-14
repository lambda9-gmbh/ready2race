import MatchResults from '@components/results/MatchResults.tsx'
import {Box, IconButton, Stack, Tab, Typography, useMediaQuery, useTheme} from '@mui/material'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'
import TabSelectionContainer from '@components/tab/TabSelectionContainer.tsx'
import {a11yProps} from '@utils/helpers.ts'
import {useState} from 'react'
import TabPanel from '@components/tab/TabPanel.tsx'
import CellTowerOutlinedIcon from '@mui/icons-material/CellTowerOutlined'
import EmojiEventsOutlinedIcon from '@mui/icons-material/EmojiEventsOutlined'
import {resultsEventRoute} from '@routes'
import {Link} from '@tanstack/react-router'

const RESULTS_TABS = ['latest-results', 'live', 'upcoming'] as const
export type ResultsTab = (typeof RESULTS_TABS)[number]

const ResultsPage = () => {
    const theme = useTheme()

    const smallScreenLayout = useMediaQuery(`(max-width:${theme.breakpoints.values.sm}px)`)

    const {eventId} = resultsEventRoute.useParams()

    const [activeTab, setActiveTab] = useState<ResultsTab>('latest-results')
    const switchTab = (tab: ResultsTab) => {
        setActiveTab(tab)
    }
    const tabProps = (tab: ResultsTab) => a11yProps('results', tab)

    return (
        <Box sx={{maxWidth: theme.breakpoints.values.sm}}>
            <Stack direction={'row'} sx={{alignItems: 'center'}}>
                <Box>
                    <Link to={'/results'}>
                        <IconButton>
                            <ArrowBackIcon />
                        </IconButton>
                    </Link>
                </Box>
                <Box sx={{flex: 1}}>
                    <TabSelectionContainer activeTab={activeTab} setActiveTab={switchTab}>
                        <Tab
                            label={'Results'}
                            icon={<EmojiEventsOutlinedIcon />}
                            iconPosition={smallScreenLayout ? 'top' : 'start'}
                            sx={{flex: 1}}
                            {...tabProps('latest-results')}
                        />
                        <Tab
                            label={'Live'}
                            icon={<CellTowerOutlinedIcon />}
                            iconPosition={smallScreenLayout ? 'top' : 'start'}
                            sx={{flex: 1}}
                            {...tabProps('live')}
                        />
                    </TabSelectionContainer>
                </Box>
            </Stack>
            <TabPanel index={'latest-results'} activeTab={activeTab}>
                <MatchResults eventId={eventId} />
            </TabPanel>
            <TabPanel index={'live'} activeTab={activeTab}>
                <Typography sx={{m:2}}>Coming soon...</Typography>
            </TabPanel>
        </Box>
    )
}
export default ResultsPage
