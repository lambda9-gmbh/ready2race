import {Box} from '@mui/material'
import AthleteBoardView from '@components/event/info/views/AthleteBoardView'
import {athleteBoardRoute} from '@routes'

/**
 * Trägerseite der Athleten-Anzeige. Bewusst ohne Bedienelemente: ein fest montierter
 * Bildschirm hat keine Maus, und auf dem Telefon ist eine Seite, die nur zeigt,
 * schneller verstanden.
 */
const AthleteBoardPage = () => {
    const {eventId} = athleteBoardRoute.useParams()

    return (
        <Box sx={{height: '100dvh', overflow: 'hidden'}}>
            <AthleteBoardView eventId={eventId} />
        </Box>
    )
}

export default AthleteBoardPage
