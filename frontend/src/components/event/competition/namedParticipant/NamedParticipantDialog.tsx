import {BaseEntityDialogProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import {useForm} from 'react-hook-form-mui'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {useCallback} from 'react'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {addNamedParticipant, updateNamedParticipant} from '@api/sdk.gen'
import {NamedParticipantDto, NamedParticipantRequest} from '@api/types.gen.ts'

type NamedParticipantForm = {
    name: string
    description: string
}

const NamedParticipantDialog = (props: BaseEntityDialogProps<NamedParticipantDto>) => {
    const {t} = useTranslation()

    const addAction = (formData: NamedParticipantForm) => {
        return addNamedParticipant({
            body: mapFormToRequest(formData),
        })
    }

    const editAction = (formData: NamedParticipantForm, entity: NamedParticipantDto) => {
        return updateNamedParticipant({
            path: {namedParticipantId: entity.id},
            body: mapFormToRequest(formData),
        })
    }

    const defaultValues: NamedParticipantForm = {
        name: '',
        description: '',
    }

    const formContext = useForm<NamedParticipantForm>()

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
                <FormInputText
                    name="name"
                    label={t('event.competition.namedParticipant.name')}
                    required
                />
                <FormInputText
                    name="description"
                    label={t('event.competition.namedParticipant.description')}
                />
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(formData: NamedParticipantForm): NamedParticipantRequest {
    return {
        name: formData.name,
        description: takeIfNotEmpty(formData.description),
    }
}

function mapDtoToForm(dto: NamedParticipantDto): NamedParticipantForm {
    return {
        name: dto.name,
        description: dto.description ?? '',
    }
}

export default NamedParticipantDialog
