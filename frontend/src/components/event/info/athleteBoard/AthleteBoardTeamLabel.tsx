import {Box, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {TeamWithClubs, scaled, teamLabel} from './common'

interface AthleteBoardTeamLabelProps {
    team: TeamWithClubs
    /** Wie bei Typography: abgemeldete und ausgeschiedene Boote stehen gedämpft in der Liste. */
    color?: string
}

/**
 * Die Zeile eines Bootes: die Vereine seiner Athleten als Kette, dahinter der Mannschaftsname.
 *
 * Zwei Stufen, nicht drei — die Athleten-Anzeige hängt am Steg und muss auf Abstand lesbar
 * bleiben, eine Crew-Stufe wie auf dem Schiedsrichter-Board hätte hier nichts verloren. Auf dem
 * großen Schirm stehen die vollen Vereinsnamen, auf dem Telefon die Kurzformen.
 *
 * Beide Ketten stehen im Dokument, die Breite blendet eine davon aus. Das ist Absicht: die
 * Anzeige stuft ihre Größen heute schon rein über CSS ab (`clamp(…vw…)`, `direction={{xs, lg}}`)
 * und kommt ohne einen einzigen Umbau bei Größenänderung aus — ein Bildschirm, der tagelang
 * unbeaufsichtigt läuft, soll beim Drehen oder Zoomen nichts neu entscheiden müssen.
 *
 * Die Grenze liegt bei `md` (900 px) und damit dort, wo die Karte breit genug für ausgeschriebene
 * Namen wird: darunter füllt eine einzelne Karte ein Telefondisplay, darüber mindestens eine
 * halbe Bildschirmbreite.
 */
const AthleteBoardTeamLabel = ({team, color}: AthleteBoardTeamLabelProps) => {
    const {t} = useTranslation()

    return (
        <Typography
            sx={{
                fontSize: scaled('0.95rem', '1.6vw', '1.4rem'),
                fontWeight: 600,
                // Höchstens zwei Zeilen, danach Auslassungspunkte: ein sehr langer
                // Renngemeinschafts-Name darf die Bootszeile nicht aufblähen.
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
            }}
            color={color}>
            <Box component="span" sx={{display: {xs: 'inline', md: 'none'}}}>
                {teamLabel(team, t, 'short')}
            </Box>
            <Box component="span" sx={{display: {xs: 'none', md: 'inline'}}}>
                {teamLabel(team, t, 'full')}
            </Box>
        </Typography>
    )
}

export default AthleteBoardTeamLabel
