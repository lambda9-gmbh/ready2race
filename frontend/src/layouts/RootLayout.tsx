import {Link, Outlet, useLocation, useMatchRoute} from '@tanstack/react-router'
import {
    AppBar,
    Box,
    Button,
    Chip,
    Container,
    Divider,
    Drawer,
    IconButton,
    Paper,
    Stack,
    Toolbar,
    Typography,
    useMediaQuery,
    useTheme,
} from '@mui/material'
import {useEffect, useState} from 'react'
import {Login, Menu, MenuOpen} from '@mui/icons-material'
import UserWidget from '@components/appbar/UserWidget.tsx'
import {useTranslation} from 'react-i18next'
import {useUser} from '@contexts/user/UserContext.ts'
import LanguageWidget from '@components/appbar/LanguageWidget.tsx'
import SidebarContent from '@components/sidebar/SidebarContent.tsx'
import {useThemeConfig} from '@contexts/theme/ThemeContext.ts'
import Config from '../Config.ts'
import logo from '../assets/r2r_logo.png'

const RootLayout = () => {
    const {t} = useTranslation()
    const theme = useTheme()
    const isMobile = useMediaQuery(theme.breakpoints.down('md'))
    const [drawerExpanded, setDrawerExpanded] = useState(!isMobile)
    const user = useUser()
    const location = useLocation()
    const matchRoute = useMatchRoute()
    const {themeConfig} = useThemeConfig()

    const languageSet = Boolean(document.getElementById('ready2race-root')!.dataset.lng)

    const isOnLoginRoute = matchRoute({to: '/login'})
    const isOnRegistrationRoute = matchRoute({to: '/registration'})

    // Auto-close drawer on navigation when on mobile
    useEffect(() => {
        if (isMobile && drawerExpanded) {
            setDrawerExpanded(false)
        }
    }, [location.pathname, isMobile])

    return (
        <Container maxWidth={'xl'} disableGutters={isMobile}>
            <Paper elevation={isMobile ? 0 : 24}>
                <Box sx={{background: t => t.palette.background.default}}>
                    <AppBar
                        elevation={2}
                        sx={{
                            position: 'static',
                        }}>
                        <Toolbar sx={{justifyContent: 'space-between'}}>
                            <Stack direction={'row'} spacing={2} alignItems={'center'}>
                                <IconButton onClick={() => setDrawerExpanded(prev => !prev)}>
                                    {drawerExpanded ? <MenuOpen /> : <Menu />}
                                </IconButton>
                                <Box
                                    component={'img'}
                                    src={logo}
                                    alt={'Ready2Race Logo'}
                                    sx={{
                                        height: {xs: 32, sm: 40},
                                        width: 'auto',
                                    }}
                                />
                                {themeConfig?.customLogo?.enabled && themeConfig?.customLogo?.filename && (
                                    <Box
                                        component={'img'}
                                        src={`${Config.logosUrl}/${themeConfig.customLogo.filename}`}
                                        alt={'Custom Logo'}
                                        sx={{
                                            height: {xs: 32, sm: 40},
                                            width: 'auto',
                                        }}
                                    />
                                )}
                            </Stack>
                            {(Config.mode === 'development' || Config.mode === 'test') && (
                                <Chip
                                    label={Config.mode === 'development' ? 'Dev-Mode' : 'Test-Mode'}
                                    color={Config.mode === 'development' ? 'warning' : 'secondary'}
                                    sx={{
                                        position: 'absolute',
                                        left: '50%',
                                        transform: 'translateX(-50%)',
                                        fontWeight: 'bold',
                                    }}
                                />
                            )}
                            <Stack direction={'row'} spacing={1}>
                                {!languageSet && <LanguageWidget />}
                                <UserWidget />
                            </Stack>
                        </Toolbar>
                    </AppBar>
                    <Box
                        sx={{
                            display: 'flex',
                            position: 'relative',
                        }}>
                        {isMobile ? (
                            <Drawer
                                variant="temporary"
                                open={drawerExpanded}
                                onClose={() => setDrawerExpanded(false)}
                                ModalProps={{
                                    keepMounted: true,
                                }}
                                className={'ready2race'}
                                sx={{
                                    '& .MuiDrawer-paper': {
                                        boxSizing: 'border-box',
                                        width: 280,
                                    },
                                }}>
                                <SidebarContent
                                    drawerExpanded={drawerExpanded}
                                    setDrawerExpanded={setDrawerExpanded}
                                    isSmallScreen={isMobile}
                                />
                            </Drawer>
                        ) : (
                            <SidebarContent
                                drawerExpanded={drawerExpanded}
                                setDrawerExpanded={setDrawerExpanded}
                                isSmallScreen={isMobile}
                            />
                        )}
                        <Stack
                            sx={{
                                padding: {xs: 2, sm: 3, md: 4},
                                boxSizing: 'border-box',
                                flex: 1,
                                minWidth: 0,
                                justifyContent: 'space-between',
                            }}>
                            <Outlet />
                            {!user.loggedIn && (
                                <Stack
                                    mt={2}
                                    mb={{xs: 0, md: -2}}
                                    spacing={{xs: 1, sm: 2}}
                                    direction={{xs: 'column', sm: 'row'}}
                                    alignItems={'center'}
                                    divider={
                                        !isMobile && <Divider orientation={'vertical'} flexItem />
                                    }
                                    justifyContent={'center'}>
                                    {!isOnLoginRoute && (
                                        <Link to={'/login'}>
                                            <Button endIcon={<Login />} fullWidth={isMobile}>
                                                <Typography>{t('user.login.login')}</Typography>
                                            </Button>
                                        </Link>
                                    )}
                                    {!isOnRegistrationRoute && (
                                        <Stack
                                            direction={isMobile ? 'column' : 'row'}
                                            spacing="5px"
                                            justifyContent="center"
                                            sx={{textAlign: 'center'}}>
                                            <Typography sx={{fontWeight: 'light'}}>
                                                {t('user.login.signUp.message')}
                                            </Typography>
                                            <Link to="/registration">
                                                <Typography color={'primary'}>
                                                    {t('user.login.signUp.link')}
                                                </Typography>
                                            </Link>
                                        </Stack>
                                    )}
                                </Stack>
                            )}
                        </Stack>
                    </Box>
                </Box>
            </Paper>
        </Container>
    )
}

export default RootLayout
