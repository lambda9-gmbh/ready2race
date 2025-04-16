import {FormEvent, PropsWithChildren, RefObject, useEffect, useState} from 'react'
import {Box, Button, Dialog, DialogActions} from '@mui/material'
import {CheckboxElement, FormContainer, useForm} from 'react-hook-form-mui'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {useTranslation} from 'react-i18next'
import {createPortal} from 'react-dom'
import {CompetitionSetupForm, FormSetupRound, getWeightings} from '@components/event/competition/setup/common.ts'

type Form = {
    teams: number
    matchForPlaceThree: boolean
    replaceAll: boolean
    // todo: loser tree
}

type Props = {
    resetSetupForm: (formData: CompetitionSetupForm) => void
    currentFormData: FormSetupRound[]
    portalContainer: RefObject<HTMLDivElement>
}
const CompetitionSetupTreeHelper = ({resetSetupForm, currentFormData, portalContainer}: Props) => {
    const {t} = useTranslation()

    const [tournamentTreeDialogOpen, setTournamentTreeDialogOpen] = useState(false)

    const openTournamentTreeDialog = () => {
        setTournamentTreeDialogOpen(true)
    }

    const closeTournamentTreeDialog = () => {
        setTournamentTreeDialogOpen(false)
    }

    const formContext = useForm<Form>({
        values: {teams: 8, matchForPlaceThree: true, replaceAll: true},
    })

    const onSubmitWithoutPropagation = (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault()
        event.stopPropagation()
        formContext.handleSubmit((data: Form) => updateSetupForm(data))(event)
    }

    const updateSetupForm = ({teams, matchForPlaceThree, replaceAll}: Form) => {
        const roundCount = Math.ceil(Math.log(teams / 2) / Math.log(2)) + 1

        type TreeHelperMatch = {
            name: string
            teams: number
            position: number
        }
        type TreeHelperRound = {
            matches: TreeHelperMatch[]
            name: string
        }
        const rounds: TreeHelperRound[] = []

        const teamsMatchesDiff = Math.pow(2, roundCount) - teams

        for (let r = 0; r < roundCount; r++) {
            const matchesTeams: number[] = []

            // Normally MatchCount is two to the power of r except in the final round, if third place match is selected
            const matchCount = matchForPlaceThree && r === 0 ? 2 : Math.pow(2, r)

            for (let m = 0; m < matchCount; m++) {
                // If this is the first round and the participants are not fitting into default "two to the power of X(Round count)"
                // then some matches will have only a teams of 1 (Starting with Match Weighting 1 going upwards)
                const teams = r === roundCount - 1 && m <= teamsMatchesDiff - 1 ? 2 - 1 : 2

                matchesTeams.push(teams)
            }

            // Sort the matches so that the name and execution order match the displayed matches but the teams are assigned
            // based on the match weighting (which is represented by the matches list index) e.g. 1,2,3,4,5,6,7,8 -> 1,8,5,4,3,6,7,2
            const matchesInBracketOrder: {
                name: string
                position: number
            }[] = getWeightings(matchesTeams.length).map(w => {
                return {
                    name: `R${r}-${w}`,
                    position: w,
                }
            })

            const matches: TreeHelperMatch[] = matchesTeams.map((v, index) => ({
                teams: v,
                name: matchesInBracketOrder[index]?.name ?? '',
                position: matchesInBracketOrder[index]?.position ?? 0,
            }))

            // todo: other name if first round does not match the numbers (e.g. 20 teams in round of 32)
            const newRound: TreeHelperRound = {
                matches: matches,
                name:
                    r === 0
                        ? t('event.competition.setup.round.finals.final')
                        : r === 1
                          ? t('event.competition.setup.round.finals.semifinal')
                          : r === 2
                            ? t('event.competition.setup.round.finals.quarterfinal')
                            : r === 3
                              ? t('event.competition.setup.round.finals.roundOf16')
                              : r === 4
                                ? t('event.competition.setup.round.finals.roundOf32')
                                : t('event.competition.setup.round.round') + ` ${roundCount - r}`,
            }
            rounds.push(newRound)
        }

        const getPlacesForRound = (roundIndex: number) => {
            const invertedIndex = roundCount - roundIndex

            if (matchForPlaceThree && roundIndex !== 0) {
                if (roundIndex === roundCount - 1) {
                    // If there is a match for place 3 the places need to be modified so that the loser of the main final does not get place 4 (because of default seeding)
                    return [
                        {roundOutcome: 1, place: 1},
                        {roundOutcome: 2, place: 3},
                        {roundOutcome: 3, place: 4},
                        {roundOutcome: 4, place: 2},
                    ]
                }

                if (roundIndex === roundCount - 2) {
                    // If this is the semi-final and there is a match for place three there are no places
                    return []
                }
            }

            const newPlaces = []

            // The amount of places that need to be provided by this round
            const placeCount =
                roundCount !== 1
                    ? roundIndex !== 0
                        ? Math.pow(2, invertedIndex) - Math.pow(2, invertedIndex - 1)
                        : teams - Math.pow(2, invertedIndex - 1)
                    : teams

            // This provides the same places for everyone leaving in this round (e.g. round of 16: 9-16 are all place 9)
            for (let i = 0; i < placeCount; i++) {
                newPlaces.push({
                    roundOutcome: Math.pow(2, invertedIndex - 1) + 1 + i,
                    place: Math.pow(2, invertedIndex - 1) + 1,
                })
            }

            return newPlaces
        }


        const tree: CompetitionSetupForm = {
            rounds: rounds
                .map((r, index) => ({round: r, index: index}))
                .sort((a, b) => b.index - a.index)
                .map(({round}, roundIndex) => ({
                    name: round.name,
                    required: roundIndex === roundCount - 1, // Only last round is required
                    matches: round.matches.map((match, matchIndex) => ({
                        duplicatable: false,
                        weighting: matchIndex + 1,
                        teams: match.teams.toString(),
                        name: match.name,
                        participants:
                            matchForPlaceThree && roundIndex === roundCount - 1
                                ? matchIndex === 0
                                    ? [{seed: 1}, {seed: 2}]
                                    : [{seed: 3}, {seed: 4}]
                                : [],
                        position: match.position,
                    })),
                    groups: [],
                    statisticEvaluations: undefined,
                    useDefaultSeeding: matchForPlaceThree ? roundIndex !== roundCount - 1 : true,
                    isGroupRound: false,
                    useStartTimeOffsets: false,
                    places: getPlacesForRound(roundIndex),
                })),
        }

        if (replaceAll) {
            resetSetupForm(tree)
        } else {
            const newData: FormSetupRound[] = [...currentFormData, ...tree.rounds]

            resetSetupForm({rounds: newData})
        }

        closeTournamentTreeDialog()
    }

    const FormPortal = (props: PropsWithChildren) => {
        const [domReady, setDomReady] = useState(false)

        useEffect(() => {
            setDomReady(true)
        }, [])

        return domReady ? createPortal(props.children, portalContainer.current!) : null
    }

    return (
        <>
            <Button variant="outlined" onClick={openTournamentTreeDialog}>
                Generate tournament tree
            </Button>
            <FormPortal>
                <Dialog
                    open={tournamentTreeDialogOpen}
                    onClose={closeTournamentTreeDialog}
                    fullWidth={true}
                    maxWidth={'xs'}
                    className="ready2competition">
                    <Box sx={{mx: 4, my: 2}}>
                        <FormContainer
                            formContext={formContext}
                            handleSubmit={event => onSubmitWithoutPropagation(event)}>
                            <FormInputNumber name="teams" label="Participanting teams" />
                            <CheckboxElement
                                name={`matchForPlaceThree`}
                                label={
                                    <FormInputLabel
                                        label={'Include a 3rd place match'}
                                        required={true}
                                        horizontal
                                    />
                                }
                            />
                            <CheckboxElement
                                name={`replaceAll`}
                                label={
                                    <FormInputLabel
                                        label={'Overwrite current setup'}
                                        required={true}
                                        horizontal
                                    />
                                }
                            />
                            <Box sx={{mt: 2}}>
                                <DialogActions>
                                    <Button onClick={closeTournamentTreeDialog}>
                                        {t('common.cancel')}
                                    </Button>
                                    <SubmitButton label={'Generate'} submitting={false} />
                                </DialogActions>
                            </Box>
                        </FormContainer>
                    </Box>
                </Dialog>
            </FormPortal>
        </>
    )
}

export default CompetitionSetupTreeHelper
