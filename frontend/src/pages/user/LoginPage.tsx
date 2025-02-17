import {useUser} from '../../contexts/user/UserContext.ts'
import {useState} from 'react'
import {LoginRequest, userLogin} from '../../api'
import {useTranslation} from 'react-i18next'
import {useFeedback} from '../../utils/hooks.ts'
import {FormContainer, PasswordElement, TextFieldElement, useForm} from 'react-hook-form-mui'
import {Box, Card, InputLabel, Stack, Typography} from '@mui/material'
import SimpleFormLayout from '../../components/SimpleFormLayout.tsx'
import {SubmitButton} from '../../components/form/SubmitButton.tsx'
import {FormInputText} from '../../components/form/input/FormInputText.tsx'
import FormInputPassword from "../../components/form/input/FormInputPassword.tsx";

type Form = LoginRequest

const LoginPage = () => {
    const {login} = useUser()
    const {t} = useTranslation()
    const feedback = useFeedback()

    const [submitting, setSubmitting] = useState(false)

    const formContext = useForm<Form>()

    const [formData, setFormData] = useState<LoginRequest>({
        email: '',
        password: '',
    })

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
                feedback.error(t('login.error'))
            }
        }
    }

    return (
        <SimpleFormLayout>
            <Box sx={{mb: 4}}>
                <Typography variant="h1">{t('login.login')}</Typography>
            </Box>
            <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                <Stack spacing={4}>
                    <FormInputText name="email" label={t('login.email')} required />
                    <FormInputPassword
                        name="password"
                        label={t('login.password')}
                        required
                    />
                    <SubmitButton label={t('login.submit')} submitting={submitting} />
                </Stack>
            </FormContainer>
        </SimpleFormLayout>
        /*<form>
            <label>
                {t('login.email')}
                <input
                    type={'email'}
                    value={formData.email}
                    onChange={e => setFormData(prev => ({...prev, email: e.target.value}))}
                />
            </label>
            <label>
                {t('login.password')}
                <input
                    type={'password'}
                    value={formData.password}
                    onChange={e => setFormData(prev => ({...prev, password: e.target.value}))}
                />
            </label>
            <button onClick={handleSubmit}>Login</button>
        </form>*/
    )
}

export default LoginPage
