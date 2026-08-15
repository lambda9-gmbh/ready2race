import {useState} from 'react'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {checkQrCode} from '@api/sdk.gen.ts'
import {Link} from '@tanstack/react-router'
import {Box, Button, Stack, Typography} from '@mui/material'
import QrCode2Icon from '@mui/icons-material/QrCode2'
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline'
import {useTranslation} from 'react-i18next'
import {resultsQRCodeRoute, router} from '@routes'
import Throbber from '@components/Throbber.tsx'
import {rememberMyEventCode} from '@utils/myEventStorage.ts'
import {qrScanOutcome} from './qrScanOutcome.ts'

/**
 * Die einladende Hinweisansicht für ein noch unverknüpftes Band: Wer vor dem Check-in
 * scannt, hat nichts falsch gemacht — statt einer Fehlermeldung wirbt die Seite dafür,
 * das Band in der Meldestelle mit dem eigenen Namen verknüpfen zu lassen. Jeder Punkt
 * der Liste existiert tatsächlich in „Mein Event" (nichts versprechen, was es nicht gibt).
 */
const UnlinkedQrHint = () => {
    const {t} = useTranslation()

    const benefits = [
        t('qrCode.unlinked.benefitMatches'),
        t('qrCode.unlinked.benefitCountdown'),
        t('qrCode.unlinked.benefitResults'),
        t('qrCode.unlinked.benefitSubstitute'),
        t('qrCode.unlinked.benefitLive'),
    ]

    return (
        <Stack
            alignItems="center"
            textAlign="center"
            gap={1.5}
            sx={{py: 6, px: 2, maxWidth: 480, mx: 'auto'}}>
            <QrCode2Icon sx={{fontSize: 56, color: 'text.secondary'}} />
            <Typography variant="h6" sx={{fontWeight: 700}}>
                {t('qrCode.unlinked.title')}
            </Typography>
            <Typography color="text.secondary">{t('qrCode.unlinked.intro')}</Typography>
            <Stack gap={1} sx={{textAlign: 'left', my: 1}}>
                {benefits.map(benefit => (
                    <Stack key={benefit} direction="row" gap={1} alignItems="flex-start">
                        <CheckCircleOutlineIcon color="success" fontSize="small" sx={{mt: '2px'}} />
                        <Typography>{benefit}</Typography>
                    </Stack>
                ))}
            </Stack>
            <Typography color="text.secondary">{t('qrCode.unlinked.rescan')}</Typography>
            <Link to={'/results'}>
                <Button variant={'contained'}>{t('common.back')}</Button>
            </Link>
        </Stack>
    )
}

const ResultsQrCodePage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const navigate = router.navigate

    const {qrCode} = resultsQRCodeRoute.useParams()

    // Ein unverknüpftes Band ist ein erwarteter Zustand (Backend: 204 ohne Inhalt) und
    // bekommt eine eigene Ansicht — nur echte Fehlerantworten bleiben eine Fehlermeldung.
    const [unlinked, setUnlinked] = useState(false)

    const {pending} = useFetch(signal => checkQrCode({signal, path: {qrCodeId: qrCode}}), {
        onResponse: response => {
            const outcome = qrScanOutcome({
                data: response.data,
                error: response.error,
                status: response.response.status,
            })
            switch (outcome.kind) {
                case 'unlinked':
                    setUnlinked(true)
                    break
                case 'error':
                    feedback.error(
                        t('common.load.error.single', {
                            entity: t('qrCode.qrCode'),
                        }),
                    )
                    break
                case 'user':
                    // Helferbänder tragen keine Teilnahme. Gemerkt gäbe der Code auf dem Gerät
                    // dauerhaft ein „Mein Event", das nur „Zu diesem Code gibt es keine Teilnahme"
                    // anzeigen kann — also ohne Speichern und ohne den Reiter auf die
                    // Ergebnisseite, die für Helfende ohnehin das Ziel ist.
                    navigate({
                        to: '/results/event/$eventId',
                        params: {eventId: outcome.eventId},
                    })
                    break
                case 'participant':
                    // Der Code wandert in den Gerätespeicher und nicht in die Zieladresse:
                    // ein weitergereichter Link soll niemanden in ein fremdes Dashboard lassen.
                    rememberMyEventCode({qrCode: qrCode, eventId: outcome.eventId})
                    navigate({
                        to: '/results/event/$eventId',
                        params: {eventId: outcome.eventId},
                        search: {tab: 'my-event'},
                    })
                    break
            }
        },
    })

    if (unlinked) {
        return <UnlinkedQrHint />
    }

    return (
        <Box sx={{display: 'flex', justifyContent: 'center', p: 2}}>
            {pending ? (
                <Throbber />
            ) : (
                <Link to={'/results'}>
                    <Button variant={'contained'}>{t('common.back')}</Button>
                </Link>
            )}
        </Box>
    )
}
export default ResultsQrCodePage
