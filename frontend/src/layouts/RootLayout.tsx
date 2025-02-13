import {Outlet} from '@tanstack/react-router'
import {AppBar, Box, Container, IconButton, Paper, Toolbar} from '@mui/material'
import {useState} from 'react'
import {Dashboard, Menu, MenuOpen, People, Work} from '@mui/icons-material'
import Sidebar from '../components/sidebar/Sidebar.tsx'
import SidebarItem from '../components/sidebar/SidebarItem.tsx'
import UserWidget from '../components/appbar/UserWidget.tsx'
import {useTranslation} from 'react-i18next'
import {readRoleGlobal, readUserGlobal} from '../authorization/privileges.ts'

const RootLayout = () => {
    const {t} = useTranslation()
    const [drawerExpanded, setDrawerExpanded] = useState(false)

    return (
        <Container maxWidth={'xl'}>
            <Paper elevation={24}>
                <Box>
                    <AppBar
                        sx={{
                            position: 'static',
                            zIndex: theme => theme.zIndex.drawer + 1,
                        }}>
                        <Toolbar>
                            <IconButton onClick={() => setDrawerExpanded(prev => !prev)}>
                                {drawerExpanded ? <MenuOpen /> : <Menu />}
                            </IconButton>
                            <Box sx={{flexGrow: 1}}></Box>
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
                                privilege={readRoleGlobal}
                                to={'/role'}
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
