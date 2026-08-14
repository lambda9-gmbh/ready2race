import {ReactNode} from 'react'
import {Box, Stack, Typography, useTheme} from '@mui/material'
import {solidOr} from './streamDisplay.ts'
import StreamStateBadge from './StreamStateBadge.tsx'

interface StreamPanelShellProps {
    /** React-Key der äußeren Panel-Box — wechselt er (neuer Lauf/neues Ergebnis), montiert
     *  die Box neu und die Slide-in-Animation spielt erneut; bei GLEICHEM Inhalt (nur ein
     *  weiterer Poll-Tick mit denselben Daten) bleibt sie stehen, statt bei jedem Poll
     *  erneut hereinzurutschen. */
    panelKey: string
    /** „LÄUFT" / „ERGEBNIS" / „ALS NÄCHSTES" — fehlt bei Panels ohne Einzel-Lauf-Zustand
     *  (Nächste Läufe), der Titel trägt dort die Aussage allein. */
    stateLabel?: string
    title: string
    roundLine: string | null
    headerTrailing?: ReactNode
    children: ReactNode
}

/**
 * Gemeinsame Hülle der zentrierten TV-Grafik-Panels (Ergebnis, Als Nächstes, Nächste
 * Läufe): vollständig deckendes, abgerundetes Panel mittig im Bild, 78 % Breite und
 * höchstens 80 % Höhe, Akzentbalken oben in Primärfarbe. Die Höhengrenze ist bewusst
 * großzügig: Ein großes Feld muss sonst so stark verkleinert werden (FitToHeight), dass
 * die Zeilen auf einer 720p-Quelle nicht mehr zu lesen sind. Rutscht beim Erscheinen einmalig per
 * translateY-Slide-in herein (CSS-Keyframe, kein Opacity-Fade über der Key-Fläche).
 */
const StreamPanelShell = ({
    panelKey,
    stateLabel,
    title,
    roundLine,
    headerTrailing,
    children,
}: StreamPanelShellProps) => {
    const theme = useTheme()
    return (
        <Box sx={{position: 'absolute', inset: 0, display: 'grid', placeItems: 'center', p: 4}}>
            <Box
                key={panelKey}
                sx={{
                    // Feste Breite statt Inhaltsbreite: Alle zentrierten Panels (Ergebnis,
                    // Als Nächstes, Nächste Läufe) teilen sich dieselbe TV-Grafik-Größe —
                    // so bekommen auch Lang-Vereinsnamen und lange Wettkampfnamen Platz.
                    width: 'min(72rem, 78vw)',
                    maxHeight: '80vh',
                    overflow: 'hidden',
                    borderRadius: 3,
                    display: 'flex',
                    flexDirection: 'column',
                    backgroundColor: solidOr(theme.palette.text.primary, '#1d1d1d'), // dunkles, DECKENDES Panel
                    color: solidOr(theme.palette.background.paper, '#ffffff'),
                    '@keyframes r2rStreamPanelIn': {
                        from: {transform: 'translateY(48px)'},
                        to: {transform: 'translateY(0)'},
                    },
                    animation: 'r2rStreamPanelIn 350ms ease-out',
                }}>
                {/* Akzentbalken oben — eine harte Kante in Primärfarbe, kein Verlauf. */}
                <Box
                    sx={{
                        height: '0.6rem',
                        flexShrink: 0,
                        backgroundColor: solidOr(theme.palette.primary.main, '#1976d2'),
                    }}
                />
                <Stack sx={{p: 4, gap: 2, overflow: 'hidden', minHeight: 0}}>
                    {/* Kopf und Unterzeile dürfen nie schrumpfen: noWrap gibt die min-height
                        frei, und der Spalten-Flex würde sie unter maxHeight sonst zu einem
                        Streifen pressen (dieselbe Falle wie im Lower-Third). */}
                    <Stack direction="row" alignItems="center" gap={2} sx={{flexShrink: 0}}>
                        {stateLabel && <StreamStateBadge label={stateLabel} />}
                        <Typography
                            variant="h3"
                            noWrap
                            sx={{fontWeight: 700, minWidth: 0, flex: 1}}>
                            {title}
                        </Typography>
                        {headerTrailing}
                    </Stack>
                    {roundLine && (
                        <Typography variant="h5" noWrap sx={{fontWeight: 500, flexShrink: 0}}>
                            {roundLine}
                        </Typography>
                    )}
                    {children}
                </Stack>
            </Box>
        </Box>
    )
}

export default StreamPanelShell
