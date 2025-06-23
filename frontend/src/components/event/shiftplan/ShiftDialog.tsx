import {BaseEntityDialogProps} from '@utils/types.ts'
import {useTranslation} from 'react-i18next'
import {useForm, useWatch} from 'react-hook-form-mui'
import {useCallback, useEffect, useMemo} from 'react'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import FormInputDateTime from '@components/form/input/FormInputDateTime.tsx'
import {FormInputAutocompleteUser} from '@components/form/input/FormInputAutocompleteUser.tsx'
import {addWorkShift, getWorkTypes, updateWorkShift} from '@api/sdk.gen.ts'
import {eventIndexRoute} from '@routes'
import {WorkShiftUpsertDto, WorkShiftWithAssignedUsersDto} from '@api/types.gen.ts'
import FormInputNumber from '@components/form/input/FormInputNumber.tsx'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import FormInputAutocomplete from '@components/form/input/FormInputAutocomplete.tsx'
import {useUser} from '@contexts/user/UserContext.ts'
import {createEventGlobal} from '@authorization/privileges.ts'

const ShiftDialog = (props: BaseEntityDialogProps<WorkShiftWithAssignedUsersDto>) => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const {eventId} = eventIndexRoute.useParams()
    const user = useUser()

    const userCanEdit = useMemo(() => user.checkPrivilege(createEventGlobal), [user])

    const {data: workTypes} = useFetch(signal => getWorkTypes({signal}), {
        onResponse: ({error}) => {
            if (error) {
                feedback.error(t('common.load.error.single', {entity: t('work.type.types')}))
            }
        },
    })

    const addAction = (formData: WorkShiftUpsertDto) =>
        addWorkShift({
            path: {
                eventId,
            },
            body: formData,
        })

    const editAction = (formData: WorkShiftUpsertDto, entity: WorkShiftWithAssignedUsersDto) =>
        entity.id == null || entity.id === ''
            ? addWorkShift({
                  path: {
                      eventId,
                  },
                  body: formData,
              })
            : updateWorkShift({
                  path: {
                      eventId,
                      workShiftId: entity.id,
                  },
                  body: formData,
              })

    const defaultValues: WorkShiftUpsertDto = {
        workType: '',
        timeFrom: new Date().toLocaleString(),
        timeTo: new Date().toLocaleString(),
        minUser: 0,
        assignedUsers: [],
    }

    const formContext = useForm<WorkShiftUpsertDto>()

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])

    const type = useWatch({control: formContext.control, name: 'workType'})

    useEffect(() => {
        const minUser = workTypes?.data.filter(wt => wt.id === type)?.[0]?.minUser
        if (minUser !== undefined) {
            formContext.setValue('minUser', minUser)
        }
    }, [type])

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            showSaveAndNew={true}
            disableSave={!userCanEdit}
            addAction={addAction}
            // @ts-ignore
            editAction={editAction}>
            <Stack spacing={4}>
                <FormInputAutocomplete
                    label={t('work.type.type')}
                    required
                    matchId
                    name={'workType'}
                    options={workTypes?.data || []}
                    autocompleteProps={{getOptionLabel: wt => wt.name, disabled: !userCanEdit}}
                />
                <FormInputDateTime
                    required
                    name="timeFrom"
                    label={t('work.shift.timeFrom')}
                    disabled={!userCanEdit}
                />
                <FormInputDateTime
                    required
                    name="timeTo"
                    label={t('work.shift.timeTo')}
                    disabled={!userCanEdit}
                />
                <FormInputNumber
                    required
                    name="minUser"
                    label={t('work.shift.minUser')}
                    disabled={!userCanEdit}
                />
                <FormInputNumber
                    name="maxUser"
                    label={t('work.shift.maxUser')}
                    disabled={!userCanEdit}
                />
                <FormInputText
                    name="remark"
                    label={t('work.shift.remark')}
                    disabled={!userCanEdit}
                />
                <FormInputAutocompleteUser
                    name="assignedUsers"
                    label={t('work.shift.assignedUsers')}
                    disabled={!userCanEdit}
                />
            </Stack>
        </EntityDialog>
    )
}

function mapDtoToForm(dto: WorkShiftWithAssignedUsersDto): WorkShiftUpsertDto {
    return {
        workType: dto.workType,
        timeFrom: dto.timeFrom,
        timeTo: dto.timeTo,
        minUser: dto.minUser,
        maxUser: dto.maxUser,
        remark: dto.remark,
        assignedUsers: dto.assignedUsers.map(u => u.id),
    }
}

export default ShiftDialog
