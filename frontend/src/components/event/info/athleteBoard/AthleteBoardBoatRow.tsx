import {ReactNode} from 'react'
import {Box, Stack, Typography} from '@mui/material'
import {MatchTeamLapDto} from '@api/types.gen'
import {compactLapLabel, scaled} from './common'

/**
 * Die Bootszeile der Athleten-Anzeige, geteilt von Lauf- und Ergebnis-Karte.
 *
 * Beide Karten zeigen dasselbe Gerüst — große Zahl links, Namensspalte, rechts der Ausgang — und
 * unterscheiden sich nur im Inhalt: links steht im Lauf die Startnummer, im Ergebnis der Platz;
 * rechts steht im Lauf die Teilzeit, im Ergebnis Zeit, Ausscheidungs- oder Abmeldegrund, und im
 * Block „Nächster Lauf" gar nichts. Bis zum 10.08.2026 lag dieses Gerüst zweimal im Quelltext, und
 * die beiden Fassungen waren bereits auseinandergelaufen (verschiedene Schriftmaxima, verschiedene
 * Breite der rechten Spalte).
 */

/**
 * Was in einer Rasterzeile der Liste steht. `boat` teilt sich die verfügbare Höhe mit den anderen
 * Booten, `heading` (die Überschrift einer Wertungskategorie) nimmt nur, was sie braucht.
 */
export type BoatListRow = 'boat' | 'heading'

interface AthleteBoardBoatListProps {
    /** Die Zeilen in ihrer Reihenfolge. Muss zur Reihenfolge der Kinder passen. */
    rows: BoatListRow[]
    children: ReactNode
}

/**
 * Der Rahmen um die Bootszeilen.
 *
 * Die Zeilen behalten ihre natürliche Höhe und docken oben an — bis zum 10.08.2026
 * teilten sie sich ab `lg` die verbleibende Höhe als `1fr`, und ein Dreierfeld auf einem
 * hohen Bildschirm zerfiel in drei Zeilen mit riesigen Lücken dazwischen (Sichttest am
 * Prod-Abzug). Gegen ein volles Feld steht weiterhin die kleinere Schrift aus
 * `densityScale()`; läuft eine Kachel trotzdem über, schneidet sie ab `lg` unten ab,
 * statt die Zeilen ineinanderzuquetschen.
 *
 * Überschriften und Boote liegen bewusst in **einem** Raster: eine Wertungskategorie in
 * einem eigenen Kasten hätte ihre eigene Höhe und eigene Abstände.
 */
export const AthleteBoardBoatList = ({rows, children}: AthleteBoardBoatListProps) => {
    // `rows` trägt seit dem Wechsel auf natürliche Zeilenhöhen keine Rasterinformation
    // mehr, bleibt aber in der Schnittstelle: die Karten kennen ihre Zeilenarten ohnehin,
    // und ein künftiges Höhen-Feintuning je Zeilenart braucht sie wieder.
    void rows

    return (
        <Box
            sx={{
                minHeight: 0,
                display: 'grid',
                gridAutoRows: 'auto',
                alignContent: 'start',
                overflow: {xs: 'visible', lg: 'hidden'},
            }}>
            {children}
        </Box>
    )
}

/**
 * Die Überschrift eines Wertungskategorie-Abschnitts innerhalb der Liste. Das Polster oben
 * gibt dem Abschnitt Luft zur vorigen Bootszeile (statt des früheren `alignSelf: 'end'`,
 * das noch aus den 1fr-verteilten Zeilen stammte und seit den natürlichen Zeilenhöhen
 * nichts mehr tat) — die Abschnitte wirkten sonst gedrängt (Rückmeldung vom 12.08.2026).
 */
export const AthleteBoardSectionHeading = ({children}: {children: ReactNode}) => (
    <Typography
        noWrap
        sx={{
            fontSize: scaled('0.8rem', '1.3vw', '1.8rem'),
            fontWeight: 700,
            pt: scaled('0.45rem', '0.7vw', '1rem'),
        }}
        color="text.secondary">
        {children}
    </Typography>
)

interface AthleteBoardBoatRowProps {
    /** Die große Zahl links: die Startnummer im Lauf, der Platz im Ergebnis. */
    leadNumber: ReactNode
    /** Position in der Liste; ab der zweiten Zeile trennt eine Linie nach oben. */
    index: number
    /** Die Namensspalte: Vereinskette, darunter Crew bzw. Startnummer. */
    children: ReactNode
    /** Die rechte Spalte. Fehlt im Block „Nächster Lauf", wo es noch nichts zu berichten gibt. */
    trailing?: ReactNode
}

