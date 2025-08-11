import {useState} from 'react'
import {useTranslation} from 'react-i18next'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {Box, Stack, Typography, Button, TextField, Paper, useMediaQuery, useTheme} from '@mui/material'
import {userLogin} from '@api/sdk.gen.ts'
import {LoginRequest} from '@api/types.gen.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import {useRouter, useSearch} from '@tanstack/react-router'
import {appLoginRoute} from '@routes'
import {useFeedback} from "@utils/hooks.ts";

const AppLoginPage = () => {
    const {login} = useUser()
    const {t} = useTranslation()
    const router = useRouter()
    const search = useSearch({from: appLoginRoute.id})
    const [submitting, setSubmitting] = useState(false)
    const formContext = useForm<LoginRequest>()
    const feedback = useFeedback();

    const handleSubmit = async (formData: LoginRequest) => {
        setSubmitting(true)
        const {data, error, response} = await userLogin({ body: formData })
        setSubmitting(false)
        if (data !== undefined && response.ok) {
            login(data, response.headers)

            if (search && typeof search.redirect === 'string' && search.redirect) {
                router.navigate({to: search.redirect})
            } else {
                router.navigate({to: '/app/function'})
            }
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

    const theme = useTheme();
    const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

    return (
        <Box 
            sx={{ 
                display: 'flex', 
                justifyContent: 'center', 
                alignItems: 'center',
                minHeight: '80vh',
                width: '100%',
                px: 4
            }}
        >
            <Paper
                elevation={isMobile ? 0 : 3}
                sx={{
                    p: { xs: 3, sm: 4 },
                    width: '100%',
                    maxWidth: { xs: '100%', sm: 400 },
                    borderRadius: { xs: 0, sm: 2 }
                }}
            >
                <Typography
                    variant={isMobile ? "h4" : "h3"}
                    textAlign="center"
                    gutterBottom
                    sx={{ mb: 3 }}
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
                            size={isMobile ? "medium" : "medium"}
                            {...formContext.register('email', { required: true })}
                        />
                        <TextField
                            label={t('user.password.password')}
                            type="password"
                            required
                            fullWidth
                            size={isMobile ? "medium" : "medium"}
                            {...formContext.register('password', { required: true })}
                        />
                        <Button
                            type="submit"
                            variant="contained"
                            color="primary"
                            fullWidth
                            size="large"
                            disabled={submitting}
                            sx={{
                                mt: 2,
                                py: { xs: 1.5, sm: 1 }
                            }}
                        >
                            {t('user.login.submit')}
                        </Button>
                    </Stack>
                </FormContainer>
            </Paper>
        </Box>
    )
}

export default AppLoginPage 