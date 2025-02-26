import {useFormContext, useWatch} from 'react-hook-form-mui'
import {Checkbox, FormControlLabel, Paper, Stack, Typography} from '@mui/material'
import {useEffect, useState} from 'react'
import {Person} from '@mui/icons-material'
import {EventRegistrationRaceDto, EventRegistrationUpsertDto} from '../../api'
import {EventRegistrationPriceTooltip} from './EventRegistrationPriceTooltip.tsx'

const EventSingleRaceField = (props: {
    option: EventRegistrationRaceDto
    participantIndex: number
}) => {
    const formContext = useFormContext<EventRegistrationUpsertDto>()

    const [active, setActive] = useState(false)

    const singleRaces = useWatch({
        control: formContext.control,
        name: `participants.${props.participantIndex}.racesSingle`,
    })

    useEffect(() => {
        const isActive = singleRaces?.find(r => r === props.option.id)
        if (isActive) {
            setActive(true)
        } else {
            setActive(false)
        }
    }, [singleRaces])

    const onChange = (checked: boolean) => {
        if (checked) {
            formContext.setValue(`participants.${props.participantIndex}.racesSingle`, [
                ...(singleRaces ?? []),
                props.option.id,
            ])
        } else {
            formContext.setValue(
                `participants.${props.participantIndex}.racesSingle`,
                singleRaces?.filter(r => r != props.option.id),
            )
        }
    }

    return (
        <Stack direction="row" justifyContent={'space-between'}>
            <FormControlLabel
                control={<Checkbox />}
                checked={active}
                onChange={(_, checked) => onChange(checked)}
                label={
                    <Stack direction={'row'} alignItems={'center'} spacing={1}>
                        <Typography>
                            {props.option.name} (
                            {props.option.description})
                        </Typography>
                        <EventRegistrationPriceTooltip race={props.option} />
                    </Stack>
                }
            />
        </Stack>
    )
}

export const EventRegistrationSingleRaceForm = (props: {
    singleRaces: Map<string, Array<EventRegistrationRaceDto>>
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
                            <Stack spacing={2} flex={1} direction="row">
                                {props.singleRaces.get(participant.gender ?? 'O')?.map(option => (
                                    <EventSingleRaceField
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
