import * as React from 'react'
import {Stack} from '@mui/material'
import {EventRegistrationTemplateDto} from '../../api'
import EventRegistrationTeamForm from './EventRegistrationTeamForm.tsx'

export const EventRegistrationTeamRaceForm = (props: {registrationTemplate: EventRegistrationTemplateDto | null}) => {

    return (
        <React.Fragment>
            <Stack>
                {props.registrationTemplate?.racesTeam.map((race, index) => (
                    <EventRegistrationTeamForm
                        key={race.id}
                        index={index}
                        race={race}
                    />
                ))}
            </Stack>
        </React.Fragment>
    )
}

export default EventRegistrationTeamRaceForm
