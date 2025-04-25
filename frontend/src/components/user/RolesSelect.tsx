import {MultiSelectElement} from 'react-hook-form-mui'
import {RoleDto} from '@api/types.gen.ts'
import {useTranslation} from "react-i18next";

type Props = {
    availableRoles?: Array<RoleDto>
}

const RolesSelect = (props: Props) => {
    const {t} = useTranslation()
    return (
        <MultiSelectElement
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
            itemKey={'id'}
            itemValue={'id'}
            itemLabel={'label'}
            showCheckbox={true}
            showChips={true}
        />
    )
}

export default RolesSelect
