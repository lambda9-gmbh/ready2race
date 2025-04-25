import {useUser} from '@contexts/user/UserContext.ts'
import {IconButton, Menu, MenuItem, Stack, Tooltip, Typography} from '@mui/material'
import {AccountCircle, Login, Logout, Person} from '@mui/icons-material'
import {Link} from '@tanstack/react-router'
import React, {useState} from 'react'
import {useTranslation} from 'react-i18next'

const UserWidget = () => {
    const user = useUser()
    const {t} = useTranslation()

    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null)
    const open = Boolean(anchorEl)
    const handleClick = (event: React.MouseEvent<HTMLElement>) => {
        setAnchorEl(event.currentTarget)
    }
    const handleClose = () => {
        setAnchorEl(null)
    }

    if (user.loggedIn) {
        const handleLogout = () => {
            user.logout()
        }

        return (
            <>
                <Tooltip title={t('user.settings.menu')}>
                    <IconButton onClick={handleClick}>
                        <AccountCircle />
                    </IconButton>
                </Tooltip>
                <Menu
                    className={'ready2race'}
                    anchorEl={anchorEl}
                    open={open}
                    onClose={handleClose}
                    onClick={handleClose}
                    transformOrigin={{horizontal: 'right', vertical: 'top'}}
                    anchorOrigin={{horizontal: 'right', vertical: 'bottom'}}
                    slotProps={{
                        paper: {
                            elevation: 0,
                            sx: {
                                overflow: 'visible',
                                filter: 'drop-shadow(0px 2px 8px rgba(0,0,0,0.32))',
                            },
                        },
                    }}>
                    <MenuItem onClick={handleClose} sx={{p: 0}}>
                        <Link
                            to={'/user/$userId'}
                            params={{userId: user.id}}
                            style={{width: '100%'}}>
                            <Stack
                                direction="row"
                                spacing={2}
                                sx={{width: 1, py: '6px', px: '16px', boxSizing: 'border-box'}}>
                                <Person />
                                <Typography>{t('user.settings.profile')}</Typography>
                            </Stack>
                        </Link>
                    </MenuItem>
                    <MenuItem onClick={handleLogout}>
                        <Stack direction="row" spacing={2}>
                            <Logout />
                            <Typography>{t('user.settings.logout')}</Typography>
                        </Stack>
                    </MenuItem>
                </Menu>
            </>
        )
    } else {
        return (
            <Tooltip title={t('user.login.login')}>
                <Link to={'/login'}>
                    <IconButton>
                        <Login />
                    </IconButton>
                </Link>
            </Tooltip>
        )
    }
}

export default UserWidget
