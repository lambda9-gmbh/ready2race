import {useFeedback, useFetch} from '@utils/hooks.ts'
import {checkQrCode} from '@api/sdk.gen.ts'
import {Link} from '@tanstack/react-router'
import {Box, Button} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {resultsQRCodeRoute, router} from '@routes'
import Throbber from '@components/Throbber.tsx'

const ResultsQrCodePage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const navigate = router.navigate

    const {qrCode} = resultsQRCodeRoute.useParams()

    const {pending} = useFetch(signal => checkQrCode({signal, path: {qrCodeId: qrCode}}), {
        onResponse: response => {
            if (response.error || response.data === undefined || response.response.status === 204) {
                feedback.error(
                    t('common.load.error.single', {
                        entity: t('qrCode.qrCode'),
                    }),
                )
            } else {
                navigate({to: '/results/event/$eventId', params: {eventId: response.data.eventId}})
            }
        },
    })

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
