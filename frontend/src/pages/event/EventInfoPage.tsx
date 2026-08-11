import {Box} from '@mui/material'
import BoardsPanel from '@components/event/board/BoardsPanel'
import {eventInfoRoute} from '@routes'

/**
 * Die Verwaltungsseite der Boards. Bis zum Board-Umbau (10.08.2026) rotierte hier die
 * Info-Seite selbst durch ihre Views; die Anzeige läuft jetzt ausschließlich über die
 * öffentlichen Board-URLs (/board/{eventId}/{boardId}), diese Seite konfiguriert sie
 * nur noch.
 */
const EventInfoPage = () => {
    const {eventId} = eventInfoRoute.useParams()

    return (
        <Box sx={{p: 3}}>
            <BoardsPanel eventId={eventId} />
        </Box>
    )
}

export default EventInfoPage
