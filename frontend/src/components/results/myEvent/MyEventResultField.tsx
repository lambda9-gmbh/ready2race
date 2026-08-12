import {useEffect, useState} from 'react'
import {Alert, Box, Chip, CircularProgress, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {getLatestMatchResults} from '@api/sdk.gen.ts'
import {LatestMatchResultInfo, MyEventResultDto} from '@api/types.gen.ts'
import AthleteBoardPenaltyNote from '@components/event/info/athleteBoard/AthleteBoardPenaltyNote.tsx'
import {hasRatingCategories} from '@utils/ratingCategorySections.ts'
import {formatPlaceOrdinal} from '@utils/placeOrdinal'
import {boatLabel, displayPlace, fieldSections, FieldTeam} from './myEventField.ts'

type MyEventResultFieldProps = {
    eventId: string
    result: MyEventResultDto
}

type FieldState =
    | {status: 'loading' | 'empty' | 'error'}
    | {status: 'loaded'; match: LatestMatchResultInfo}

/**
 * Eine Zeile des Feldes: Platz, Boot, Zeit — und beim eigenen Boot der Balken samt
 * „Dein Boot", in derselben Zeichensprache wie die markierte Zeile im Tagesplan.
 */
const FieldRow = ({team}: {team: FieldTeam}) => {
    const {t} = useTranslation()
    const place = displayPlace(team)

    return (
        <Stack
            direction="row"
            gap={1.5}
            alignItems="center"
            sx={{
                py: 0.75,
                ...(team.own
                    ? {
                          borderLeft: 3,
                          borderColor: 'primary.main',
                          pl: 1,
                          bgcolor: 'action.hover',
                      }
                    : {borderLeft: 3, borderColor: 'transparent', pl: 1}),
            }}>
            <Typography
                sx={{
                    fontWeight: team.own ? 800 : 600,
                    minWidth: '1.8em',
                    textAlign: 'center',
                    flexShrink: 0,
                }}
                color={place != null ? 'text.primary' : 'text.secondary'}>
                {place != null ? formatPlaceOrdinal(place) : '–'}
            </Typography>
            <Box sx={{flex: 1, minWidth: 0}}>
                <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
                    <Typography sx={{fontWeight: team.own ? 700 : 400}}>
                        {boatLabel(team)}
                    </Typography>
                    {team.own && (
                        <Chip size="small" color="primary" label={t('myEvent.fieldOwnBoat')} />
                    )}
                </Stack>
                {team.teamName && (
                    <Typography variant="body2" color="text.secondary">
                        {team.teamName}
                    </Typography>
                )}
            </Box>
            <Stack alignItems="flex-end" sx={{flexShrink: 0, maxWidth: '45%'}}>
                <Typography
                    variant="body2"
                    sx={{fontWeight: team.own ? 700 : 400, textAlign: 'right'}}
                    color={team.failed || team.deregistered ? 'text.secondary' : 'text.primary'}>
                    {team.deregistered
                        ? [t('event.info.athleteBoard.deregistered'), team.deregisteredReason]
                              .filter(Boolean)
                              .join(' · ')
                        : team.failed
                          ? (team.failedReason ?? t('event.info.athleteBoard.failed'))
                          : (team.timeString ?? '')}
                </Typography>
                {!team.deregistered && (
                    <AthleteBoardPenaltyNote
                        penaltySeconds={team.penaltySeconds}
                        penaltyNote={team.penaltyNote}
                    />
                )}
            </Stack>
        </Stack>
    )
}

/**
 * Das komplette Feld eines eigenen Ergebnisses, nachgeladen beim Aufklappen — EIN Abruf, kein
 * Takt: das Ergebnis ist gelaufen, es ändert sich nicht mehr im Sekundenrhythmus.
 *
 * Geladen wird über denselben öffentlichen Endpoint wie die Ergebnisseite
 * (`/latest-match-results`, auf diesen Lauf eingegrenzt). Damit gilt die Sichtbarkeitsregel
 * der Veranstaltung automatisch: was die Ergebnisseite nicht zeigt, kommt hier als leere
 * Liste an und wird als „noch nicht freigegeben" erklärt.
 */
export const MyEventResultField = ({eventId, result}: MyEventResultFieldProps) => {
    const {t} = useTranslation()
    const [state, setState] = useState<FieldState>({status: 'loading'})

    useEffect(() => {
        const controller = new AbortController()
        getLatestMatchResults({
            signal: controller.signal,
            path: {eventId},
            query: {limit: 1, matchId: result.matchId},
        })
            .then(({data, error}) => {
                if (error) {
                    setState({status: 'error'})
                } else if (!data || data.length === 0) {
                    setState({status: 'empty'})
                } else {
                    setState({status: 'loaded', match: data[0]})
                }
            })
            .catch(() => {
                if (!controller.signal.aborted) {
                    setState({status: 'error'})
                }
            })
        return () => controller.abort()
    }, [eventId, result.matchId])

    if (state.status !== 'loaded') {
        if (state.status === 'loading') {
            return (
                <Box sx={{py: 1, display: 'flex', justifyContent: 'center'}}>
                    <CircularProgress size={20} />
                </Box>
            )
        }
        if (state.status === 'error') {
            return (
                <Alert severity="error" sx={{my: 0.5}}>
                    {t('myEvent.fieldLoadError')}
                </Alert>
            )
        }
        // Der Lauf ist auf dem Telefon schon als Ergebnis eingeordnet, die Freigabe der
        // Ergebnisseite aber noch nicht da (oder gerade zurückgenommen) — ehrlicher als ein
        // leerer Kasten.
        return (
            <Alert severity="info" sx={{my: 0.5}}>
                {t('myEvent.fieldNotReleased')}
            </Alert>
        )
    }

    const sections = fieldSections(state.match, result.teamId)
    const showHeadings = hasRatingCategories(sections)

    return (
        <Box sx={{pb: 1}}>
            {sections.map(section => (
                <Box key={section.category?.id ?? 'none'}>
                    {showHeadings && (
                        <Typography
                            variant="caption"
                            color="text.secondary"
                            sx={{fontWeight: 700}}>
                            {section.category?.name ?? t('event.ratingCategory.withoutCategory')}
                        </Typography>
                    )}
                    {section.entries.map(team => (
                        <FieldRow key={team.teamId} team={team} />
                    ))}
                </Box>
            ))}
        </Box>
    )
}
