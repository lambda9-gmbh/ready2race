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
 * Wettkämpfe an (Wunsch vom 10.08.2026). Je Rennen EINE Mehrfachauswahl — ein Wettkampf hat genau
 * ein Rennen für alle seine Runden (seit dem 11.08.2026, RaceClocker kennt keine Startarten mehr).
 * Ein Wettkampf, der bei einem Rennen angehakt wird, wandert von einem anderen hierher (der letzte
 * Klick gewinnt); abgewählt hat er kein Rennen mehr. Die Regel selbst rechnet das Backend.
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
        const map = new Map<string, CompetitionRaceAssignmentDto[]>()
        for (const race of races) map.set(race.id, [])
        for (const c of assignments ?? []) {
            if (c.race && map.has(c.race)) map.get(c.race)!.push(c)
        }
        return map
    }, [assignments, races])

    const save = async (raceId: string, competitions: CompetitionRaceAssignmentDto[]) => {
        const {error} = await setRaceClockerRaceAssignments({
            path: {eventId, raceId},
            body: {
                competitions: competitions.map(c => c.competitionId),
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

    return (
        <Box>
            <Typography variant={'subtitle2'} gutterBottom>
                {t('event.timing.assignments.title')}
            </Typography>
            <Typography variant={'body2'} color={'text.secondary'}>
                {t('event.timing.assignments.hint')}
            </Typography>
            <Stack spacing={3} sx={{mt: 2}}>
                {races.map(race => (
                    <Box key={race.id}>
                        <Typography variant={'body2'} fontWeight={600} gutterBottom>
                            {race.name}
                        </Typography>
                        <Autocomplete
                            multiple
                            size={'small'}
                            options={assignments}
                            getOptionLabel={label}
                            isOptionEqualToValue={(a, b) => a.competitionId === b.competitionId}
                            value={byRace.get(race.id) ?? []}
                            onChange={(_, value) => save(race.id, value)}
                            renderInput={params => (
                                <TextField
                                    {...params}
                                    label={t('event.timing.assignments.competitions')}
                                />
                            )}
                        />
                    </Box>
                ))}
            </Stack>
        </Box>
    )
}

export default RaceClockerRaceAssignments
