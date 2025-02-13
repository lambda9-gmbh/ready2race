import EntityDialog from '../EntityDialog.tsx'
import {BaseEntityDialogProps} from '../../utils/types.ts'
import {addRole, RoleDto, RoleRequest, updateRole} from '../../api'
import {useForm} from 'react-hook-form-mui'
import {useCallback} from 'react'

type RoleForm = {
    name: string
    description: string
    privileges: string[]
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
    description: formData.description, // todo: after merger use function takeIfNotEmpty
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
            editAction={editAction}
        />
    )
}

export default RoleDialog
