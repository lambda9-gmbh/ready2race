import {Box, Button, Typography} from '@mui/material'
import {CompetitionRoundDto} from "@api/types.gen.ts";
import {competitionRoute, eventRoute} from "@routes";
import {useTranslation} from "react-i18next";
import {useFeedback} from "@utils/hooks.ts";
import {addSubstitution} from "@api/sdk.gen.ts";

type Props = {
    roundDto: CompetitionRoundDto
}
const Substitutions = ({roundDto, ...props}: Props) => {
    const {t} = useTranslation()
    const feedback = useFeedback()

    const {eventId} = eventRoute.useParams()
    const {competitionId} = competitionRoute.useParams()

    const onClick = () => {
        const {error} = await addSubstitution({
            path:  {
                eventId: eventId,
                competitionId: competitionId,
            },
            body: {
                competitionRegistrationId: roundDto.matches[0].teams[0].registrationId,
                competitionSetupRound: roundDto.setupRoundId,
                participantOut: roundDto.matches[0].teams[0].
            }
        })

        if (error) {
            feedback.error("todo error")
        } else {
            feedback.success("todo success")
        }
    }

    return (
        <>
            {roundDto.substitutions.map(sub => (
                <Box>
                    <Typography>todo Club: {sub.clubId}</Typography>
                    <Typography>
                        todo In: {sub.participantIn.firstName} {sub.participantIn.lastName}
                    </Typography>
                    <Typography>
                        todo Out: {sub.participantOut.firstName} {sub.participantOut.lastName}
                    </Typography>
                    <Button onClick={onClick}>todo Edit</Button>
                </Box>
            ))}
        </>
    )
}

export default Substitutions
