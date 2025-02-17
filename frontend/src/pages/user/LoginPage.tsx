import {useUser} from '../../contexts/user/UserContext.ts'
import {useState} from 'react'
import {LoginRequest, userLogin} from '../../api'
import {useTranslation} from 'react-i18next'
import {useFeedback} from '../../utils/hooks.ts'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {Box, Divider, Stack, Typography} from '@mui/material'
import SimpleFormLayout from '../../components/SimpleFormLayout.tsx'
import {SubmitButton} from '../../components/form/SubmitButton.tsx'
import {FormInputText} from '../../components/form/input/FormInputText.tsx'
import FormInputPassword from '../../components/form/input/FormInputPassword.tsx'
import {Link} from '@tanstack/react-router'

type Form = LoginRequest

const LoginPage = () => {
    const {login} = useUser()
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const formContext = useForm<Form>()

    const handleSubmit = async (formData: Form) => {
        setSubmitting(true)
        const {data, error} = await userLogin({
            body: formData,
        })

        setSubmitting(false)
        if (data !== undefined) {
            login(data)
        } else if (error) {
            if (error.status.value === 500) {
                feedback.error(t('common.error.unexpected'))
            } else {
                feedback.error(t('user.login.error'))
            }
        }
    }

    return (
        <SimpleFormLayout maxWidth={400}>
            <Box sx={{mb: 4}}>
                <Typography variant="h1">{t('user.login.login')}</Typography>
            </Box>
            <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                <Stack spacing={4}>
                    <FormInputText name="email" label={t('user.login.email')} required />
                    <Box sx={{display: 'flex', flexDirection: 'column'}}>
                        <FormInputPassword name="password" label={t('user.login.password')} required />
                        <Box sx={{display: 'flex', justifyContent: 'end', mt: 2}}>
                            <Link to='/passwordReset'>
                                <Typography>{t('user.login.forgotPassword')}</Typography>
                            </Link>
                        </Box>
                    </Box>
                    <SubmitButton label={t('user.login.submit')} submitting={submitting} />
                    <Divider />
                    <Stack direction='row' spacing='5px' justifyContent='center'>
                        <Typography sx={{fontWeight: 'light'}}>{t('user.login.signUp.message')}</Typography>
                        <Link to='/registration'>
                            <Typography>{t('user.login.signUp.link')}</Typography>
                        </Link>
                    </Stack>
                </Stack>
            </FormContainer>
        </SimpleFormLayout>
    )
}

export default LoginPage
