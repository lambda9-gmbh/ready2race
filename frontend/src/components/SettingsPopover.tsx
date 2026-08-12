import {ReactNode, useState} from 'react'
import {IconButton, Popover, Stack, Tooltip, Typography} from '@mui/material'
import {Settings} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'

type Props = {
    children: ReactNode
    /** Mindestbreite des Inhalts in Pixeln - auf kleinen Bildschirmen zusätzlich auf 90vw gedeckelt. */
    minWidth?: number
    /** Obergrenze der Breite, als CSS-Ausdruck. */
    maxWidth?: string
}

/**
 * Gemeinsames Interaktionsmuster für die Einstellungs-Popovers am Zeitplan und am
 * Schiedsrichter-Board: ein Zahnrad in der Kopfzeile öffnet ein Popover, darin ein Stack aus
 * Abschnitten mit Überschrift ([SettingsSection]). Die Abschnitte selbst bringen die jeweiligen
 * Seiten mit — hier steht nur die Hülle, damit beide Seiten gleich aussehen und sich gleich
 * anfühlen.
 *
 * Die Breite ist konfigurierbar, damit Abschnitte mit Selects und Beschriftungen (Zeitplan) nicht
 * in einer schmalen Spalte umbrechen: mindestens [minWidth] (die frühere feste 320 war zu schmal),
 * höchstens [maxWidth] - beides über min(..., 90vw) so gedeckelt, dass das Popover auf kleinen
 * Bildschirmen im Fenster bleibt.
 *
 * Der Inhalt wird erst beim Öffnen gebaut (MUI-Popover ohne keepMounted): Abschnitte mit eigenem
 * Formularzustand starten dadurch bei jedem Öffnen frisch vom aktuellen Stand.
 */
const SettingsPopover = ({children, minWidth = 360, maxWidth = 'min(420px, 90vw)'}: Props) => {
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
                {/* min-width gewinnt in CSS gegen max-width, deshalb ist auch sie auf 90vw
                    gedeckelt - sonst schöbe sich das Popover auf schmalen Geräten aus dem Fenster. */}
                <Stack
                    spacing={3}
                    sx={{p: 2.5, minWidth: `min(${minWidth}px, 90vw)`, maxWidth}}>
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
