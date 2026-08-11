import {Fragment} from 'react'
import {Box, Chip, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {AthleteBoardResult} from '@api/types.gen'
import AthleteBoardPenaltyNote from './AthleteBoardPenaltyNote'
import AthleteBoardTeamLabel from './AthleteBoardTeamLabel'
import {
    AthleteBoardBoatList,
    AthleteBoardBoatRow,
    AthleteBoardBoatStatus,
    AthleteBoardBoatSubline,
    AthleteBoardSectionHeading,
    BoatListRow,
} from './AthleteBoardBoatRow'
import {formatClockTime, formatPlace, scaled} from './common'
import {groupByRatingCategory, hasRatingCategories} from '@utils/ratingCategorySections.ts'

interface AthleteBoardResultCardProps {
    result: AthleteBoardResult
    // Gefahrene Zeiten rechts am Boot — je Board-Element abschaltbar; Plätze und die
    // Gründe ausgeschiedener/abgemeldeter Boote bleiben stehen, sonst wirkte die Zeile
    // wie ein unerklärtes Loch.
    showTimes?: boolean
}

const AthleteBoardResultCard = ({result, showTimes = true}: AthleteBoardResultCardProps) => {
    const {t} = useTranslation()

    const teams = [...result.teams].sort((a, b) => {
        // Abgemeldete Mannschaften ganz ans Ende: sie sind nicht gefahren und stehen nur noch
        // als Erklärung in der Liste.
        if (a.deregistered !== b.deregistered) return a.deregistered ? 1 : -1
        // Platzierte zuerst, danach die ohne Platz (DNF und Konsorten).
        if (a.place == null && b.place == null) return a.startNumber - b.startNumber
        if (a.place == null) return 1
        if (b.place == null) return -1
        return a.place - b.place
    })

    // Getrennte Abschnitte je Wertungskategorie, in der konfigurierten Reihenfolge - dieselbe
    // Gruppierung wie auf der oeffentlichen Ergebnisseite und im Schiedsrichter-Dashboard. Ein
    // Lauf ohne Kategorien ergibt genau einen namenlosen Abschnitt und bleibt damit wie bisher.
    const sections = groupByRatingCategory(teams, team => team.ratingCategory)
    const showSectionHeadings = hasRatingCategories(sections)

    // Das Zeilenraster der Liste, in derselben Reihenfolge wie die Kinder darunter: je Abschnitt
    // erst die Überschrift (falls es Kategorien gibt), dann seine Boote.
    const rows: BoatListRow[] = sections.flatMap(section => [
        ...(showSectionHeadings ? (['heading'] as BoatListRow[]) : []),
        ...section.entries.map((): BoatListRow => 'boat'),
    ])

    return (
        <>
            <Stack direction="row" justifyContent="space-between" alignItems="flex-start" gap={1}>
                <Box sx={{minWidth: 0}}>
                    <Typography sx={{fontSize: scaled('1rem', '1.8vw', '2.6rem'), fontWeight: 700}}>
                        {result.competitionName}
                    </Typography>
                    <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
                        {result.roundName && (
                            <Typography
                                sx={{fontSize: scaled('0.75rem', '1.2vw', '1.6rem')}}
                                color="text.secondary">
                                {result.roundName}
                            </Typography>
                        )}
                        {result.matchName && result.matchName !== result.roundName && (
                            <Chip label={result.matchName} size="small" variant="outlined" />
                        )}
                    </Stack>
                </Box>
                {/* Geplanter Start groß, darunter der tatsächliche — so ist eine Verschiebung
                    im Ergebnis noch nachvollziehbar, ohne den Zeitplan zu verstecken. */}
                {result.startTime && (
                    <Stack alignItems="flex-end" sx={{flexShrink: 0}}>
                        <Typography
                            sx={{
                                fontSize: scaled('1.1rem', '2.4vw', '3.2rem'),
                                fontWeight: 700,
                                lineHeight: 1.1,
                            }}>
                            {formatClockTime(result.startTime)}
                        </Typography>
                        {result.actualStartTime && (
                            <Typography
                                sx={{fontSize: scaled('0.75rem', '1.3vw', '1.6rem')}}
                                color="text.secondary">
                                {t('event.info.athleteBoard.startedAt', {
                                    time: formatClockTime(result.actualStartTime),
                                })}
                            </Typography>
                        )}
                    </Stack>
                )}
            </Stack>

            <AthleteBoardBoatList rows={rows}>
                {sections.map(section => (
                    // Fragment statt Box: die Zeilen müssen direkte Kinder des Rasters bleiben,
                    // sonst bekäme jeder Abschnitt seine eigene Höhe zurück.
                    <Fragment key={section.category?.id ?? 'none'}>
                        {showSectionHeadings && (
                            <AthleteBoardSectionHeading>
                                {section.category?.name ??
                                    t('event.ratingCategory.withoutCategory')}
                            </AthleteBoardSectionHeading>
                        )}
                        {section.entries.map((team, index) => (
                            <AthleteBoardBoatRow
                                key={`${result.matchId}-${team.startNumber}`}
                                index={index}
                                // Der Platz innerhalb der Wertungskategorie — team.place bleibt der
                                // Platz im Lauf und ist nur seine Grundlage. Als Ordnungszahl
                                // („1." / "1st"), damit die große Zahl nicht wie die Startnummer
                                // der Lauf-Karte liest; die Startnummer steht klein darunter,
                                // damit die Zeile dem Boot zuzuordnen bleibt.
                                leadNumber={
                                    team.categoryPlace != null
                                        ? formatPlace(team.categoryPlace, t)
                                        : '–'
                                }
                                trailing={
                                    // Ohne Zeiten bleibt die rechte Spalte den Booten
                                    // vorbehalten, die eine Erklärung brauchen.
                                    !showTimes && !team.failed && !team.deregistered ? undefined : (
                                        <>
                                            <AthleteBoardBoatStatus
                                                muted={team.failed || team.deregistered}
                                                label={
                                                    team.deregistered
                                                        ? [
                                                              t(
                                                                  'event.info.athleteBoard.deregistered',
                                                              ),
                                                              team.deregisteredReason,
                                                          ]
                                                              .filter(Boolean)
                                                              .join(' · ')
                                                        : team.failed
                                                          ? (team.failedReason ??
                                                            t('event.info.athleteBoard.failed'))
                                                          : (team.timeString ?? '')
                                                }
                                            />
                                            {!team.deregistered && showTimes && (
                                                <AthleteBoardPenaltyNote
                                                    penaltySeconds={team.penaltySeconds}
                                                    penaltyNote={team.penaltyNote}
                                                />
                                            )}
                                        </>
                                    )
                                }>
                                <AthleteBoardTeamLabel
                                    team={team}
                                    color={team.deregistered ? 'text.secondary' : 'text.primary'}
                                />
                                <AthleteBoardBoatSubline>
                                    {t('event.info.athleteBoard.startNumber')} {team.startNumber}
                                </AthleteBoardBoatSubline>
                            </AthleteBoardBoatRow>
                        ))}
                    </Fragment>
                ))}
            </AthleteBoardBoatList>
        </>
    )
}

export default AthleteBoardResultCard
