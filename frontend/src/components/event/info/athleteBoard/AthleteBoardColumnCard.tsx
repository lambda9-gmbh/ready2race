import {ReactNode} from 'react'
import {Box, Card, Typography} from '@mui/material'
import {scaled} from './common'

interface AthleteBoardColumnCardProps {
    /** Status-Überschrift der Spalte, z.B. "Aktueller Lauf". */
    title: string
    /** Steht anstelle des Inhalts, solange die Spalte nichts zu zeigen hat. */
    emptyText: string
    /**
     * Genau zwei Elemente: Kopfblock und Bootsliste. Der Rahmen stellt sie in ein
     * `auto 1fr`-Raster, damit die Liste den ganzen Rest der Höhe bekommt und ihre Zeilen sich
     * darin teilen können.
     */
    children?: ReactNode
    /**
     * Für das Board-System (Kachel im freien Raster): Der Rahmen klemmt seinen Inhalt NICHT ab,
     * sondern lässt ihn auf seine natürliche Höhe wachsen — die Kachel selbst scrollt dann
     * (BoardMatchSlotElement, `overflow: auto`). Ohne das blieb in kleinen Kacheln die Crew-Zeile
     * unter der Vereinskette am unteren Rand hängen und wurde vom `overflow: hidden` der Bühne
     * abgeschnitten (Rückmeldung vom 11.08.2026). Die klassische Bühne (Athleten-Board mit den drei
     * Statusspalten) lässt das Flag aus und behält ihr Scroll-Verbot samt Dichte-Skalierung.
     */
    allowOverflow?: boolean
}

/**
 * Der Rahmen einer Spalte auf der Bühne: volle Höhe, Statusüberschrift oben, Inhalt darunter.
 *
 * Die Überschrift sitzt bewusst im Rahmen und nicht im Inhalt — die Statusspalten stehen auch
 * leer, und ein fest montierter Bildschirm soll seine Struktur nicht wechseln, nur weil gerade
 * nichts fährt.
 */
const AthleteBoardColumnCard = ({
    title,
    emptyText,
    children,
    allowOverflow = false,
}: AthleteBoardColumnCardProps) => (
    <Card
        variant="outlined"
        sx={{
            // Ab lg gilt das Scroll-Verbot der Bühne, darunter bleibt die Karte in ihrer
            // natürlichen Höhe und darf wie früher gestapelt scrollen. Im Board-System
            // (allowOverflow) wächst die Karte dagegen auf ihre natürliche Höhe und die Kachel
            // scrollt sie — sonst klemmt das Scroll-Verbot der Bühne die Crew-Zeile ab.
            height: allowOverflow ? 'auto' : {xs: 'auto', lg: '100%'},
            minHeight: allowOverflow ? '100%' : 0,
            overflow: allowOverflow ? 'visible' : {xs: 'visible', lg: 'hidden'},
            display: 'grid',
            gridTemplateRows: allowOverflow ? 'auto auto' : {xs: 'auto auto', lg: 'auto 1fr'},
            // Oben andocken statt vertikal zentrieren: ohne das streckt align-content
            // (Default stretch) die beiden auto-Zeilen über die volle Kachelhöhe, und
            // eine halb gefüllte Karte hängt mit großem Leerraum in der Mitte
            // (Nutzer-Rückmeldung vom 12.08.2026 — „kann ruhig oben sein"). Gilt für
            // Lauf- UND Ergebnis-Karte, die beide in diesem Rahmen stehen.
            alignContent: 'start',
            rowGap: scaled('0.25rem', '0.4vw', '0.6rem'),
            p: scaled('0.5rem', '0.9vw', '1.25rem'),
        }}>
        <Typography
            sx={{
                fontSize: scaled('0.75rem', '1vw', '1.8rem'),
                fontWeight: 700,
                textTransform: 'uppercase',
                letterSpacing: '0.04em',
                lineHeight: 1.2,
            }}
            color="text.secondary">
            {title}
        </Typography>
        <Box
            sx={{
                minHeight: 0,
                display: 'grid',
                gridTemplateRows: allowOverflow
                    ? 'auto auto'
                    : {xs: 'auto auto', lg: 'auto minmax(0, 1fr)'},
                rowGap: scaled('0.35rem', '0.6vw', '0.9rem'),
            }}>
            {children ?? (
                <Typography
                    sx={{fontSize: scaled('0.85rem', '1.2vw', '1.8rem')}}
                    color="text.secondary">
                    {emptyText}
                </Typography>
            )}
        </Box>
    </Card>
)

export default AthleteBoardColumnCard
