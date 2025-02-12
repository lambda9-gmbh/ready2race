import {BaseEntityDialogProps} from '../../../../utils/types.ts'
import {
    addNamedParticipant,
    NamedParticipantDto,
    NamedParticipantRequest,
    updateNamedParticipant,
} from '../../../../api'
import {useTranslation} from 'react-i18next'
import {useForm} from 'react-hook-form-mui'
import EntityDialog from '../../../EntityDialog.tsx'
import {Stack} from '@mui/material'
import {useCallback} from 'react'
import {takeIfNotEmpty} from '../../../../utils/ApiUtils.ts'
import {FormInputText} from '../../../form/input/FormInputText.tsx'

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

    const entityNameKey = {entity: t('event.race.namedParticipant.namedParticipant')}

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            title={action =>
                action === 'add'
                    ? t('entity.add.action', entityNameKey)
                    : t('entity.edit.action', entityNameKey)
            }
            addAction={addAction}
            editAction={editAction}>
            <Stack spacing={2}>
                <FormInputText name="name" label={t('entity.name')} required />
                <FormInputText name="description" label={t('entity.description')} />
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
