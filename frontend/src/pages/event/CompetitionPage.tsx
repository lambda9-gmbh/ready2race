import {Box, Divider, Grid2, Stack, Typography} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {eventRoute, competitionRoute} from '@routes'
import {eventDayName} from '@components/event/common.ts'
import {AutocompleteOption} from '@utils/types.ts'
import Throbber from '@components/Throbber.tsx'
import CompetitionAndDayAssignment from '@components/event/competitionAndDayAssignment/CompetitionAndDayAssignment.tsx'
import {useState} from 'react'
import EntityDetailsEntry from '@components/EntityDetailsEntry.tsx'
import {getEventDays, getCompetition} from '@api/sdk.gen.ts'
import CompetitionCountEntry from '@components/event/competition/CompetitionCountEntry.tsx'

const CompetitionPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const [reloadDataTrigger, setReloadDataTrigger] = useState(false)

    const {data: competitionData, pending: competitionPending} = useFetch(
        signal => getCompetition({signal, path: {eventId: eventId, competitionId: competitionId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(t('common.load.error.single', {entity: t('event.competition.competition')}))
                    console.error(error)
                }
            },
            deps: [eventId, competitionId],
        },
    )

    const {data: assignedEventDaysData, pending: assignedEventDaysPending} = useFetch(
        signal => getEventDays({signal, path: {eventId: eventId}, query: {competitionId: competitionId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {entity: t('event.eventDay.eventDays')}),
                    )
                    console.error(error)
                }
            },
            deps: [eventId, competitionId, reloadDataTrigger],
        },
    )
    const assignedEventDays =
        assignedEventDaysData?.data.map(value => ({
            id: value.id,
            label: eventDayName(value.date, value.name),
        })) ?? []

    const {data: eventDaysData, pending: eventDaysPending} = useFetch(
        signal => getEventDays({signal, path: {eventId: eventId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {entity: t('event.eventDay.eventDays')}),
                    )
                    console.error(error)
                }
            },
            deps: [eventId, reloadDataTrigger],
        },
    )

    const selection: AutocompleteOption[] =
        eventDaysData?.data.map(value => ({
            id: value.id,
            label: eventDayName(value.date, value.name),
        })) ?? []

    return (
        <Grid2 container justifyContent="space-between" direction="row" spacing={2}>
            <Box sx={{flex: 1, maxWidth: 600}}>
                {(competitionData && (
                    <Stack spacing={2}>
                        <EntityDetailsEntry
                            content={
                                competitionData.properties.identifier + ' | ' + competitionData.properties.name
                            }
                            variant="h1"
                        />
                        <EntityDetailsEntry content={competitionData.properties.shortName} />
                        <EntityDetailsEntry content={competitionData.properties.description} />
                        {competitionData.properties.competitionCategory && (
                            <Box sx={{py: 2}}>
                                <EntityDetailsEntry
                                    content={competitionData.properties.competitionCategory.name}
                                />
                                <EntityDetailsEntry
                                    content={competitionData.properties.competitionCategory.description}
                                />
                            </Box>
                        )}

                        <Divider />

                        {competitionData.properties.namedParticipants.map((np, index) => (
                            <>
                                <Box key={`box${index}`}> {/*todo: should have np.id instead of index to prevent updating errors*/}
                                    <Typography variant="subtitle1">{np.name}</Typography>
                                    <Typography>{np.description}</Typography>
                                    <CompetitionCountEntry
                                        label={t('event.competition.count.males')}
                                        content={np.countMales}
                                    />
                                    <CompetitionCountEntry
                                        label={t('event.competition.count.females')}
                                        content={np.countFemales}
                                    />
                                    <CompetitionCountEntry
                                        label={t('event.competition.count.nonBinary')}
                                        content={np.countNonBinary}
                                    />
                                    <CompetitionCountEntry
                                        label={t('event.competition.count.mixed')}
                                        content={np.countMixed}
                                    />
                                </Box>
                            </>
                        ))}
                        <Divider />
                        {competitionData.properties.fees.map((f, index) => (
                            <>
                                <Box key={`box${index}`}> {/*todo: should have np.id instead of index to prevent updating errors*/}
                                    <Typography variant="subtitle1">{f.name}</Typography>
                                    <Typography>{f.description}</Typography>
                                    <Typography>
                                        {f.required
                                            ? t('event.competition.fee.required.required')
                                            : t('event.competition.fee.required.notRequired')
                                        }
                                    </Typography>
                                    <CompetitionCountEntry
                                        label={t('event.competition.fee.amount')}
                                        content={f.amount + "€"}
                                    />
                                </Box>
                            </>
                        ))}
                    </Stack>
                )) ||
                    (competitionPending && <Throbber />)}
            </Box>
            <Box sx={{flex: 1, maxWidth: 400}}>
                {(eventDaysData && assignedEventDaysData && (
                    <CompetitionAndDayAssignment
                        entityPathId={competitionId}
                        options={selection}
                        assignedEntities={assignedEventDays}
                        assignEntityLabel={t('event.eventDay.eventDay')}
                        competitionsToDay={false}
                        onSuccess={() => setReloadDataTrigger(!reloadDataTrigger)}
                    />
                )) ||
                    ((eventDaysPending || assignedEventDaysPending) && <Throbber />)}
            </Box>
        </Grid2>
    )
}

export default CompetitionPage
