import * as React from 'react'
import {Stack} from '@mui/material'
import {EventRegistrationTemplateDto} from '../../api'
import EventRegistrationTeamsForm from './EventRegistrationTeamsForm.tsx'

export const EventRegistrationTeamCompetitionForm = (props: {registrationTemplate: EventRegistrationTemplateDto | null}) => {

    return (
        <React.Fragment>
            <Stack>
                {props.registrationTemplate?.competitionsTeam.map((competition, index) => (
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