export const AthleteBoardBoatRow = ({
    leadNumber,
    index,
    children,
    trailing,
}: AthleteBoardBoatRowProps) => (
    <Stack
        direction="row"
        alignItems="center"
        gap={1.5}
        sx={{
            minWidth: 0,
            minHeight: 0,
            overflow: {xs: 'visible', lg: 'hidden'},
            // Seit die Zeilen ihre natürliche Höhe haben, trägt das Polster den Abstand,
            // den vorher die 1fr-Verteilung erzeugte.
            py: scaled('0.3rem', '0.5vw', '0.8rem'),
            // Die Trennlinie hängt an der Zeile statt am `divider`-Prop des Stack: dessen
            // eingeschobene Elemente wären eigene Rasterzeilen und würden das Raster
            // der Liste verschieben.
            borderTop: index > 0 ? '1px solid' : 'none',
            borderColor: 'divider',
        }}>
        <Typography
            sx={{
                fontSize: scaled('1.4rem', '2.8vw', '4.5rem'),
                fontWeight: 800,
                lineHeight: 1,
                minWidth: '1.4em',
                textAlign: 'center',
                flexShrink: 0,
            }}>
            {leadNumber}
        </Typography>
        <Box sx={{flex: 1, minWidth: 0}}>{children}</Box>
        {/* Ein langer Ausscheidungsgrund darf den Vereinsnamen nicht überlagern: rechts bündig in
            der eigenen Spalte umbrechen. Ein Drittel reicht für „abgemeldet · Krankheit" und lässt
            dem Namen auch bei vier Spalten eine ganze Zeile. */}
        {trailing && (
            <Stack alignItems="flex-end" sx={{flexShrink: 0, maxWidth: '35%'}}>
                {trailing}
            </Stack>
        )}
    </Stack>
)

interface AthleteBoardBoatStatusProps {
    /** Was die Zeile über den Ausgang sagt: Zeit, Ausscheidungs- oder Abmeldegrund. */
    label: ReactNode
    /** Gedämpft für Boote, die nicht gewertet wurden — ausgeschieden oder abgemeldet. */
    muted?: boolean
}

/** Die Zeile rechts am Boot. */
export const AthleteBoardBoatStatus = ({label, muted = false}: AthleteBoardBoatStatusProps) => (
    <Typography
        sx={{
            fontSize: scaled('0.9rem', '1.5vw', '2.2rem'),
            fontWeight: 600,
            textAlign: 'right',
        }}
        color={muted ? 'text.secondary' : 'text.primary'}>
        {label}
    </Typography>
)

/**
 * Die Rundenzeiten rechts am Boot, direkt unter der großen Gesamt-/Zwischenzeit — geteilt
 * von Lauf-, Ergebnis-Karte und Sprecher-Kachel. Bis zum 12.08.2026 hingen sie als
 * Crew-Subline links unter den Namen und waren aus Anzeigetafel-Entfernung unlesbar.
 *
 * Form: kompakt beschriftete Werte („R1 0:05.0  R2 0:16.9") auf einer Zeile. Jede Zeit
 * steht in einer Zelle fester Mindestbreite mit Tabellenziffern und rechtsbündigem Wert:
 * so teilen sich die Boote eines Laufs dieselben Spaltenkanten (die Runden kommen für
 * alle Boote aus denselben RaceClocker-Marken), und die Zeile bleibt auf der Anzeigetafel
 * tabellarisch ruhig statt je Boot anders zu flattern. Der Schriftgrad liegt bewusst
 * zwischen Crew-Subline und Gesamtzeit und läuft über scaled() mit der Dichte mit.
 *
 * Ohne Rundenzeiten rendert die Komponente nichts — keine leere Zeile, kein Versatz.
 */
export const AthleteBoardLapTimes = ({laps}: {laps?: MatchTeamLapDto[] | null}) =>
    laps && laps.length > 0 ? (
        <Stack
            direction="row"
            justifyContent="flex-end"
            flexWrap="wrap"
            columnGap={scaled('0.5rem', '0.8vw', '1.1rem')}
            sx={{maxWidth: '100%'}}>
            {laps.map((lap, index) => (
                <Stack
                    key={index}
                    direction="row"
                    alignItems="baseline"
                    gap={scaled('0.15rem', '0.25vw', '0.4rem')}>
                    <Typography
                        component="span"
                        sx={{fontSize: scaled('0.65rem', '1.05vw', '1.4rem'), fontWeight: 600}}
                        color="text.secondary">
                        {compactLapLabel(lap.name)}
                    </Typography>
                    <Typography
                        component="span"
                        sx={{
                            fontSize: scaled('0.8rem', '1.35vw', '1.9rem'),
                            fontWeight: 700,
                            lineHeight: 1.2,
                            // Tabellenziffern + feste Mindestbreite: gleiche Spaltenkanten
                            // über alle Boote, solange die Zeiten einstellig in Minuten
                            // bleiben („0:05.0" = 6 Zeichen); längere Zeiten wachsen
                            // rechtsbündig nach links, ohne die Nachbarn zu verschieben.
                            fontVariantNumeric: 'tabular-nums',
                            minWidth: '5.5ch',
                            textAlign: 'right',
                        }}>
                        {lap.timeString}
                    </Typography>
                </Stack>
            ))}
        </Stack>
    ) : null

/** Die kleine Zeile unter der Vereinskette: Crew im Lauf, Startnummer im Ergebnis. */
export const AthleteBoardBoatSubline = ({children}: {children: ReactNode}) => (
    // Einzeilig mit Auslassungspunkten: erst dadurch hat eine Bootszeile eine berechenbare Höhe.
    // Mit umbrechender Crew hinge die Kartenhöhe an der Länge der Nachnamen.
    <Typography noWrap sx={{fontSize: scaled('0.7rem', '1.1vw', '1.5rem')}} color="text.secondary">
        {children}
    </Typography>
)
