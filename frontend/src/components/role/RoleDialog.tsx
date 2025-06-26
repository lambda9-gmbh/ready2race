import EntityDialog from '@components/EntityDialog.tsx'
import {BaseEntityDialogProps} from '@utils/types.ts'
import {MultiSelectElement, useForm} from 'react-hook-form-mui'
import {useCallback} from 'react'
import {Stack} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {takeIfNotEmpty} from '@utils/ApiUtils.ts'
import {addRole, getPrivileges, updateRole} from '@api/sdk.gen.ts'
import {RoleDto, RoleRequest} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {useFetch} from "@utils/hooks.ts";
import {scopeLevel} from "@utils/helpers.ts";

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
    const {t} = useTranslation()
    const formContext = useForm<RoleForm>()

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])

    const {data} = useFetch(
        signal => getPrivileges({signal}), // The backend checks for the privilege READUserGlobal (Might be unnecessary since the typescript files have the same privileges listed)
    )

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            addAction={addAction}
            editAction={editAction}>
            <Stack spacing={4}>
                <FormInputText name={'name'} label={t('role.name')} required/>
                <FormInputText name={'description'} label={t('role.description')}/>
                <MultiSelectElement
                    name={'privileges'}
                    label={'Rechte'}
                    options={
                        data?.sort((a, b) => {
                            if (a.resource > b.resource) {
                                return 1
                            } else if (a.resource < b.resource) {
                                return -1
                            } else {
                                if (a.action > b.action) {
                                    return 1
                                } else if (a.action < b.action) {
                                    return -1
                                } else {
                                    const scopeDiff = scopeLevel[a.scope] - scopeLevel[b.scope]
                                    if (scopeDiff >= 0) {
                                        return 1
                                    } else {
                                        return -1
                                    }
                                }
                            }
                        }).map(p => ({
                            id: p.id,
                            label: p.action + '.' + p.resource + '.' + p.scope
                        })) ?? []
                    }
                    itemKey={'id'}
                    itemValue={'id'}
                    itemLabel={'label'}
                    showCheckbox={true}
                    showChips={true}
                />
            </Stack>
        </EntityDialog>
    )
}

export default RoleDialog
