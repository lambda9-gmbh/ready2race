import {useUser} from '@contexts/user/UserContext.ts'
import {useState} from 'react'
import {useTranslation} from 'react-i18next'
import {useFeedback} from '@utils/hooks.ts'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {Box, Stack, Typography} from '@mui/material'
import SimpleFormLayout from '@components/SimpleFormLayout.tsx'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputPassword from '@components/form/input/FormInputPassword.tsx'
import {Link} from '@tanstack/react-router'
import {userLogin} from '@api/sdk.gen.ts'
import {LoginRequest} from '@api/types.gen.ts'

type Form = LoginRequest

const LoginPage = () => {
    const {login} = useUser()
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const formContext = useForm<Form>()

    const handleSubmit = async (formData: Form) => {
        setSubmitting(true)
        const {data, error, response} = await userLogin({
            body: formData,
        })

        setSubmitting(false)
        if (data !== undefined && response.ok) {
            login(data, response.headers)
        } else if (error) {
            if (error.status.value === 429) {
                feedback.error(t('user.login.error.tooManyRequests'))
            } else if (error.status.value === 500) {
                feedback.error(t('common.error.unexpected'))
            } else {
                feedback.error(t('user.login.error.credentials'))
            }
        }
    }

    return (
        <SimpleFormLayout maxWidth={400}>
            <Box sx={{mb: 4}}>
                <Typography variant="h1" textAlign="center">
                    {t('user.login.login')}
                </Typography>
            </Box>
            <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                <Stack spacing={4}>
                    <FormInputText name="email" label={t('user.email.email')} required />
                    <Box sx={{display: 'flex', flexDirection: 'column'}}>
                        <FormInputPassword
                            name="password"
                            label={t('user.password.password')}
                            required
                        />
                        <Box sx={{display: 'flex', justifyContent: 'end', mt: 2}}>
                            <Link to="/resetPassword">
                                <Typography>{t('user.login.forgotPassword')}</Typography>
                            </Link>
                        </Box>
                    </Box>
                    <SubmitButton submitting={submitting}>{t('user.login.submit')}</SubmitButton>
                </Stack>
            </FormContainer>
        </SimpleFormLayout>
    )
}

export default LoginPage
