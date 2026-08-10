import {useEffect} from 'react'
import {Box, CircularProgress, Typography} from '@mui/material'
import {useNavigate} from '@tanstack/react-router'
import {useTranslation} from 'react-i18next'
import {useFetch} from '@utils/hooks'
import {getPublicBoards} from '@api/sdk.gen'
import {athleteBoardRoute} from '@routes'

/**
 * Bestands-URL der alten Athleten-Anzeige (/board/{eventId}), auf die fest montierte
 * Bildschirme zeigen. Sie bleibt bestehen und leitet auf das erste Board des Events um
 * — die Migration hat dafür je Event mit Athleten-Anzeige ein Default-Board angelegt,
 * das der alten Bühne entspricht.
 */
const AthleteBoardPage = () => {
    const {t} = useTranslation()
    const {eventId} = athleteBoardRoute.useParams()
    const navigate = useNavigate()

    const {data: boards} = useFetch(signal => getPublicBoards({signal, path: {eventId}}), {
        deps: [eventId],
    })

    useEffect(() => {
        const first = boards?.[0]
        if (first) {
            void navigate({
                to: '/board/$eventId/$boardId',
                params: {eventId, boardId: first.id},
                replace: true,
            })
        }
    }, [boards, eventId, navigate])

    return (
        <Box
            sx={{
                display: 'flex',
                height: '100dvh',
                alignItems: 'center',
                justifyContent: 'center',
                p: 3,
            }}>
            {boards && boards.length === 0 ? (
                <Typography variant="h5" color="text.secondary">
                    {t('event.boards.none')}
                </Typography>
            ) : (
                <CircularProgress />
            )}
        </Box>
    )
}

export default AthleteBoardPage
