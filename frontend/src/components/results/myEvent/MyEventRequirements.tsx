import {
    Alert,
    AlertTitle,
    Box,
    List,
    ListItem,
    ListItemIcon,
    ListItemText,
    Stack,
    Typography,
} from '@mui/material'
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline'
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked'
import {format} from 'date-fns'
import {useTranslation} from 'react-i18next'
import type {TFunction} from 'i18next'
import {MyEventRequirementDto} from '@api/types.gen.ts'
import {formatClockTime} from '@components/event/info/athleteBoard/common.ts'
import {openRequirements, requirementWindow} from './myEventOrder.ts'

type MyEventRequirementsProps = {
    requirements: MyEventRequirementDto[]
    // "banner": das Band ganz oben, das nur die offenen Pflichtbedingungen nennt.
    // "list": die vollständige Liste weiter unten, erledigte eingeschlossen.
    variant: 'banner' | 'list'
}

/**
 * Die Zeile mit dem Erledigungsfenster („Erledigen zwischen 06:30 und 07:30"). Ob sie
 * erscheint, entscheidet [requirementWindow]; hier steht nur noch die Wortwahl für die
 * drei Formen des Fensters (beidseitig, nur ab, nur bis).
 */
const windowLine = (r: MyEventRequirementDto, t: TFunction): string | null => {
    const window = requirementWindow(r)
    if (!window) {
        return null
    }
    if (window.from && window.until) {
        return t('myEvent.requirementCheckBetween', {
            from: formatClockTime(window.from),
            until: formatClockTime(window.until),
        })
    }
    if (window.from) {
        return t('myEvent.requirementCheckFrom', {from: formatClockTime(window.from)})
    }
    return t('myEvent.requirementCheckUntil', {until: formatClockTime(window.until!)})
}

/**
 * Bedingungen der eigenen Teilnahme (Pass, Beitrag, Nachweis …), in zwei Ausprägungen aus
 * einer Datei — beide lesen dieselben Daten, und getrennte Dateien wären zwei Orte, an denen
 * derselbe Zustand unterschiedlich benannt werden könnte.
 *
 * Gezeigt wird ausschließlich `publicNote`, der athletengerechte öffentliche Text. Die
 * interne `description` liefert der Server seit dem 11.08.2026 gar nicht mehr aus.
 */
