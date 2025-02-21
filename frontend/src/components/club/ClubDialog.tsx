import {addClub, ClubDto, ClubUpsertDto, updateClub} from '../../api'
import {BaseEntityDialogProps} from '../../utils/types.ts'
import {useTranslation} from 'react-i18next'
import EntityDialog from '../EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '../form/input/FormInputText.tsx'
import {useForm} from 'react-hook-form-mui'
import {useCallback} from 'react'

type ClubForm = {
    name: string
}

const addAction = (formData: ClubForm) => {
    return addClub({
        body: mapFormToRequest(formData),
    })
}

const editAction = (formData: ClubForm, entity: ClubDto) => {
    return updateClub({
        path: {clubId: entity.id},
        body: mapFormToRequest(formData),
    })
}

const ClubDialog = (props: BaseEntityDialogProps<ClubDto>) => {
    const {t} = useTranslation()

    const defaultValues: ClubForm = {
        name: '',
    }

    const formContext = useForm<ClubForm>()

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
                <FormInputText name={'name'} label={t('entity.name')} required />
            </Stack>
        </EntityDialog>
    )
}

function mapFormToRequest(formData: ClubForm): ClubUpsertDto {
    return {
        name: formData.name,
    }
}

function mapDtoToForm(dto: ClubDto): ClubForm {
    return {
        name: dto.name,
    }
}

export default ClubDialog
