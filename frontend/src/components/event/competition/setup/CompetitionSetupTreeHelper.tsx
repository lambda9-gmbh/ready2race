import {FormEvent, PropsWithChildren, RefObject, useEffect, useState} from 'react'
import {Box, Button, Dialog, DialogActions} from '@mui/material'
import {CheckboxElement, FormContainer, useForm} from 'react-hook-form-mui'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import FormInputLabel from '@components/form/input/FormInputLabel.tsx'
import {useTranslation} from 'react-i18next'
import {createPortal} from 'react-dom'
import {
    CompetitionSetupForm,
    FormSetupRound,
} from '@components/event/competition/setup/CompetitionSetup.tsx'
import {getWeightings} from '@components/event/competition/setup/common.ts'

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

        const foo = Math.pow(2, roundCount) - teams

        for (let r = 0; r < roundCount; r++) {
            const matchesTeams: number[] = []

            // Normally MatchCount is two to the power of r except in the final round, if third place match is selected
            const matchCount = matchForPlaceThree && r === 0 ? 2 : Math.pow(2, r)

            for (let m = 0; m < matchCount; m++) {
                // If this is the first round and the participants are not fitting into default "two to the power of X(Round count)"
                // then some matches will have only a teams of 1 (Starting with Match Weighting 1 going upwards)
                const teams = r === roundCount - 1 && m <= foo - 1 ? 2 - 1 : 2

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

            const newRound: TreeHelperRound = {
                matches: matches,
                name: 'X-Finale',
            }
            rounds.push(newRound)
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
