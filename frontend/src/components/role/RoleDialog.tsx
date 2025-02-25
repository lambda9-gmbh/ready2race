import EntityDialog from '@components/EntityDialog.tsx'
import {BaseEntityDialogProps} from '@utils/types.ts'
import {useForm} from 'react-hook-form-mui'
import {useCallback} from 'react'
import {Stack} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {addRole, updateRole} from "@api/sdk.gen.ts";
import {RoleDto, RoleRequest} from "@api/types.gen.ts";

type RoleForm = {
    name: string
    description: string
    privileges: string[] // todo: implement me
}

const defaultValues: RoleForm = {
    name: '',
    description: '',
    privileges: [],
}

const mapDtoToForm = (dto: RoleDto): RoleForm => ({
    name: dto.name,
    description: dto.description ?? '',
    privileges: dto.privileges.map(p => p.id),
})

const mapFormToRequest = (formData: RoleForm): RoleRequest => ({
    name: formData.name,
    description: takeIfNotEmpty(formData.description),
    privileges: formData.privileges,
})

const addAction = (formData: RoleForm) =>
    addRole({
        body: mapFormToRequest(formData),
    })

const editAction = (formData: RoleForm, entity: RoleDto) =>
    updateRole({
        path: {roleId: entity.id},
        body: mapFormToRequest(formData),
    })

const RoleDialog = (props: BaseEntityDialogProps<RoleDto>) => {
    const formContext = useForm<RoleForm>()

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
                <FormInputText name={'name'} label={'[todo] Bezeichnung'} required />
                <FormInputText name={'description'} label={'[todo] Beschreibung'} />
            </Stack>
        </EntityDialog>
    )
}

export default RoleDialog
