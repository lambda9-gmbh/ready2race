import React, {useState} from 'react'
import {useTranslation} from 'react-i18next'
import {IconButton, Menu, MenuItem, Tooltip, Typography} from '@mui/material'
import {Language} from '@mui/icons-material'
import {languageNames, LANGUAGES} from '@i18n/config.ts'
import {useUser} from '@contexts/user/UserContext.ts'

const LanguageWidget = () => {
    const {t} = useTranslation()
    const {language, changeLanguage} = useUser()

    const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null)

    const open = Boolean(anchorEl)

    const handleClick = (event: React.MouseEvent<HTMLElement>) => {
        setAnchorEl(event.currentTarget)
    }
    const handleClose = () => {
        setAnchorEl(null)
    }

    return (
        <>
            <Tooltip title={t('common.language.menu')}>
                <IconButton onClick={handleClick}>
                    <Language />
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
                {LANGUAGES.map(lng => (
                    <MenuItem
                        key={lng}
                        onClick={() => changeLanguage(lng)}
                        selected={language === lng}>
                        <Typography>{languageNames[lng]}</Typography>
                    </MenuItem>
                ))}
            </Menu>
        </>
    )
}

export default LanguageWidget
