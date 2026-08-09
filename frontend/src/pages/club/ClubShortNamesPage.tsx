import {Box, Typography} from '@mui/material'
import {Link} from '@tanstack/react-router'
import {useTranslation} from 'react-i18next'
import ClubShortNamePanel from '@components/club/shortName/ClubShortNamePanel.tsx'

/**
 * Stammdaten → Vereinskurzformen.
 *
 * Eine eigene Seite neben der Vereinsübersicht, nicht ein Abschnitt darunter: gepflegt wird hier
 * der Vereins*name*, und die Mehrzahl der vorkommenden Namen gehört zu gar keinem Verein aus der
 * Übersicht — Gastruderer tragen ihren Verein als Freitext an der Person.
 *
 * Beide Abschnitte bleiben auf einer Seite: wer oben eine Kürzungsregel anfasst, muss unmittelbar
 * darunter sehen, was sie mit den echten Vereinsnamen macht.
 */
const ClubShortNamesPage = () => {
    const {t} = useTranslation()

    return (
        <Box>
            <ClubShortNamePanel />
            <Box sx={{mt: 3}}>
                <Link to={'/club'}>
                    <Typography color={'primary'}>{t('club.shortName.toClubs')}</Typography>
                </Link>
            </Box>
        </Box>
    )
}

export default ClubShortNamesPage
