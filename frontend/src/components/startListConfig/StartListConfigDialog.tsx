import {BaseEntityDialogProps} from "@utils/types.ts";
import {StartListConfigDto, StartListConfigRequest} from "@api/types.gen.ts";
import EntityDialog from "@components/EntityDialog.tsx";
import {useForm} from "react-hook-form-mui";
import { useCallback } from "react";
import { Stack } from "@mui/material";
import {FormInputText} from "@components/form/input/FormInputText.tsx";
import {addStartListConfig, updateStartListConfig} from "@api/sdk.gen.ts";
import {takeIfNotEmpty} from "@utils/ApiUtils.ts";

type Form = {
    name: string
    colParticipantFirstname: string
    colParticipantLastname: string
    colParticipantGender: string
    colParticipantRole: string
    colParticipantYear: string
    colParticipantClub: string
    colClubName: string
    colTeamName: string
    colTeamStartNumber: string
    colMatchName: string
    colMatchStartTime: string
    colRoundName: string
    colCompetitionIdentifier: string
    colCompetitionName: string
    colCompetitionShortName: string
    colCompetitionCategory: string
}

const defaultValues: Form = {
    name: '',
    colParticipantFirstname: '',
    colParticipantLastname: '',
    colParticipantGender: '',
    colParticipantRole: '',
    colParticipantYear: '',
    colParticipantClub: '',
    colClubName: '',
    colTeamName: '',
    colTeamStartNumber: '',
    colMatchName: '',
    colMatchStartTime: '',
    colRoundName: '',
    colCompetitionIdentifier: '',
    colCompetitionName: '',
    colCompetitionShortName: '',
    colCompetitionCategory: '',
}

const addAction = (formData: Form) =>
    addStartListConfig({
        body: mapFormToRequest(formData)
    })

const editAction = (formData: Form, entity: StartListConfigDto) =>
    updateStartListConfig({
        path: {startListConfigId: entity.id},
        body: mapFormToRequest(formData)
    })

const StartListConfigDialog = (props: BaseEntityDialogProps<StartListConfigDto>) => {

    const formContext = useForm<Form>()

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}>
            <Stack spacing={4}>
                <FormInputText name={'name'} label={'[todo] Name'} required />
            </Stack>
        </EntityDialog>
    )
}

const mapFormToRequest = (formData: Form): StartListConfigRequest => ({
    name: formData.name,
    colParticipantFirstname: takeIfNotEmpty(formData.colParticipantFirstname),
    colParticipantLastname: takeIfNotEmpty(formData.colParticipantLastname),
    colParticipantGender: takeIfNotEmpty(formData.colParticipantGender),
    colParticipantRole: takeIfNotEmpty(formData.colParticipantRole),
    colParticipantYear: takeIfNotEmpty(formData.colParticipantYear),
    colParticipantClub: takeIfNotEmpty(formData.colParticipantClub),
    colClubName: takeIfNotEmpty(formData.colClubName),
    colTeamName: takeIfNotEmpty(formData.colTeamName),
    colTeamStartNumber: takeIfNotEmpty(formData.colTeamStartNumber),
    colMatchName: takeIfNotEmpty(formData.colMatchName),
    colMatchStartTime: takeIfNotEmpty(formData.colMatchStartTime),
    colRoundName: takeIfNotEmpty(formData.colRoundName),
    colCompetitionIdentifier: takeIfNotEmpty(formData.colCompetitionIdentifier),
    colCompetitionName: takeIfNotEmpty(formData.colCompetitionName),
    colCompetitionShortName: takeIfNotEmpty(formData.colCompetitionShortName),
    colCompetitionCategory: takeIfNotEmpty(formData.colCompetitionCategory),
})

const mapDtoToForm = (dto: StartListConfigDto): Form => ({
    name: dto.name,
    colParticipantFirstname: dto.colParticipantFirstname ?? '',
    colParticipantLastname: dto.colParticipantLastname ?? '',
    colParticipantGender: dto.colParticipantGender ?? '',
    colParticipantRole: dto.colParticipantRole ?? '',
    colParticipantYear: dto.colParticipantYear ?? '',
    colParticipantClub: dto.colParticipantClub ?? '',
    colClubName: dto.colClubName ?? '',
    colTeamName: dto.colTeamName ?? '',
    colTeamStartNumber: dto.colTeamStartNumber ?? '',
    colMatchName: dto.colMatchName ?? '',
    colMatchStartTime: dto.colMatchStartTime ?? '',
    colRoundName: dto.colRoundName ?? '',
    colCompetitionIdentifier: dto.colCompetitionIdentifier ?? '',
    colCompetitionName: dto.colCompetitionName ?? '',
    colCompetitionShortName: dto.colCompetitionShortName ?? '',
    colCompetitionCategory: dto.colCompetitionCategory ?? '',
})

export default StartListConfigDialog