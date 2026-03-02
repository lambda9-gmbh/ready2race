import {
    EventDayScheduleCompetitionDataDto,
    EventDayScheduleCompetitionMatchDataDto,
    EventDayScheduleCompetitionRoundDataDto,
} from '@api/types.gen.ts'
import BaseDialog from '@components/BaseDialog.tsx'
import {useTranslation} from 'react-i18next'
import {
    Button,
    DialogActions,
    DialogContent,
    DialogTitle,
    MenuItem,
    Select,
    Stack,
    Typography,
} from '@mui/material'
import FormInputTime from '@components/form/input/FormInputTime.tsx'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {useEffect, useState} from 'react'
import {addTimeslot} from '@api/sdk.gen.ts'
import {addMinutes} from 'date-fns'
import {SubmitButton} from '@components/form/SubmitButton.tsx'
import {useFeedback} from '@utils/hooks.ts'

type Props = {
    data: Array<EventDayScheduleCompetitionDataDto>
    onClose: () => void
    open: boolean
    eventId: string
    eventDayId: string
}

type TimeslotForm = {
    startTime: Date
}

export const CompetitionTimeslotForm = ({data, open, onClose, eventId, eventDayId}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const formContext = useForm<TimeslotForm>()

    const [selectedCompetition, setSelectedCompetition] =
        useState<EventDayScheduleCompetitionDataDto | null>(null)
    const [selectedRound, setSelectedRound] =
        useState<EventDayScheduleCompetitionRoundDataDto | null>(null)
    const [selectedMatch, setSelectedMatch] =
        useState<EventDayScheduleCompetitionMatchDataDto | null>(null)

    const [duration, setDuration] = useState<number>(0)
    const [endTime, setEndTime] = useState<Date>(new Date())
    const availableRounds =
        selectedCompetition?.rounds.filter(
            round => (round.matchCount ?? round.matches.length) > 0,
        ) ?? []

    useEffect(() => {
        let tmpDuration = 0
        if (selectedCompetition?.matchDuration && selectedCompetition?.matchGapsDuration) {
            if (selectedMatch && selectedRound && selectedCompetition) {
                tmpDuration = selectedCompetition.matchDuration
            } else if (selectedRound && selectedCompetition) {
                const roundMatchCount = selectedRound.matchCount ?? selectedRound.matches.length
                tmpDuration =
                    roundMatchCount * selectedCompetition.matchDuration +
                    Math.max(0, roundMatchCount - 1) * selectedCompetition.matchGapsDuration
            } else if (selectedCompetition) {
                const totalMatches =
                    selectedCompetition.matchCount ??
                    selectedCompetition.rounds.reduce(
                        (sum, round) => sum + (round.matchCount ?? round.matches.length),
                        0,
                    )
                tmpDuration =
                    totalMatches * selectedCompetition.matchDuration +
                    Math.max(0, totalMatches - 1) * selectedCompetition.matchGapsDuration
            }
        } else {
            tmpDuration = 0
        }
        const startTime = formContext.getValues('startTime')
        setDuration(tmpDuration)
        setEndTime(addMinutes(startTime, tmpDuration))
    }, [selectedCompetition, selectedRound, selectedMatch, formContext.watch('startTime')])

    const handleSubmit = async (data: TimeslotForm) => {
        if (selectedCompetition?.matchGapsDuration) {
            let name = ''
            if (selectedMatch) {
                name += selectedMatch.matchName + ' - '
            }
            if (selectedRound) {
                name += selectedRound.roundName + ' - '
            }
            name += selectedCompetition?.competitionName

            const {error: resError, response} = await addTimeslot({
                path: {eventId: eventId, eventDayId: eventDayId},
                body: {
                    name: name,
                    eventDay: eventDayId,
                    competitionReference: selectedCompetition.competitionId,
                    roundReference: selectedRound?.roundId,
                    matchReference: selectedMatch?.matchId,
                    startTime: data.startTime.toTimeString().slice(0, 8),
                    endTime: endTime.toTimeString().slice(0, 8),
                },
            })
            if (!resError) {
                handleClose()
            } else {
                if (response.status === 409) {
                    if (resError.errorCode === 'DUPLICATE_TIMESLOT') {
                        feedback.error('wambo wambo')
                    } else if (resError.errorCode === 'CHILD_TIMESLOT_ALREADY_EXISTS') {
                        feedback.error('wambo wambo wambo')
                    }
                } else {
                    feedback.error(t('common.error.unexpected'))
                }
            }
        }
    }
    const handleClose = () => {
        setSelectedCompetition(null)
        setSelectedRound(null)
        setSelectedMatch(null)
        formContext.reset()
        onClose()
    }

    return (
        <BaseDialog open={open} onClose={handleClose}>
            <FormContainer formContext={formContext} onSuccess={handleSubmit}>
                <DialogTitle>wambo</DialogTitle>
                <DialogContent>
                    <Stack spacing={4}>
                        <Select
                            name="competition"
                            value={selectedCompetition?.competitionId ?? 'none'}
                            onChange={e => {
                                const competition =
                                    data.find(
                                        c =>
                                            c.competitionId === e.target.value &&
                                            c.matchDuration &&
                                            c.matchGapsDuration,
                                    ) ?? null

                                setSelectedCompetition(competition)
                                setSelectedRound(null)
                                setSelectedMatch(null)
                            }}>
                            {data
                                .filter(c => c.matchDuration && c.matchGapsDuration)
                                .map(comp => (
                                    <MenuItem key={comp.competitionId} value={comp.competitionId}>
                                        {comp.competitionName}
                                    </MenuItem>
                                ))}
                            <MenuItem value={'none'}>{'kein wambo'}</MenuItem>
                        </Select>

                        <Select
                            name="round"
                            value={selectedRound?.roundId ?? 'none'}
                            disabled={!selectedCompetition}
                            onChange={e => {
                                const round =
                                    availableRounds.find(
                                        r => r.roundId === e.target.value,
                                    ) ?? null

                                setSelectedRound(round)
                                setSelectedMatch(null)
                            }}>
                            {availableRounds.map(round => (
                                <MenuItem key={round.roundId} value={round.roundId}>
                                    {round.roundName}
                                </MenuItem>
                            ))}
                            <MenuItem value={'none'}>{'kein wambo'}</MenuItem>
                        </Select>

                        <Select
                            name="match"
                            value={selectedMatch?.matchId ?? 'none'}
                            disabled={!selectedRound}
                            onChange={e =>
                                setSelectedMatch(
                                    selectedRound?.matches.find(
                                        m => m.matchId === e.target.value,
                                    ) ?? null,
                                )
                            }>
                            {selectedRound?.matches.map(match => (
                                <MenuItem key={match.matchId} value={match.matchId}>
                                    {match.matchName}
                                </MenuItem>
                            ))}
                            <MenuItem value={'none'}>{'kein wambo'}</MenuItem>
                        </Select>
                        <FormInputTime
                            name={'startTime'}
                            label={t('event.eventDay.startTime')}
                            timeSteps={{minutes: 1}}
                            required
                        />
                        <Typography variant={'body2'}>{`wambo: ${duration} wambo`}</Typography>
                        <Typography
                            variant={
                                'body2'
                            }>{`wambo: ${endTime.toTimeString().slice(0, 5)}`}</Typography>
                    </Stack>
                </DialogContent>
                <DialogActions>
                    <Button variant="outlined" onClick={handleClose}>
                        wambo
                    </Button>
                    <SubmitButton submitting={false}>wambo</SubmitButton>
                </DialogActions>
            </FormContainer>
        </BaseDialog>
    )
}
