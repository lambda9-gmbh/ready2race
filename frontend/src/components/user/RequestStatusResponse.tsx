import {Box, Button, Stack, Typography} from '@mui/material'
import {CheckCircleOutline, ErrorOutline} from '@mui/icons-material'
import {Link} from '@tanstack/react-router'
import {PropsWithChildren} from 'react'
import {useTranslation} from 'react-i18next'

type Props = {
    success: boolean
    header: string
    showLoginNavigation?: boolean
}

const RequestStatusResponse = ({children, ...props}: PropsWithChildren<Props>) => {
    const {t} = useTranslation()
    return (
        <Stack spacing={4}>
            <Box sx={{display: 'flex', justifyContent: 'center'}}>
                {(props.success && (
                    <CheckCircleOutline color="success" sx={{height: 100, width: 100}} />
                )) || <ErrorOutline color="error" sx={{height: 100, width: 100}} />}
            </Box>
            <Typography variant="h2" textAlign="center">
                {props.header}
            </Typography>
            {children}
            {props.showLoginNavigation && (
                <Box sx={{display: 'flex', justifyContent: 'center'}}>
                    <Link to="/login">
                        <Button variant="contained">{t('user.login.login')}</Button>
                    </Link>
                </Box>
            )}
        </Stack>
    )
}

export default RequestStatusResponse
