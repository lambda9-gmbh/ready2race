import {Paper, Stack, Typography} from '@mui/material'
import {useEffect, useMemo, useState} from 'react'
import {useFormContext, useWatch} from 'react-hook-form-mui'
import {useTranslation} from 'react-i18next'
import {
    CompetitionRegistrationSingleUpsertDto,
    CompetitionRegistrationTeamUpsertDto,
    EventRegistrationInfoDto,
    EventRegistrationUpsertDto,
} from '@api/types.gen.ts'

export const EventRegistrationFeeDisplay = (props: {
    registrationInfo: EventRegistrationInfoDto | null
}) => {
    const {t} = useTranslation()
    const formContext = useFormContext<EventRegistrationUpsertDto>()

    const [teamAmount, setTeamAmount] = useState<number>(0)
    const [singleAmount, setSingleAmount] = useState<number>(0)

    const competitionRegistrations = useWatch({
        control: formContext.control,
        name: 'competitionRegistrations',
    })
    const participants = useWatch({control: formContext.control, name: 'participants'})

    const competitionMap = useMemo(() => {
        return new Map(
            [
                ...(props.registrationInfo?.competitionsSingle ?? []),
                ...(props.registrationInfo?.competitionsTeam ?? []),
            ].map(competition => {
                return [competition.id, competition]
            }),
        )
    }, [props.registrationInfo?.competitionsSingle, props.registrationInfo?.competitionsTeam])

    useEffect(() => {
        setTeamAmount(
            competitionRegistrations?.reduce((sumTage, comp) => {
                return (
                    sumTage +
                    (comp.teams?.reduce((sum, team) => {
                        return sum + calcAmountForTeam(comp.competitionId, team)
                    }, 0) || 0)
                )
            }, 0) || 0,
        )
    }, [competitionRegistrations])

    useEffect(() => {
        setSingleAmount(
            participants.reduce((sum, participant) => {
                return (
                    sum +
                    (participant.competitionsSingle?.reduce((sumParticipant, registration) => {
                        return sumParticipant + calcAmountForSingle(registration)
                    }, 0) || 0)
                )
            }, 0) || 0,
        )
    }, [participants])

    const calcAmountForTeam = (
        competitionId: string,
        registration: CompetitionRegistrationTeamUpsertDto,
    ): number => {
        const competition = competitionMap.get(competitionId)
        if (competition) {
            return (
                competition.fees
                    ?.filter(fee => fee.required || registration.optionalFees?.includes(fee.id))
                    .reduce((sum, fee) => {
                        return sum + Number(fee.amount)
                    }, 0) ?? 0
            )
        } else {
            return 0
        }
    }

    const calcAmountForSingle = (registration: CompetitionRegistrationSingleUpsertDto): number => {
        const competition = competitionMap.get(registration.competitionId)
        if (competition) {
            return (
                competition.fees
                    ?.filter(fee => fee.required || registration.optionalFees?.includes(fee.id))
                    .reduce((sum, fee) => {
                        return sum + Number(fee.amount)
                    }, 0) ?? 0
            )
        } else {
            return 0
        }
    }

    return (
        <Paper elevation={2} sx={{width: '240px', alignSelf: 'end', mb: 3}}>
            <Stack p={1} alignItems={'end'}>
                <Typography variant={'caption'}>{t('event.registration.totalFees')}</Typography>
                <Typography variant={'h5'}>{(teamAmount + singleAmount).toFixed(2)}€</Typography>
            </Stack>
        </Paper>
    )
}
