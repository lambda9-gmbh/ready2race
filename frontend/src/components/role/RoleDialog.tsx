import EntityDialog from '../EntityDialog.tsx'
import {BaseEntityDialogProps} from '../../utils/types.ts'
import {addRole, RoleDto, RoleRequest, updateRole} from '../../api'
import {useForm} from 'react-hook-form-mui'
import {useTranslation} from 'react-i18next'

type RoleForm = {
    name: string
    description: string
    privileges: string[]
}

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
    const {t} = useTranslation()
    const formContext = useForm<RoleForm>()

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            title={action => t(`entity.${action}.action`, {entity: t('role.role')})}
            addAction={addAction}
            editAction={editAction}
            onSuccess={() => null}
        />
    )
}

export default RoleDialog
