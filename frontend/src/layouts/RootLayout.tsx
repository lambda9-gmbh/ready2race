import {Outlet} from '@tanstack/react-router'
import {AppBar, Box, Container, Divider, IconButton, Paper, Toolbar} from '@mui/material'
import {useState} from 'react'
import {
    Dashboard,
    EditCalendar,
    Event,
    Menu,
    MenuOpen,
    People,
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
    readClubOwn, readEventOwn,
    readUserGlobal,
    updateEventGlobal,
} from '@authorization/privileges.ts'
import {useUser} from '@contexts/user/UserContext.ts'

const RootLayout = () => {
    const {t} = useTranslation()
    const [drawerExpanded, setDrawerExpanded] = useState(false)
    const user = useUser()

    return (
        <Container maxWidth={'xl'}>
            <Paper elevation={24}>
                <Box>
                    <AppBar
                        sx={{
                            position: 'static',
                            zIndex: theme => theme.zIndex.drawer + 1,
                        }}>
                        <Toolbar sx={{justifyContent: 'space-between'}}>
                            <IconButton onClick={() => setDrawerExpanded(prev => !prev)}>
                                {drawerExpanded ? <MenuOpen /> : <Menu />}
                            </IconButton>
                            <UserWidget />
                        </Toolbar>
                    </AppBar>
                    <Box
                        sx={{
                            display: 'flex',
                        }}>
                        <Sidebar open={drawerExpanded}>
                            <SidebarItem
                                text={t('navigation.titles.dashboard')}
                                icon={<Dashboard />}
                                authenticatedOnly
                                to={'/dashboard'}
                            />
                            {user.loggedIn && user.clubId && (
                                <SidebarItem
                                    text={t('navigation.titles.myClubs')}
                                    icon={<Workspaces />}
                                    authenticatedOnly
                                    privilege={readClubOwn}
                                    to={'/club/$clubId'}
                                    params={{clubId: user.clubId}}
                                />
                            )}
                            <SidebarItem
                                text={t('navigation.titles.clubs')}
                                icon={<Workspaces />}
                                authenticatedOnly
                                privilege={readClubGlobal}
                                to={'/club'}
                            />
                            <SidebarItem
                                text={t('navigation.titles.events')}
                                icon={<Event />}
                                // TODO remove authenticatedOnly so everyone can see published events?
                                authenticatedOnly
                                privilege={readEventOwn}
                                to={'/event'}
                            />
                            <SidebarItem
                                text={t('navigation.titles.competitionConfig')}
                                icon={<EditCalendar />} //todo: better icon
                                authenticatedOnly
                                privilege={updateEventGlobal}
                                to={'/competitionConfig'}
                            />
                            <Divider />
                            <SidebarItem
                                text={t('navigation.titles.users')}
                                icon={<People />}
                                authenticatedOnly
                                privilege={readUserGlobal}
                                to={'/user'}
                            />
                            <SidebarItem
                                text={t('navigation.titles.roles')}
                                icon={<Work />}
                                authenticatedOnly
                                privilege={readUserGlobal}
                                to={'/role'}
                            />
                            <SidebarItem
                                text={t('navigation.titles.config')}
                                icon={<Settings />}
                                authenticatedOnly
                                privilege={updateEventGlobal}
                                to={'/config'}
                            />
                        </Sidebar>
                        <Box
                            sx={{
                                padding: 4,
                                width: 1,
                            }}>
                            <Outlet />
                        </Box>
                    </Box>
                </Box>
            </Paper>
        </Container>
    )
}

export default RootLayout
