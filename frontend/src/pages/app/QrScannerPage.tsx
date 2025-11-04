import {Button, Stack, Typography, useMediaQuery, useTheme} from '@mui/material'
import {CheckQrCodeResponse} from '@api/types.gen.ts'
import {useEffect, useRef} from 'react'
import QrNimiqScanner from '@components/qrApp/QrNimiqScanner.tsx'
import {useTranslation} from 'react-i18next'
import {useAppSession} from '@contexts/app/AppSessionContext'
import {checkQrCode} from '@api/sdk.gen.ts'
import {useFeedback} from '@utils/hooks.ts'
import Config from '../../Config.ts'
import {getUserAppRights} from '@components/qrApp/common.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import LogoutIcon from '@mui/icons-material/Logout'

const uuidRegex = /([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})$/

const QrScannerPage = () => {
    const {t} = useTranslation()
    const {qr, events, navigateTo} = useAppSession()
    const feedback = useFeedback()
    const theme = useTheme()
    const isMobile = useMediaQuery(theme.breakpoints.down('sm'))

    const initialEffect = useRef<boolean>(true)
    const qrCheckPending = useRef<boolean>(false)

    useEffect(() => {
        if (initialEffect.current) {
            qr.reset()
            initialEffect.current = false
        } else {
            if (qr.received && !qr.handled) {
                const response = qr.response

                if (response === null || response === undefined) {
                    navigateTo('App_Assign')
                } else if (response.type == 'Participant') {
                    navigateTo('APP_Participant')
                } else if (response.type == 'User') {
                    navigateTo('App_User')
                }
                qr.update({...qr, handled: true})
            }
        }
    }, [qr, navigateTo])

    async function handleScannerResult(qrCodeContent: string) {
        if (!qrCheckPending.current) {
            qrCheckPending.current = true
            const match = qrCodeContent.match(uuidRegex)

            if (match) {
                const qrCodeId = match[1]
                try {
                    const result = await checkQrCode({
                        path: {qrCodeId},
                        throwOnError: true,
                    })
                    let response: CheckQrCodeResponse | null = null
                    if (result.data && Object.keys(result.data).length > 0) {
                        response = result.data
                    }
                    qr.update({...qr, qrCodeId: qrCodeId, response: response, received: true})
                } catch {
                    feedback.error(
                        t('common.load.error.single', {
                            entity: t('qrCode.qrCode'),
                        }),
                    )
                }
            } else {
                feedback.error(t('qrAssign.invalidQrFormat'))
            }
            qrCheckPending.current = false
        }
    }
    const user = useUser()
    const availableAppFunctions = getUserAppRights(user)
    function goBack() {
        if (availableAppFunctions.length === 1) {
            navigateTo('APP_Event_List')
        } else {
            navigateTo('APP_Function_Select')
        }
    }

    return (
        <Stack
            spacing={3}
            alignItems="center"
            justifyContent="center"
            sx={{
                minHeight: '60vh',
                px: {xs: 2, sm: 3},
                py: 2,
            }}>
            <Typography variant={isMobile ? 'h4' : 'h3'} textAlign="center">
                {t('qrScanner.title')}
            </Typography>
            <QrNimiqScanner callback={handleScannerResult} />
            <Stack
                spacing={2}
                sx={{
                    width: '100%',
                    maxWidth: 400,
                    mt: 2,
                }}>
                {(Config.mode === 'development' || Config.mode === 'test') && (
                    <Button
                        onClick={() => handleScannerResult('b294a2e0-039d-4ede-bd84-c61a47dd9c04')}
                        fullWidth
                        size="large"
                        variant="contained"
                        color="primary"
                        sx={{
                            py: {xs: 1.5, sm: 2},
                        }}>
                        Skip ({Config.mode}-mode)
                    </Button>
                )}
                {availableAppFunctions.length > 1 || (events?.length ?? 0) > 1 ? (
                    <Button onClick={goBack} fullWidth variant="outlined">
                        {t('common.back')}
                    </Button>
                ) : (
                    <Button
                        onClick={() => 'logout' in user && user.logout(true)}
                        startIcon={<LogoutIcon />}
                        fullWidth
                        variant="outlined">
                        {t('user.settings.logout')}
                    </Button>
                )}
            </Stack>
        </Stack>
    )
}

export default QrScannerPage
