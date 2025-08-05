import {Paper, Stack, Typography} from '@mui/material'
import {useMemo} from 'react'
import {useFormContext, useWatch} from 'react-hook-form-mui'
import {useTranslation} from 'react-i18next'
import {useEventRegistration} from "@contexts/eventRegistration/EventRegistrationContext.ts";
import {
    CompetitionRegistrationFormData,
    EventRegistrationFormData, EventRegistrationParticipantFormData
} from "../../pages/eventRegistration/EventRegistrationCreatePage.tsx";
import {EventRegistrationCompetitionDto} from "@api/types.gen.ts";

const calcAmountForCompetitionRegistration = (
    competition: EventRegistrationCompetitionDto,
    optionalFees: string[],
    late: boolean,
): number =>
    competition.fees?.filter(fee => fee.required || optionalFees.includes(fee.id))
        .reduce((sum, fee) => sum + Number(late ? (fee.lateAmount ?? fee.amount) : fee.amount), 0)
        ?? 0

const calcAmountForSingles = (
    participants: EventRegistrationParticipantFormData[],
    late: boolean,
    competitionMap: Map<string, EventRegistrationCompetitionDto>,
) =>
    participants.reduce((sum, participant) =>
        sum + (participant.competitionsSingle?.filter(reg => reg.isLate === late).reduce((sumP, reg) => {
            const competition = competitionMap.get(reg.competitionId)
            return sumP + (competition ? calcAmountForCompetitionRegistration(competition, reg.optionalFees ?? [], late) : 0)
        }, 0) ?? 0), 0
    )

const calcAmountForTeams = (
    competitionRegistrations: CompetitionRegistrationFormData[],
    late: boolean,
    competitionMap: Map<string, EventRegistrationCompetitionDto>,
) =>
    competitionRegistrations.reduce((sum, comp) =>
        sum + (comp.teams.filter(team => team.isLate === late).reduce((sumT, team) => {
            const competition = competitionMap.get(comp.competitionId)
            return sumT + (competition ? calcAmountForCompetitionRegistration(competition, team.optionalFees ?? [], late) : 0)
        }, 0)), 0
    )

export const EventRegistrationFeeDisplay = () => {
    const {t} = useTranslation()
    const formContext = useFormContext<EventRegistrationFormData>()
    const {info} = useEventRegistration()

    const competitionRegistrations = useWatch({
        control: formContext.control,
        name: 'competitionRegistrations',
    })
    const participants = useWatch({control: formContext.control, name: 'participants'})

    const competitionMap = useMemo(() => {
        return new Map(
            [
                ...(info?.competitionsSingle ?? []),
                ...(info?.competitionsTeam ?? []),
            ].map(competition => {
                return [competition.id, competition]
            }),
        )
    }, [info?.competitionsSingle, info?.competitionsTeam])

    const teamAmount = useMemo(() => calcAmountForTeams(competitionRegistrations, false, competitionMap), [competitionRegistrations])
    const singleAmount = useMemo(() => calcAmountForSingles(participants, false, competitionMap), [participants])
    const lateTeamAmount = useMemo(() => calcAmountForTeams(competitionRegistrations, true, competitionMap), [competitionRegistrations])
    const lateSingleAmount = useMemo(() => calcAmountForSingles(participants, true, competitionMap), [participants])

    return (
        <Paper elevation={2} sx={{width: '240px', alignSelf: 'end', mb: 3}}>
            <Stack p={1} alignItems={'end'}>
                <Typography variant={'caption'}>{t('event.registration.totalFees')}</Typography>
                <Typography variant={'h5'}>{(teamAmount + singleAmount).toFixed(2)}€</Typography>
                { info?.state !== 'REGULAR' &&
                    <>
                        <Typography variant={'caption'}>{t('event.registration.lateTotalFees')}</Typography>
                        <Typography variant={'h5'}>{(lateTeamAmount + lateSingleAmount).toFixed(2)}€</Typography>
                    </>
                }
            </Stack>
        </Paper>
    )
}
