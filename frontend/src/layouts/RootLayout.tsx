import {Link, Outlet, useLocation} from '@tanstack/react-router'
import {
    AppBar,
    Box,
    Button,
    Container,
    Divider,
    IconButton,
    Paper,
    Stack,
    Toolbar,
    Typography,
} from '@mui/material'
import {useState} from 'react'
import {
    Dashboard,
    Event,
    Home,
    Login,
    Menu,
    MenuOpen,
    People,
    Receipt,
    Settings,
    Work,
    Workspaces,
} from '@mui/icons-material'
import Sidebar from '@components/sidebar/Sidebar.tsx'
import SidebarItem from '@components/sidebar/SidebarItem.tsx'
import UserWidget from '@components/appbar/UserWidget.tsx'
import {useTranslation} from 'react-i18next'
import {
    readClubGlobal,
    readClubOwn,
    readInvoiceGlobal,
    readUserGlobal,
    updateEventGlobal,
} from '@authorization/privileges.ts'
import {useUser} from '@contexts/user/UserContext.ts'
import LanguageWidget from '@components/appbar/LanguageWidget.tsx'

const RootLayout = () => {
    const {t} = useTranslation()
    const [drawerExpanded, setDrawerExpanded] = useState(true)
    const user = useUser()
    const location = useLocation()

    const languageSet = Boolean(document.getElementById('ready2race-root')!.dataset.lng)

    return (
        <Container maxWidth={'xl'}>
            <Paper elevation={24}>
                <Box sx={{background: t => t.palette.background.default}}>
                    <AppBar
                        elevation={2}
                        sx={{
                            position: 'static',
                        }}>
                        <Toolbar sx={{justifyContent: 'space-between'}}>
                            <IconButton onClick={() => setDrawerExpanded(prev => !prev)}>
                                {drawerExpanded ? <MenuOpen /> : <Menu />}
                            </IconButton>
                            <Stack direction={'row'} spacing={1}>
                                {!languageSet && <LanguageWidget />}
                                <UserWidget />
                            </Stack>
                        </Toolbar>
                    </AppBar>
                    <Box
                        sx={{
                            display: 'flex',
                        }}>
                        <Sidebar open={drawerExpanded}>
                            {!user.loggedIn && (
                                <SidebarItem
                                    text={t('navigation.titles.landing')}
                                    icon={<Home />}
                                    to={'/'}
                                />
                            )}
                            <SidebarItem
                                text={t('navigation.titles.dashboard')}
                                icon={<Dashboard />}
                                authenticatedOnly
                                to={'/dashboard'}
                            />
                            {user.loggedIn && user.clubId && (
                                <SidebarItem
                                    text={t('navigation.titles.myClub')}
                                    icon={<Workspaces />}
                                    privilege={readClubOwn}
                                    to={'/club/$clubId'}
                                    params={{clubId: user.clubId}}
                                />
                            )}
                            <SidebarItem
                                text={t('navigation.titles.clubs')}
                                icon={<Workspaces />}
                                privilege={readClubGlobal}
                                to={'/club'}
                            />
                            <SidebarItem
                                text={t('navigation.titles.events')}
                                icon={<Event />}
                                to={'/event'}
                            />
                            <SidebarItem
                                text={t('navigation.titles.users')}
                                icon={<People />}
                                privilege={readUserGlobal}
                                to={'/user'}
                            />
                            <SidebarItem
                                text={t('navigation.titles.roles')}
                                icon={<Work />}
                                privilege={readUserGlobal}
                                to={'/role'}
                            />
                            <SidebarItem
                                text={t('navigation.titles.config')}
                                icon={<Settings />}
                                privilege={updateEventGlobal}
                                to={'/config'}
                            />
                            <SidebarItem
                                text={t('navigation.titles.invoices')}
                                icon={<Receipt />}
                                privilege={readInvoiceGlobal}
                                to={'/invoices'}
                            />
                        </Sidebar>
                        <Stack
                            sx={{
                                padding: 4,
                                boxSizing: 'border-box',
                                flex: 1,
                                minWidth: 0,
                                justifyContent: 'space-between',
                            }}>
                            <Outlet />
                            {!user.loggedIn &&
                                location.pathname != '/login' &&
                                location.pathname != '/registration' && (
                                    <Stack spacing={1} mt={1} mb={-3}>
                                        <Stack
                                            spacing={2}
                                            direction={'row'}
                                            alignItems={'center'}
                                            divider={<Divider orientation={'vertical'} flexItem />}
                                            justifyContent={'center'}>
                                            <Link to={'/login'}>
                                                <Button endIcon={<Login />}>
                                                    <Typography>{t('user.login.login')}</Typography>
                                                </Button>
                                            </Link>
                                            <Stack
                                                direction="row"
                                                spacing="5px"
                                                justifyContent="center">
                                                <Typography sx={{fontWeight: 'light'}}>
                                                    {t('user.login.signUp.message')}
                                                </Typography>
                                                <Link to="/registration">
                                                    <Typography color={'primary'}>
                                                        {t('user.login.signUp.link')}
                                                    </Typography>
                                                </Link>
                                            </Stack>
                                        </Stack>
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
