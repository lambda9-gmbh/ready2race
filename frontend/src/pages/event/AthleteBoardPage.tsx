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
        // Das Scroll-Verbot gilt erst ab lg (siehe AthleteBoardView): darunter wächst die Seite
        // natürlich mit ihrem Inhalt und scrollt wie jede andere Seite.
        <Box sx={{height: {xs: 'auto', lg: '100dvh'}, overflow: {xs: 'visible', lg: 'hidden'}}}>
            <AthleteBoardView eventId={eventId} />
        </Box>
    )
}

export default AthleteBoardPage
