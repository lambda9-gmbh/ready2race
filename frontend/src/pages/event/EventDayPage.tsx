import {Box, Button, Card, Link, Typography, useMediaQuery, useTheme} from '@mui/material'
import {useTranslation} from 'react-i18next'
import {useEntityAdministration, useFeedback, useFetch} from '@utils/hooks.ts'
import {eventDayRoute, eventRoute} from '@routes'
import Throbber from '@components/Throbber.tsx'
import CompetitionAndDayAssignment from '@components/event/CompetitionAndDayAssignment.tsx'
import {AutocompleteOption} from '@utils/types.ts'
import {competitionLabelName} from '@components/event/competition/common.ts'
import {useRef, useState} from 'react'
import {
    getEventDay,
    getCompetitions,
    downloadSchedulePdfForEventDay,
    getCompetitionMatchData,
} from '@api/sdk.gen.ts'
import TimeslotTable from '@components/event/eventDay/timeslots/TimeslotTable.tsx'
import {TimeslotDto} from '@api/types.gen.ts'
import TimeslotDialog from '@components/event/eventDay/timeslots/TimeslotDialog.tsx'
import FileDownloadOutlinedIcon from '@mui/icons-material/FileDownloadOutlined'
import {getFilename} from '@utils/helpers.ts'
import {CompetitionTimeslotForm} from '@components/event/eventDay/timeslots/CompetitionTimeslotForm.tsx'

const EventDayPage = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const theme = useTheme()
    const isMobile = useMediaQuery(theme.breakpoints.down('md'))

    const {eventId} = eventRoute.useParams()
    const {eventDayId} = eventDayRoute.useParams()

    const [reloadData, setReloadData] = useState(false)
    const [openDialog, setOpenDialog] = useState(false)
    const handleOnCLose = () => {
        setOpenDialog(false)
        reloadCompetitionMatchData()
    }

    const {data: eventDayData, pending: eventDayPending} = useFetch(
        signal => getEventDay({signal, path: {eventId: eventId, eventDayId: eventDayId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.single', {entity: t('event.eventDay.eventDay')}),
                    )
                }
            },
            deps: [eventId, eventDayId],
        },
    )

    const {data: assignedCompetitionsData, pending: assignedCompetitionsPending} = useFetch(
        signal =>
            getCompetitions({signal, path: {eventId: eventId}, query: {eventDayId: eventDayId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.competition.competitions'),
                        }),
                    )
                }
            },
            deps: [eventId, eventDayId, reloadData],
        },
    )
    const assignedCompetitions = assignedCompetitionsData?.data.map(value => value.id) ?? []

    const {data: competitionsData, pending: competitionsPending} = useFetch(
        signal => getCompetitions({signal, path: {eventId: eventId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.competition.competitions'),
                        }),
                    )
                }
            },
            deps: [eventId, reloadData],
        },
    )

    const {data: competitionMatchData, reload: reloadCompetitionMatchData} = useFetch(
        signal =>
            getCompetitionMatchData({signal, path: {eventId: eventId, eventDayId: eventDayId}}),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.multiple.short', {
                            entity: t('event.competition.competitions'),
                        }),
                    )
                } else {
                    administrationProps.table.reloadData()
                }
            },
            deps: [eventId, eventDayId, reloadData],
        },
    )

    const downloadRef = useRef<HTMLAnchorElement>(null)

    const handleDownloadSchedulePdf = async () => {
        const {data, error, response} = await downloadSchedulePdfForEventDay({
            path: {
                eventId,
                eventDayId,
            },
        })
        const anchor = downloadRef.current

        if (error) {
            feedback.error(t('common.error.unexpected'))
        } else if (data !== undefined && anchor) {
            // need Blob constructor for text/csv
            anchor.href = URL.createObjectURL(new Blob([data]))
            anchor.download =
                getFilename(response) ?? `schedule-${eventDayData?.date.toString()}.pdf}`
            anchor.click()
            anchor.href = ''
            anchor.download = ''
        }
    }

    const selection: AutocompleteOption[] =
        competitionsData?.data.map(value => ({
            id: value.id,
            label: competitionLabelName(value.properties.identifier, value.properties.name),
        })) ?? []

    const administrationProps = useEntityAdministration<TimeslotDto>(t('event.eventDay.timeslot'))

    return (
        <Box>
            {competitionMatchData && (
                <CompetitionTimeslotForm
                    data={competitionMatchData}
                    onClose={handleOnCLose}
                    open={openDialog}
                    eventId={eventId}
                    eventDayId={eventDayId}
                />
            )}
            <Link ref={downloadRef} display={'none'}></Link>
            {(eventDayData && (
                <Box sx={{display: 'flex', flexDirection: 'column', gap: 4}}>
                    <Typography variant={'h1'}>
                        {eventDayData.date + (eventDayData.name ? ' | ' + eventDayData.name : '')}
                    </Typography>
                    {eventDayData.description && (
                        <Typography>{eventDayData.description}</Typography>
                    )}
                    <Card sx={{p: 2}}>
                        {(competitionsData && assignedCompetitionsData && (
                            <CompetitionAndDayAssignment
                                entityPathId={eventDayId}
                                options={selection}
                                assignedEntities={assignedCompetitions}
                                assignEntityLabel={t('event.competition.competition')}
                                competitionsToDay={true}
                                reloadData={() => setReloadData(!reloadData)}
                            />
                        )) ||
                            ((competitionsPending || assignedCompetitionsPending) && <Throbber />)}
                    </Card>
                    <Card sx={{p: 2, display: 'flex', flexDirection: 'column'}}>
                        <Typography variant="h6">{t('event.eventDay.schedule')}</Typography>
                        <Button
                            startIcon={<FileDownloadOutlinedIcon />}
                            variant={'outlined'}
                            sx={{
                                width: 'fit-content',
                                ...(!isMobile ? {ml: 'auto'} : {mt: 2}),
                            }}
                            onClick={handleDownloadSchedulePdf}>
                            {t('event.eventDay.downloadSchedule')}
                        </Button>
                        {competitionMatchData && competitionMatchData?.length > 0 && (
                            <Button
                                variant={'outlined'}
                                sx={{
                                    width: 'fit-content',
                                    mt: 1,
                                    ...(!isMobile ? {ml: 'auto'} : {}),
                                }}
                                onClick={() => setOpenDialog(true)}>
                                {'wambo'}
                            </Button>
                        )}
                        <TimeslotTable {...administrationProps.table} />
                        <TimeslotDialog {...administrationProps.dialog} />
                    </Card>
                </Box>
            )) ||
                (eventDayPending && <Throbber />)}
        </Box>
    )
}

export default EventDayPage
