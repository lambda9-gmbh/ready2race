import {CheckboxButtonGroup, useFormContext, useWatch} from 'react-hook-form-mui'
import {Checkbox, Divider, FormControlLabel, Paper, Stack, Typography} from '@mui/material'
import {useEffect, useState} from 'react'
import {Person} from '@mui/icons-material'
import {EventRegistrationCompetitionDto, EventRegistrationUpsertDto} from '../../api'
import {EventRegistrationPriceTooltip} from './EventRegistrationPriceTooltip.tsx'

const EventSingleCompetitionField = (props: {
    option: EventRegistrationCompetitionDto
    participantIndex: number
}) => {
    const formContext = useFormContext<EventRegistrationUpsertDto>()

    const [active, setActive] = useState(false)
    const [competitionIndex, setCompetitionIndex] = useState<number | undefined>(undefined)

    const singleCompetitions = useWatch({
        control: formContext.control,
        name: `participants.${props.participantIndex}.competitionsSingle`,
    })

    useEffect(() => {
        let index = singleCompetitions?.findIndex(r => r.competitionId === props.option.id) ?? -1
        setCompetitionIndex(index !== -1 ? index : undefined)
        setActive(index !== -1)
    }, [singleCompetitions])

    const onChange = (checked: boolean) => {
        if (checked) {
            formContext.setValue(`participants.${props.participantIndex}.competitionsSingle`, [
                ...(singleCompetitions ?? []),
                {competitionId: props.option.id},
            ])
        } else {
            formContext.setValue(
                `participants.${props.participantIndex}.competitionsSingle`,
                singleCompetitions?.filter(c => c.competitionId != props.option.id),
            )
        }
    }

    return (
        <Stack direction="row">
            <FormControlLabel
                control={<Checkbox />}
                checked={active}
                onChange={(_, checked) => onChange(checked)}
                label={
                    <Stack direction={'row'} alignItems={'center'} spacing={1}>
                        <Typography>{props.option.name}</Typography>
                        <EventRegistrationPriceTooltip competition={props.option} />
                    </Stack>
                }
            />
            {active && (props.option.fees?.length ?? 0) > 0 && (
                <>
                    <Divider orientation={'vertical'} sx={{mr: 2}} />
                    <CheckboxButtonGroup
                        name={`participants.${props.participantIndex}.competitionsSingle.${competitionIndex}.optionalFees`}
                        options={props.option.fees?.filter(f => !f.required) ?? []}
                        row
                    />
                </>
            )}
        </Stack>
    )
}

export const EventRegistrationSingleCompetitionForm = (props: {
    competitionsSingle: Map<string, Array<EventRegistrationCompetitionDto>>
}) => {
    const formContext = useFormContext<EventRegistrationUpsertDto>()

    const participantList = useWatch({
        control: formContext.control,
        name: `participants`,
    })

    return (
        <Stack spacing={2}>
            {participantList.map((participant, index) => (
                <Paper sx={{p: 2}} elevation={2} key={participant.id}>
                    <Stack direction="row" spacing={1}>
                        <Stack spacing={2} flex={1}>
                            <Stack direction="row" alignItems={'end'} spacing={1}>
                                <Person />
                                <Typography alignItems={'center'}>
                                    {`${participant.firstname} ${participant.lastname}`}
                                </Typography>
                            </Stack>
                            <Stack flex={1}>
                                {props.competitionsSingle
                                    .get(participant.gender ?? 'O')
                                    ?.map(option => (
                                        <EventSingleCompetitionField
                                            key={option.id}
                                            participantIndex={index}
                                            option={option}
                                        />
                                    ))}
                            </Stack>
                        </Stack>
                        <Typography alignSelf={'end'} variant={'overline'} color={'grey'}>
                            #{index + 1}
                        </Typography>
                    </Stack>
                </Paper>
            ))}
        </Stack>
    )
}