export const MyEventRequirements = ({requirements, variant}: MyEventRequirementsProps) => {
    const {t} = useTranslation()

    if (variant === 'banner') {
        const open = openRequirements(requirements)
        // Kein Band ohne offene Pflicht: ein Band, das nur "alles gut" sagt, würde am Renntag
        // oben Platz kosten und beim nächsten Mal übersehen werden.
        if (open.length === 0) {
            return null
        }
        // Der generische Meldestellen-Hinweis ist der Fallback für Bedingungen ohne eigenen
        // Text. Sobald jede offene Bedingung ihren publicNote trägt, sagt er nichts mehr,
        // was nicht schon konkreter darüber steht.
        const showOfficeHint = open.some(r => !r.publicNote)
        // Kein eigener Abstand nach unten: den trägt bereits das gap der umschließenden Stack
        // im Panel, sonst steht das Band doppelt so weit vom nächsten Block ab.
        return (
            <Alert severity="warning">
                <AlertTitle>{t('myEvent.requirementsOpen')}</AlertTitle>
                <Stack component="ul" sx={{pl: 2.5, my: 0.5}} spacing={0.25}>
                    {open.map(r => {
                        const window = windowLine(r, t)
                        return (
                            <Box component="li" key={r.id}>
                                <Typography sx={{fontWeight: 600}}>{r.name}</Typography>
                                {r.publicNote && (
                                    <Typography variant="body2">{r.publicNote}</Typography>
                                )}
                                {window && <Typography variant="body2">{window}</Typography>}
                            </Box>
                        )
                    })}
                </Stack>
                {showOfficeHint && (
                    <Typography variant="body2">{t('myEvent.requirementsOfficeHint')}</Typography>
                )}
            </Alert>
        )
    }

    if (requirements.length === 0) {
        return null
    }

    const allDone = openRequirements(requirements).length === 0

    return (
        <Box>
            {allDone && (
                <Typography variant="body2" color="success.main" sx={{mb: 0.5}}>
                    {t('myEvent.requirementsAllDone')}
                </Typography>
            )}
            <List dense disablePadding>
                {requirements.map(r => {
                    const window = windowLine(r, t)
                    return (
                        <ListItem key={r.id} disableGutters sx={{alignItems: 'flex-start'}}>
                            <ListItemIcon sx={{minWidth: 36, mt: 0.5}}>
                                {r.fulfilled ? (
                                    <CheckCircleOutlineIcon color="success" fontSize="small" />
                                ) : (
                                    <RadioButtonUncheckedIcon
                                        color={r.optional ? 'disabled' : 'warning'}
                                        fontSize="small"
                                    />
                                )}
                            </ListItemIcon>
                            <ListItemText
                                primary={
                                    <Stack
                                        direction="row"
                                        gap={1}
                                        alignItems="baseline"
                                        flexWrap="wrap">
                                        <Typography sx={{fontWeight: r.fulfilled ? 400 : 600}}>
                                            {r.name}
                                        </Typography>
                                        {/* Eine freiwillige Bedingung bleibt auch offen harmlos — der
                                            Zusatz nimmt ihr die Dringlichkeit, die das Symbol suggeriert. */}
                                        {r.optional && (
                                            <Typography variant="caption" color="text.secondary">
                                                {t('myEvent.requirementOptional')}
                                            </Typography>
                                        )}
                                        <Typography
                                            variant="caption"
                                            color={r.fulfilled ? 'success.main' : 'text.secondary'}>
                                            {r.fulfilled
                                                ? t('myEvent.requirementFulfilled')
                                                : t('myEvent.requirementOpen')}
                                        </Typography>
                                    </Stack>
                                }
                                secondary={
                                    r.publicNote || window || (r.scopes ?? []).length > 0 ? (
                                        <>
                                            {/* Bei einer Bedingung je Wettkampf oder Tag sagt
                                                "offen" allein zu wenig: Wer für einen von zwei
                                                Wettkämpfen gewogen ist, sucht sonst den Fehler.
                                                Deshalb je Rahmen eine Zeile. */}
                                            {(r.scopes ?? []).map((scope, index) => (
                                                <Typography
                                                    key={`${scope.competitionName ?? ''}-${scope.eventDayDate ?? ''}-${index}`}
                                                    variant="body2"
                                                    color={
                                                        scope.fulfilled
                                                            ? 'success.main'
                                                            : 'text.secondary'
                                                    }>
                                                    {[
                                                        scope.competitionName,
                                                        scope.eventDayDate
                                                            ? format(
                                                                  new Date(scope.eventDayDate),
                                                                  t('format.date'),
                                                              )
                                                            : null,
                                                    ]
                                                        .filter(Boolean)
                                                        .join(' · ')}
                                                    {' — '}
                                                    {scope.fulfilled
                                                        ? t('myEvent.requirementFulfilled')
                                                        : t('myEvent.requirementOpen')}
                                                </Typography>
                                            ))}
                                            {r.publicNote && (
                                                <Typography
                                                    variant="body2"
                                                    color="text.secondary">
                                                    {r.publicNote}
                                                </Typography>
                                            )}
                                            {window && (
                                                <Typography
                                                    variant="body2"
                                                    color="text.secondary">
                                                    {window}
                                                </Typography>
                                            )}
                                        </>
                                    ) : undefined
                                }
                                // Zwei Zeilen im Sekundärtext: ohne dies packte MUI sie in ein
                                // <p> und der Browser meckerte über <p> in <p>.
                                secondaryTypographyProps={{component: 'div'}}
                            />
                        </ListItem>
                    )
                })}
            </List>
        </Box>
    )
}
