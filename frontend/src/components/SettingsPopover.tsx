import {ReactNode, useRef, useState} from 'react'
import {IconButton, Popover, Stack, Tooltip, Typography} from '@mui/material'
import {Settings} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'

type Props = {
    children: ReactNode
    /** Mindestbreite des Inhalts in Pixeln - auf kleinen Bildschirmen zusätzlich auf 90vw gedeckelt. */
    minWidth?: number
    /** Obergrenze der Breite, als CSS-Ausdruck. */
    maxWidth?: string
    /**
     * Optional von außen gesteuert: Ist [open] gesetzt, hält der Aufrufer den Zustand und das
     * Popover meldet Öffnen/Schließen über [onOpenChange] — so kann neben dem Zahnrad auch ein
     * zweiter Auslöser (der Filter-Chip des Schiedsrichter-Boards) dasselbe Popover öffnen.
     * Ohne [open] verhält sich alles wie bisher: der Zustand liegt hier drin.
     */
    open?: boolean
    onOpenChange?: (open: boolean) => void
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
const SettingsPopover = ({
    children,
    minWidth = 360,
    maxWidth = 'min(420px, 90vw)',
    open,
    onOpenChange,
}: Props) => {
    const {t} = useTranslation()
    const [innerOpen, setInnerOpen] = useState(false)
    // Anker ist immer das Zahnrad — auch wenn ein externer Auslöser öffnet, gehört das Popover
    // optisch an die Stelle, an der es sich sonst öffnet.
    const buttonRef = useRef<HTMLButtonElement | null>(null)

    const isOpen = open ?? innerOpen
    const setOpen = (next: boolean) => {
        setInnerOpen(next)
        onOpenChange?.(next)
    }

    return (
        <>
            <Tooltip title={t('common.settings')}>
                <IconButton
                    size={'small'}
                    ref={buttonRef}
                    onClick={() => setOpen(true)}
                    aria-label={t('common.settings')}>
                    <Settings fontSize={'small'} />
                </IconButton>
            </Tooltip>
            <Popover
                open={isOpen && buttonRef.current !== null}
                anchorEl={buttonRef.current}
                onClose={() => setOpen(false)}
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
