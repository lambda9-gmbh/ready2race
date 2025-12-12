import Sidebar from '@components/sidebar/Sidebar.tsx'
import SidebarItem from '@components/sidebar/SidebarItem.tsx'
import {
    AdminPanelSettings,
    Assessment,
    Dashboard,
    Event,
    Home,
    People,
    PhoneAndroid,
    Receipt,
    Settings,
    Work,
    Workspaces,
} from '@mui/icons-material'
import {Box, Divider, ListItem, ListItemIcon, Typography} from '@mui/material'
import {
    readAdministrationConfigGlobal,
    readClubGlobal,
    readClubOwn,
    readInvoiceGlobal,
    readUserGlobal,
    updateEventGlobal,
} from '@authorization/privileges.ts'
import {useTranslation} from 'react-i18next'
import {useUser} from '@contexts/user/UserContext.ts'
import {useThemeConfig} from '@contexts/theme/ThemeContext.ts'
import Config from '../../Config.ts'
import TrendingFlatIcon from '@mui/icons-material/TrendingFlat'

type Props = {
    drawerExpanded: boolean
    setDrawerExpanded: (expanded: boolean) => void
    isSmallScreen: boolean
}

const SidebarContent = ({...props}: Props) => {
    const {t} = useTranslation()
    const user = useUser()
    const {themeConfig} = useThemeConfig()

    return (
        <Sidebar
            isOpen={props.drawerExpanded}
            open={() => props.setDrawerExpanded(true)}
            close={() => props.setDrawerExpanded(false)}
            isSmallScreen={props.isSmallScreen}>
            {props.isSmallScreen && (
                <>
                    <SidebarItem
                        text={t('landing.smallScreen.to.results')}
                        icon={<Assessment />}
                        to={'/results'}
                    />
                    <SidebarItem
                        text={t('landing.smallScreen.to.app')}
                        icon={<PhoneAndroid />}
                        to={'/app'}
                    />
                    <Divider sx={{my: 1}} />
                </>
            )}
            {!user.loggedIn && (
                <SidebarItem text={t('navigation.titles.landing')} icon={<Home />} to={'/'} />
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
            <SidebarItem text={t('navigation.titles.events')} icon={<Event />} to={'/event'} />
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
            <SidebarItem
                text={t('navigation.titles.administration')}
                icon={<AdminPanelSettings />}
                privilege={readAdministrationConfigGlobal}
                to={'/administration'}
            />
            {props.isSmallScreen &&
                themeConfig?.customLogo?.enabled &&
                themeConfig?.customLogo?.filename && (
                    <ListItem sx={{marginTop: 'auto'}}>
                        <Typography sx={{mr: 4}}>Ready2Race</Typography>
                        <ListItemIcon>
                            <TrendingFlatIcon />
                        </ListItemIcon>
                        <ListItemIcon>
                            <Box
                                component={'img'}
                                src={`${Config.logosUrl}/${themeConfig.customLogo.filename}`}
                                alt={'Custom Logo'}
                                sx={{
                                    height: {xs: 32, sm: 40},
                                    width: 'auto',
                                }}
                            />
                        </ListItemIcon>
                    </ListItem>
                )}
        </Sidebar>
    )
}

export default SidebarContent
