import { Box, Typography } from '@mui/material'
import { InfoViewConfigurationDto } from '@api/types.gen'
import UpcomingMatchesView from './views/UpcomingMatchesView'
import { LatestMatchResultsView } from './views/LatestMatchResultsView'

interface InfoViewDisplayProps {
    eventId: string
    view: InfoViewConfigurationDto
}

const InfoViewDisplay = ({ eventId, view }: InfoViewDisplayProps) => {
    const renderView = () => {
        switch (view.viewType) {
            case 'UPCOMING_MATCHES':
                return (
                    <UpcomingMatchesView
                        eventId={eventId}
                        limit={view.dataLimit}
                        filters={view.filters}
                    />
                )
            case 'LATEST_MATCH_RESULTS':
                return (
                    <LatestMatchResultsView
                        eventId={eventId}
                        limit={view.dataLimit}
                        filters={view.filters}
                    />
                )
            default:
                return (
                    <Box sx={{ p: 3 }}>
                        <Typography>Unknown view type: {view.viewType}</Typography>
                    </Box>
                )
        }
    }
    
    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            {renderView()}
        </Box>
    )
}

export default InfoViewDisplay