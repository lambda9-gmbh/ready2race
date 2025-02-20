import {useEffect, useState} from 'react'
import {verifyUserRegistration} from '@api/sdk.gen.ts'
import {registrationTokenRoute} from '@routes'
import {Box, Button, Stack, Typography} from '@mui/material'
import Throbber from '@components/Throbber.tsx'
import SimpleFormLayout from '@components/SimpleFormLayout.tsx'
import {CheckCircleOutline} from '@mui/icons-material'
import {Link} from '@tanstack/react-router'
import {useTranslation} from 'react-i18next'

const VerifyRegistrationPage = () => {
    const {t} = useTranslation()
    const {registrationToken} = registrationTokenRoute.useParams()

    const [verifying, setVerifying] = useState(false)

    useEffect(() => {
        const sendVerification = async () => {
            setVerifying(true)
            const {error} = await verifyUserRegistration({
                body: {
                    token: registrationToken,
                },
            })
            setVerifying(false)

            if (error) {
                console.log(error)
            }
        }
        sendVerification().catch(console.error)
    }, [])

    return (
        <SimpleFormLayout maxWidth={400}>
            {(verifying && <Throbber />) || (
                <Stack spacing={4}>
                    <Box sx={{display: 'flex', justifyContent: 'center'}}>
                        <CheckCircleOutline color="success" sx={{height: 100, width: 100}} />
                    </Box>
                    <Typography variant="h2" textAlign="center">
                        {t('user.registration.email.verified.header')}
                    </Typography>
                    <Typography textAlign="center">
                        {t('user.registration.email.verified.message')}
                    </Typography>
                    <Box sx={{display: 'flex', justifyContent: 'center'}}>
                        <Link to="/login">
                            <Button variant="contained">{t('user.login.login')}</Button>
                        </Link>
                    </Box>
                </Stack>
            )}
        </SimpleFormLayout>
    )
}

export default VerifyRegistrationPage
