import {useEffect, useRef, useState} from 'react'
import {verifyUserRegistration} from '@api/sdk.gen.ts'
import {registrationTokenRoute} from '@routes'
import {Typography} from '@mui/material'
import Throbber from '@components/Throbber.tsx'
import SimpleFormLayout from '@components/SimpleFormLayout.tsx'
import {useTranslation} from 'react-i18next'
import RequestStatusResponse from '@components/user/RequestStatusResponse.tsx'

// TODO: validate/sanitize basepath (also in routes.tsx)
const basepath = document.getElementById('ready2race-root')!.dataset.basepath

const VerifyRegistrationPage = () => {
    const {t} = useTranslation()
    const {registrationToken} = registrationTokenRoute.useParams()

    const verificationRequested = useRef(false)

    const [requestResult, setRequestResult] = useState<
        'NotFound' | 'Unexpected' | 'Success' | null
    >(null)

    useEffect(() => {
        ;(async () => {
            if (verificationRequested.current) return
            verificationRequested.current = true
            const result = await verifyUserRegistration({
                body: {
                    token: registrationToken,
                    callbackUrl: location.origin + (basepath ? `/${basepath}` : '') + '/challenge/',
                },
            })

            if (result.error) {
                setRequestResult(result.error.status.value === 404 ? 'NotFound' : 'Unexpected')
            } else {
                setRequestResult('Success')
            }
        })()
    }, [])

    return (
        <SimpleFormLayout maxWidth={400}>
            {requestResult === null ? (
                <Throbber />
            ) : (
                <RequestStatusResponse
                    success={requestResult === 'Success'}
                    header={
                        requestResult === 'Success'
                            ? t('user.registration.requested.verified.header')
                            : t('user.registration.requested.verificationError.header')
                    }
                    showLoginNavigation>
                    <Typography textAlign="center">
                        {requestResult === 'Success'
                            ? t('user.registration.requested.verified.message')
                            : requestResult === 'NotFound'
                              ? t('user.registration.requested.verificationError.message.notFound')
                              : t(
                                    'user.registration.requested.verificationError.message.unexpected',
                                )}
                    </Typography>
                </RequestStatusResponse>
            )}
        </SimpleFormLayout>
    )
}

export default VerifyRegistrationPage
