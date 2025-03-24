import {useFieldArray, UseFormReturn} from 'react-hook-form-mui'
import {Box, Button, Stack, Typography} from '@mui/material'
import {CompetitionSetupForm} from '@components/event/competition/setup/CompetitionSetup.tsx'
import CompetitionSetupMatch from '@components/event/competition/setup/CompetitionSetupMatch.tsx'

type Props = {
    round: {index: number; id: string}
    formContext: UseFormReturn<CompetitionSetupForm>
    removeRound: (index: number) => void
    teamCounts: {thisRound: number; nextRound: number}
    getRoundTeamCountWithoutMatch: (ignoreMatchIndex: number) => number
}
const CompetitionSetupRound = ({round, formContext, removeRound, teamCounts, ...props}: Props) => {
    const defaultTeamSize = 2

    //const matchesPath = ('rounds[' + roundIndex + '].matches') as `rounds.${number}.matches`

    const {
        fields: matchFields,
        append: appendMatch,
        remove: removeMatch,
    } = useFieldArray({
        control: formContext.control,
        name: `rounds.${round.index}.matches`,
    })

    const watchMatchFields = formContext.watch(`rounds.${round.index}.matches`)

    const takenOutcomes =
        watchMatchFields
            ?.map(v => v.outcomes)
            .flat()
            .map(v => v?.outcome ?? 0) ?? []

    function findLowestMissingOutcome(yetUnregisteredOutcomes: number[]): number {
        const set = new Set([...takenOutcomes, ...yetUnregisteredOutcomes])
        let i = 1
        while (set.has(i)) {
            i++
        }
        return i
    }

    const appendMatchOutcomes: {outcome: number}[] = []
    for (let i = 0; i < defaultTeamSize; i++) {
        appendMatchOutcomes.push({
            outcome: findLowestMissingOutcome(appendMatchOutcomes.map(v => v.outcome)),
        })
    }

    return (
        <>
            <Box sx={{display: 'flex', justifyContent: 'center'}}>
                <Typography>Max Teams: {teamCounts.thisRound}</Typography>
            </Box>
            <Stack
                direction="row"
                spacing={2}
                justifyContent="space-between"
                sx={{alignItems: 'center'}}>
                <Stack
                    direction="row"
                    spacing={2}
                    alignItems={'center'}
                    sx={{border: 1, borderColor: 'blue', p: 2}}>
                    {matchFields.map((matchField, matchIndex) => (
                        <CompetitionSetupMatch
                            key={matchField.id}
                            formContext={formContext}
                            round={round}
                            match={{index: matchIndex, id: matchField.id}}
                            removeMatch={() => removeMatch(matchIndex)}
                            findLowestMissingOutcome={findLowestMissingOutcome}
                            teamCounts={{
                                thisRoundWithoutThis:
                                    props.getRoundTeamCountWithoutMatch(matchIndex),
                                nextRound: teamCounts.nextRound,
                            }}
                        />
                    ))}
                    <Box>
                        <Button
                            variant="outlined"
                            onClick={() => {
                                appendMatch({
                                    duplicatable: false,
                                    weighting: matchFields.length + 1,
                                    teams: `${defaultTeamSize}`,
                                    name: '',
                                    outcomes: appendMatchOutcomes,
                                })
                            }}
                            sx={{width: 1}}>
                            Add Match
                        </Button>
                    </Box>
                </Stack>
                <Box>
                    <Button variant="outlined" onClick={() => removeRound(round.index)}>
                        Remove Round
                    </Button>
                </Box>
            </Stack>
        </>
    )
}

export default CompetitionSetupRound
