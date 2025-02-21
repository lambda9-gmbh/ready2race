import {BaseEntityDialogProps} from '../../utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityDialog from '../EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '../form/input/FormInputText.tsx'
import {useForm} from 'react-hook-form-mui'
import {useCallback} from 'react'
import {addClubParticipant, ParticipantDto, ParticipantUpsertDto, updateClubParticipant} from '../../api'
import FormInputNumber from '../form/input/FormInputNumber.tsx'
import {clubIndexRoute} from '../../routes.tsx'

type ParticipantForm = {
    firstname: string
    lastname: string
    year?: number | null
    gender: 'M' | 'F' | 'O'
    phone?: string | null
    external?: boolean | null
    externalClubName?: string | null
}

const ParticipantDialog = (props: BaseEntityDialogProps<ParticipantDto>) => {
    const {t} = useTranslation()

    const {clubId} = clubIndexRoute.useParams()

    const addAction = (formData: ParticipantForm) => {
        return addClubParticipant({
            path: {clubId},
            body: mapFormToRequest(formData),
        })
    }

    const editAction = (formData: ParticipantForm, entity: ParticipantDto) => {
        return updateClubParticipant({
            path: {clubId, participantId: entity.id},
            body: mapFormToRequest(formData),
        })
    }

    const defaultValues: ParticipantForm = {
        firstname: '',
        lastname: '',
        gender: 'M',
    }

    const formContext = useForm<ParticipantForm>()

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
            <Stack spacing={2}>
                <FormInputText name={'firstname'} label={t('entity.firstname')} required />
                <FormInputText name={'lastname'} label={t('entity.lastname')} required />
                <FormInputNumber name={'year'} label={t('club.participant.year')} />
                <FormInputText name={'phone'} label={t('entity.phone')} />
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(formData: ParticipantForm): ParticipantUpsertDto {
    return {
        firstname: formData.firstname,
        lastname: formData.lastname,
        year: formData.year,
        gender: formData.gender,
        phone: formData.phone,
        external: formData.external,
        externalClubName: formData.externalClubName,
    }
}

function mapDtoToForm(dto: ParticipantDto): ParticipantForm {
    return {
        firstname: dto.firstname,
        lastname: dto.lastname,
        year: dto.year,
        gender: dto.gender,
        phone: dto.phone,
        external: dto.external,
        externalClubName: dto.externalClubName,
    }
}

export default ParticipantDialog
