import * as React from 'react'
import {Stack} from '@mui/material'
import {EventRegistrationInfoDto} from '../../api'
import EventRegistrationTeamsForm from './EventRegistrationTeamsForm.tsx'

export const EventRegistrationTeamCompetitionForm = (props: {
    registrationInfo: EventRegistrationInfoDto | null
}) => {
    return (
        <React.Fragment>
            <Stack>
                {props.registrationInfo?.competitionsTeam.map((competition, index) => (
                    <EventRegistrationTeamsForm
                        key={competition.id}
                        index={index}
                        competition={competition}
                    />
                ))}
            </Stack>
        </React.Fragment>
    )
}

export default EventRegistrationTeamCompetitionForm
