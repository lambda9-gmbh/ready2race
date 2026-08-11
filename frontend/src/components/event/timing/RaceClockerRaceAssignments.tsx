import {useMemo} from 'react'
import {Autocomplete, Box, Stack, TextField, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {
    getRaceClockerCompetitionAssignments,
    setRaceClockerRaceAssignments,
} from '@api/sdk.gen.ts'
import {CompetitionRaceAssignmentDto, RaceClockerRaceDto} from '@api/types.gen.ts'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import Throbber from '@components/Throbber.tsx'

type Props = {
    eventId: string
    races: RaceClockerRaceDto[]
    /** Wird nach einer Änderung an einem Wettkampf-Override aufgerufen, damit die Abweichungs-Liste daneben nachzieht. */
    onChanged?: () => void
}

/**
 * Die umgedrehte Zuordnung: statt sich durch jeden Wettkampf zu klicken, hakt man am Rennen die
 * Wettkämpfe an (Wunsch vom 10.08.2026). Je Rennen zwei Mehrfachauswahlen — „gilt für Qualifikation"
 * und „gilt für Läufe". Ein Wettkampf, der bei einem Rennen angehakt wird, wandert von einem anderen
 * hierher (der letzte Klick gewinnt); abgewählt fällt er auf „erbt die Voreinstellung" zurück. Die
 * Regel selbst rechnet das Backend.
 *
 * Nach jeder Änderung wird die Liste vom Server neu geladen — nur so bildet sich das „Verschieben"
 * (der Wettkampf verschwindet dann beim anderen Rennen) ohne lokale Sonderlogik ab.
 */
const RaceClockerRaceAssignments = ({eventId, races, onChanged}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {
        data: assignments,
        pending,
        reload,
    } = useFetch(signal => getRaceClockerCompetitionAssignments({signal, path: {eventId}}), {
        onResponse: ({error}) => {
            if (error) feedback.error(t('common.error.unexpected'))
        },
        deps: [eventId],
    })

    const label = (c: CompetitionRaceAssignmentDto) => `${c.identifier} ${c.name}`

    const byRace = useMemo(() => {
        const map = new Map<string, {qualification: CompetitionRaceAssignmentDto[]; rounds: CompetitionRaceAssignmentDto[]}>()
        for (const race of races) map.set(race.id, {qualification: [], rounds: []})
        for (const c of assignments ?? []) {
            if (c.raceQualification && map.has(c.raceQualification))
                map.get(c.raceQualification)!.qualification.push(c)
            if (c.raceRounds && map.has(c.raceRounds)) map.get(c.raceRounds)!.rounds.push(c)
        }
        return map
    }, [assignments, races])

    const save = async (
        raceId: string,
        qualification: CompetitionRaceAssignmentDto[],
        rounds: CompetitionRaceAssignmentDto[],
    ) => {
        const {error} = await setRaceClockerRaceAssignments({
            path: {eventId, raceId},
            body: {
                qualificationCompetitions: qualification.map(c => c.competitionId),
                roundsCompetitions: rounds.map(c => c.competitionId),
            },
        })
        if (error) {
            feedback.error(t('common.error.unexpected'))
        } else {
            feedback.success(t('event.timing.assignments.saved'))
            reload()
            onChanged?.()
        }
    }

    if (pending && !assignments) return <Throbber />
    if (!assignments || assignments.length === 0 || races.length === 0) return null

    // Nur Wettkämpfe mit Qualifikationsrunde in der Qualifikations-Liste — die anderen haben keine.
    const qualificationOptions = assignments.filter(c => c.hasQualificationRound)

    return (
        <Box>
            <Typography variant={'subtitle2'} gutterBottom>
                {t('event.timing.assignments.title')}
            </Typography>
            <Typography variant={'body2'} color={'text.secondary'}>
                {t('event.timing.assignments.hint')}
            </Typography>
            <Stack spacing={3} sx={{mt: 2}}>
                {races.map(race => {
                    const current = byRace.get(race.id) ?? {qualification: [], rounds: []}
                    return (
                        <Box key={race.id}>
                            <Typography variant={'body2'} fontWeight={600} gutterBottom>
                                {race.name}
                            </Typography>
                            <Stack spacing={2}>
                                <Autocomplete
                                    multiple
                                    size={'small'}
                                    options={qualificationOptions}
                                    getOptionLabel={label}
                                    isOptionEqualToValue={(a, b) =>
                                        a.competitionId === b.competitionId
                                    }
                                    value={current.qualification}
                                    onChange={(_, value) => save(race.id, value, current.rounds)}
                                    renderInput={params => (
                                        <TextField
                                            {...params}
                                            label={t('event.timing.assignments.forQualification')}
                                        />
                                    )}
                                />
                                <Autocomplete
                                    multiple
                                    size={'small'}
                                    options={assignments}
                                    getOptionLabel={label}
                                    isOptionEqualToValue={(a, b) =>
                                        a.competitionId === b.competitionId
                                    }
                                    value={current.rounds}
                                    onChange={(_, value) => save(race.id, current.qualification, value)}
                                    renderInput={params => (
                                        <TextField
                                            {...params}
                                            label={t('event.timing.assignments.forRounds')}
                                        />
                                    )}
                                />
                            </Stack>
                        </Box>
                    )
                })}
            </Stack>
        </Box>
    )
}

export default RaceClockerRaceAssignments
