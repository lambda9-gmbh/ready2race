import {ReactNode, useState} from 'react'
import {IconButton, Popover, Stack, Tooltip, Typography} from '@mui/material'
import {Settings} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'

/**
 * Gemeinsames Interaktionsmuster für die Einstellungs-Popovers am Zeitplan und am
 * Schiedsrichter-Board: ein Zahnrad in der Kopfzeile öffnet ein Popover, darin ein Stack aus
 * Abschnitten mit Überschrift ([SettingsSection]). Die Abschnitte selbst bringen die jeweiligen
 * Seiten mit — hier steht nur die Hülle, damit beide Seiten gleich aussehen und sich gleich
 * anfühlen.
 *
 * Der Inhalt wird erst beim Öffnen gebaut (MUI-Popover ohne keepMounted): Abschnitte mit eigenem
 * Formularzustand starten dadurch bei jedem Öffnen frisch vom aktuellen Stand.
 */
const SettingsPopover = ({children}: {children: ReactNode}) => {
    const {t} = useTranslation()
    const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null)

    return (
        <>
            <Tooltip title={t('common.settings')}>
                <IconButton
                    size={'small'}
                    onClick={e => setAnchorEl(e.currentTarget)}
                    aria-label={t('common.settings')}>
                    <Settings fontSize={'small'} />
                </IconButton>
            </Tooltip>
            <Popover
                open={anchorEl !== null}
                anchorEl={anchorEl}
                onClose={() => setAnchorEl(null)}
                anchorOrigin={{vertical: 'bottom', horizontal: 'right'}}
                transformOrigin={{vertical: 'top', horizontal: 'right'}}>
                <Stack spacing={3} sx={{p: 2, width: 320, maxWidth: '90vw'}}>
                    {children}
                </Stack>
            </Popover>
        </>
    )
}

/** Ein Abschnitt im Einstellungs-Popover: Überschrift in Kapitälchen, darunter die Regler. */
export const SettingsSection = ({title, children}: {title: string; children: ReactNode}) => (
    <Stack spacing={1}>
        <Typography variant={'overline'} sx={{color: 'text.secondary', lineHeight: 1.5}}>
            {title}
        </Typography>
        {children}
    </Stack>
)

export default SettingsPopover
