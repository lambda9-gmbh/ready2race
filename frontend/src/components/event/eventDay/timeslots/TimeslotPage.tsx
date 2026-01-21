import {useFeedback, useFetch} from '@utils/hooks.ts'
import {useTranslation} from 'react-i18next'
import {Divider, List, ListItem, ListItemButton, ListItemText, Typography} from '@mui/material'
import {getTimeslots} from '@api/sdk.gen.ts'
import {useState} from 'react'
import {EmailTemplateDto, TimeslotDto} from '@api/types.gen.ts'
import {EmailTemplateEditor} from '@components/administration/emailTemplates/EmailTemplateEditor.tsx'

type Props = {
    eventId: string
    eventDayId: string
}

export function TimeslotPage({eventDayId, eventId}: Props) {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const [selectedTimeslot, setSelectedTimeslot] = useState<TimeslotDto | null>(null)
    const {data: timeslots, reload: ReloadTimeslots} = useFetch(
        signal =>
            getTimeslots({
                signal,
                path: {
                    eventDayId: eventDayId,
                    eventId: eventId,
                },
            }),
        {deps: [eventDayId]},
    )
    const handleEditorClose = () => {
        setSelectedTimeslot(null)
        ReloadTimeslots()
    }

    return (
        <>
            {/*{selectedTimeslot && (*/}
            {/*    <EmailTemplateEditor*/}
            {/*        template={selectedTemplate}*/}
            {/*        lng={lng}*/}
            {/*        open={true}*/}
            {/*        onClose={() => handleEditorClose()}*/}
            {/*    />*/}
            {/*)}*/}

            {timeslots && (
                <List>
                    {timeslots.map(timeslot => (
                        <>
                            <ListItem key={timeslot.id}>
                                <ListItemButton onClick={() => setSelectedTimeslot(timeslot)}>
                                    <ListItemText
                                        primary={`${timeslot.name} (${timeslot.startTime} - ${timeslot.endTime})`}
                                    />
                                </ListItemButton>
                            </ListItem>
                            <Divider variant="middle" component="li" />
                        </>
                    ))}
                </List>
            )}
        </>
    )
}
