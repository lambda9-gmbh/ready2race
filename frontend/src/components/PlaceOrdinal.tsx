import {Box} from '@mui/material'
import {placeOrdinalParts} from '@utils/placeOrdinal'

/**
 * Ein Platz als große Ziffer mit klein hochgestelltem englischen Suffix („1" + „st") —
 * das voll ausgeschriebene Ordinal wirkte in der großen Platz-Typografie klobig
 * (Nutzer-Feedback mit Screenshot, 12.08.2026). Erbt Schriftgröße, -gewicht und Farbe
 * der umgebenden Typography; nur an den GROSSEN Platz-Stellen einsetzen — einzeilige
 * Text-Labels und Tooltips bleiben bei [formatPlaceOrdinal].
 */
const PlaceOrdinal = ({place}: {place: number}) => {
    const parts = placeOrdinalParts(place)
    return (
        <>
            {parts.number}
            <Box
                component="span"
                sx={{
                    fontSize: '0.58em',
                    // lineHeight 0: das hochgestellte Suffix darf die Zeilenhöhe der
                    // großen Karten nicht aufspannen, sonst springen die Bootszeilen.
                    lineHeight: 0,
                    verticalAlign: 'super',
                }}>
                {parts.suffix}
            </Box>
        </>
    )
}

export default PlaceOrdinal
