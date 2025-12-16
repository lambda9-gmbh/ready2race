import Sidebar from '@components/sidebar/Sidebar.tsx'
import SidebarItem from '@components/sidebar/SidebarItem.tsx'
import {
    AdminPanelSettings,
    Assessment,
    ChevronLeft,
    ChevronRight,
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
import {Box, Divider, IconButton, Tooltip, Typography} from '@mui/material'
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
            {!props.isSmallScreen && (
                <>
                    <Tooltip
                        title={t(
                            props.drawerExpanded
                                ? 'navigation.sidebar.collapse'
                                : 'navigation.sidebar.expand',
                        )}
                        placement="right"
                        arrow>
                        <IconButton
                            onClick={() => props.setDrawerExpanded(!props.drawerExpanded)}
                            sx={{
                                width: '100%',
                                borderRadius: 0,
                                py: 1.5,
                                justifyContent: 'flex-end',
                                px: 2,
                            }}>
                            {props.drawerExpanded ? <ChevronLeft /> : <ChevronRight />}
                        </IconButton>
                    </Tooltip>
                    <Divider sx={{my: 1}} />
                </>
            )}
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
                    <Box
                        sx={{
                            marginTop: 'auto',
                            px: 3,
                            py: 3,
                            display: 'flex',
                            flexDirection: 'column',
                            alignItems: 'center',
                        }}>
                        <Typography
                            variant="subtitle1"
                            sx={{
                                fontWeight: 700,
                                mb: 1.5,
                            }}>
                            Ready2Race
                        </Typography>
                        <Divider
                            sx={{
                                width: '80%',
                                mb: 1.5,
                            }}
                        />
                        <Box
                            sx={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: 2,
                            }}>
                            <Typography
                                variant="caption"
                                sx={{
                                    color: 'text.secondary',
                                }}>
                                {t('common.for')}
                            </Typography>
                            <Box
                                component={'img'}
                                src={`${Config.logosUrl}/${themeConfig.customLogo.filename}`}
                                alt={'Custom Logo'}
                                sx={{
                                    height: {xs: 36, sm: 44},
                                    width: 'auto',
                                    maxWidth: '140px',
                                }}
                            />
                        </Box>
                    </Box>
                )}
        </Sidebar>
    )
}

export default SidebarContent
