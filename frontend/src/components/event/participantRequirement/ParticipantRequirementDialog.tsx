import {BaseEntityDialogProps} from '@utils/types.ts'
import {ParticipantRequirementDto, ParticipantRequirementUpsertDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {addParticipantRequirement, updateParticipantRequirement} from '@api/sdk.gen.ts'
import {useForm} from 'react-hook-form-mui'
import {useCallback} from 'react'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {FormInputCheckbox} from '@components/form/input/FormInputCheckbox.tsx'

type ParticipantRequirementForm = {
    name: string
    description: string
    optional: boolean
}

const ParticipantRequirementDialog = (props: BaseEntityDialogProps<ParticipantRequirementDto>) => {
    const {t} = useTranslation()

    const addAction = (formData: ParticipantRequirementForm) => {
        return addParticipantRequirement({
            body: mapFormToRequest(formData),
        })
    }

    const editAction = (
        formData: ParticipantRequirementForm,
        entity: ParticipantRequirementDto,
    ) => {
        return updateParticipantRequirement({
            path: {participantRequirementId: entity.id},
            body: mapFormToRequest(formData),
        })
    }

    const defaultValues: ParticipantRequirementForm = {
        name: '',
        description: '',
        optional: false,
    }

    const formContext = useForm<ParticipantRequirementForm>()

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
                <FormInputText name="name" label={t('event.name')} required />
                <FormInputText name="description" label={t('entity.description')} />
                <FormInputCheckbox name="optional" label={t('entity.optional')} />
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(formData: ParticipantRequirementForm): ParticipantRequirementUpsertDto {
    return {
        name: formData.name,
        description: takeIfNotEmpty(formData.description),
        optional: formData.optional,
    }
}

function mapDtoToForm(dto: ParticipantRequirementDto): ParticipantRequirementForm {
    return {
        name: dto.name,
        description: dto.description ?? '',
        optional: dto.optional,
    }
}

export default ParticipantRequirementDialog
