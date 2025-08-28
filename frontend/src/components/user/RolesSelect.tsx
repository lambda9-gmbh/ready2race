import {RoleDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import FormInputMultiselect from '@components/form/input/FormInputMultiselect.tsx'

type Props = {
    availableRoles?: Array<RoleDto>
}

const RolesSelect = (props: Props) => {
    const {t} = useTranslation()
    return (
        <FormInputMultiselect
            name={'roles'}
            label={t('role.roles')}
            options={
                props.availableRoles
                    ?.sort((a, b) => {
                        if (a.name < b.name) {
                            return 1
                        } else if (a.name > b.name) {
                            return -1
                        } else {
                            return 0
                        }
                    })
                    .map(r => ({
                        id: r.id,
                        label: r.name,
                    })) ?? []
            }
            showCheckbox
            showChips
            fullWidth
        />
    )
}

export default RolesSelect
