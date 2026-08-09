import {
    Box,
    Button,
    Card,
    CardActionArea,
    CardContent,
    Stack,
    Typography,
    useMediaQuery,
    useTheme,
} from '@mui/material'
import {useAppSession} from '@contexts/app/AppSessionContext.tsx'
import {useEffect} from 'react'
import {useTranslation} from 'react-i18next'
import QrCodeIcon from '@mui/icons-material/QrCode'
import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import AssignmentIcon from '@mui/icons-material/Assignment'
import RestaurantIcon from '@mui/icons-material/Restaurant'
import SportsScoreIcon from '@mui/icons-material/SportsScore'
import {useUser} from '@contexts/user/UserContext.ts'
import {AppEntry, appEntries} from '@components/qrApp/common.ts'
import SwapHorizIcon from "@mui/icons-material/SwapHoriz";
import LogoutIcon from "@mui/icons-material/Logout";

const ENTRY_ICONS: Record<string, typeof QrCodeIcon> = {
    APP_QR_MANAGEMENT: QrCodeIcon,
    APP_COMPETITION_CHECK: CheckCircleIcon,
    APP_EVENT_REQUIREMENT: AssignmentIcon,
    APP_CATERER: RestaurantIcon,
    LIVE_DASHBOARD: SportsScoreIcon,
}

const AppFunctionSelectPage = () => {
    const {t} = useTranslation()
    const {setAppFunction, events, navigateTo} = useAppSession()
    const theme = useTheme()
    const isMobile = useMediaQuery(theme.breakpoints.down('sm'))
    const user = useUser()

    const entries = appEntries(user)

    useEffect(() => {
        if (entries.length === 0 && user.loggedIn) {
            navigateTo('APP_Forbidden')
        }
    }, [entries.length, user.loggedIn, navigateTo])

    const handleSelect = (entry: AppEntry) => {
        setAppFunction(entry.appFunction)
        navigateTo(entry.target)
    }

    return (
        <Stack
            spacing={4}
            alignItems="center"
            justifyContent="center"
            sx={{
                p: {xs: 2, sm: 4},
                minHeight: '60vh',
            }}>
            <Typography variant={isMobile ? 'h5' : 'h4'} textAlign="center">
                {t('app.functionSelect.title')}
            </Typography>
            <Box
                display="grid"
                gridTemplateColumns={{
                    xs: '1fr',
                    sm: 'repeat(2, 1fr)',
                    md: 'repeat(auto-fit, minmax(250px, 1fr))',
                }}
                gap={{xs: 2, sm: 3}}
                width="100%"
                maxWidth="800px">
                {entries.map(entry => {
                    const Icon = ENTRY_ICONS[entry.key]
                    return (
                        <Card
                            key={entry.key}
                            sx={{
                                height: {xs: '180px', sm: '200px'},
                                display: 'flex',
                                flexDirection: 'column',
                                transition: 'all 0.2s ease-in-out',
                                '&:hover': {
                                    transform: {xs: 'none', sm: 'scale(1.05)'},
                                    boxShadow: {xs: 2, sm: 4},
                                },
                                '&:active': {
                                    transform: 'scale(0.98)',
                                },
                            }}>
                            <CardActionArea
                                onClick={() => handleSelect(entry)}
                                sx={{
                                    height: '100%',
                                    display: 'flex',
                                    flexDirection: 'column',
                                    justifyContent: 'center',
                                    alignItems: 'center',
                                    p: {xs: 2, sm: 3},
                                }}>
                                <CardContent sx={{textAlign: 'center', p: 0}}>
                                    <Icon
                                        sx={{
                                            fontSize: {xs: 48, sm: 60},
                                            mb: {xs: 1, sm: 2},
                                            color: 'primary.main',
                                        }}
                                    />
                                    <Typography
                                        variant={isMobile ? 'body1' : 'h6'}
                                        sx={{
                                            fontWeight: isMobile ? 600 : 400,
                                        }}>
                                        {t(entry.labelKey)}
                                    </Typography>
                                </CardContent>
                            </CardActionArea>
                        </Card>
                    )
                })}
            </Box>
            {((events?.length ?? 0) > 1) ? (
                <Button
                    onClick={ () => navigateTo("APP_Event_List")}
                    variant="outlined"
                    startIcon={<SwapHorizIcon/>}
                    fullWidth
                    sx={{mt: 4}}>
                    {t('app.functionSelect.switchEvent')}
                </Button>
            ): (
                <Button
                    onClick={ () => 'logout' in user && user.logout(true)}
                    startIcon={<LogoutIcon/>}
                    fullWidth
                    variant="outlined"
                    sx={{mt: 4}}>
                    {t('user.settings.logout')}
                </Button>
            )}
        </Stack>
    )
}

export default AppFunctionSelectPage
