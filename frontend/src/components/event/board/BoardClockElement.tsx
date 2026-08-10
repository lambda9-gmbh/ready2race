import {Stack, Typography} from '@mui/material'
import {BoardElement, BoardViewDto} from '@api/types.gen'

interface BoardClockElementProps {
    element: BoardElement
    view: BoardViewDto
    /** Die servergeankerte Uhr der Seite (useServerClock) — keine eigene Geräteuhr. */
    now: Date
}

/**
 * Uhr/Kopfzeile als eigenes Element: Veranstaltungsname (abschaltbar) und die
 * Serveruhr, groß genug für die andere Seite des Stegs.
 */
const BoardClockElement = ({element, view, now}: BoardClockElementProps) => (
    <Stack
        sx={{height: '100%', minHeight: 0}}
        alignItems="center"
        justifyContent="center"
        gap="clamp(0.2rem, 0.5vh, 0.8rem)">
        {element.showEventName !== false && (
            <Typography
                sx={{fontSize: 'clamp(1rem, 2.2vw, 3.5rem)', fontWeight: 800, textAlign: 'center'}}
                noWrap>
                {view.eventName}
            </Typography>
        )}
        <Typography sx={{fontSize: 'clamp(2rem, 6vw, 9rem)', fontWeight: 800, lineHeight: 1}}>
            {now.toLocaleTimeString(undefined, {hour: '2-digit', minute: '2-digit'})}
        </Typography>
    </Stack>
)

export default BoardClockElement
