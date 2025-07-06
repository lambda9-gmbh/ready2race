import {useState} from 'react'
import {useTranslation} from 'react-i18next'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {Box, Stack, Typography, Button, TextField} from '@mui/material'
import {userLogin} from '@api/sdk.gen.ts'
import {LoginRequest} from '@api/types.gen.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import {useRouter, useSearch} from '@tanstack/react-router'
import {appLoginRoute} from '@routes'

const AppLoginPage = () => {
    const {login} = useUser()
    const {t} = useTranslation()
    const router = useRouter()
    const search = useSearch({from: appLoginRoute.id})
    const [submitting, setSubmitting] = useState(false)
    const formContext = useForm<LoginRequest>()

    const handleSubmit = async (formData: LoginRequest) => {
        setSubmitting(true)
        const {data, response} = await userLogin({ body: formData })
        setSubmitting(false)
        if (data !== undefined && response.ok) {
            login(data, response.headers)
            if (search && typeof search.redirect === 'string' && search.redirect) {
                router.navigate({to: search.redirect})
            } else {
                router.navigate({to: '/app'})
            }
        }
    }

    return (
        <Box className="app-login-box">
            <Box className="app-login-inner">
                <Typography
                    variant="h2"
                    textAlign="center"
                >
                    {t('user.login.login')}
                </Typography>
                <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                    <Stack spacing={3}>
                        <TextField
                            label={t('user.email.email')}
                            type="email"
                            required
                            fullWidth
                            {...formContext.register('email', { required: true })}
                        />
                        <TextField
                            label={t('user.password.password')}
                            type="password"
                            required
                            fullWidth
                            {...formContext.register('password', { required: true })}
                        />
                        <Button
                            type="submit"
                            variant="contained"
                            color="primary"
                            fullWidth
                            size="large"
                            disabled={submitting}
                        >
                            {t('user.login.submit')}
                        </Button>
                    </Stack>
                </FormContainer>
            </Box>
        </Box>
    )
}

export default AppLoginPage 