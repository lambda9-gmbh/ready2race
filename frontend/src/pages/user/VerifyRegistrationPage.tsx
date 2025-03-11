import {useEffect, useState} from 'react'
import {verifyUserRegistration} from '@api/sdk.gen.ts'
import {registrationTokenRoute} from '@routes'
import {Typography} from '@mui/material'
import Throbber from '@components/Throbber.tsx'
import SimpleFormLayout from '@components/SimpleFormLayout.tsx'
import {useTranslation} from 'react-i18next'
import RequestStatusResponse from '@components/user/RequestStatusResponse.tsx'

const VerifyRegistrationPage = () => {
    const {t} = useTranslation()
    const {registrationToken} = registrationTokenRoute.useParams()

    const [verifying, setVerifying] = useState(false)

    useEffect(() => {
        ;(async () => {
            setVerifying(true)
            await verifyUserRegistration({
                body: {
                    token: registrationToken,
                },
            })
            setVerifying(false)
        })()
    }, [])

    return (
        <SimpleFormLayout maxWidth={400}>
            {(verifying && <Throbber />) || (
                <RequestStatusResponse
                    success={true}
                    header={t('user.registration.email.verified.header')}
                    showLoginNavigation>
                    <Typography textAlign="center">
                        {t('user.registration.email.verified.message')}
                    </Typography>
                </RequestStatusResponse>
            )}
        </SimpleFormLayout>
    )
}

export default VerifyRegistrationPage
