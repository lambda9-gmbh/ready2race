import {BaseEntityDialogProps} from '@utils/types.ts'
import {TaskDto, TaskUpsertDto} from '@api/types.gen.ts'
import {useTranslation} from 'react-i18next'
import {addTask, updateTask} from '@api/sdk.gen.ts'
import {useForm} from 'react-hook-form-mui'
import {useCallback} from 'react'
import EntityDialog from '@components/EntityDialog.tsx'
import {Stack} from '@mui/material'
import {FormInputText} from '@components/form/input/FormInputText.tsx'
import FormInputDateTime from '@components/form/input/FormInputDateTime.tsx'
import {FormInputToggleButtonGroup} from '@components/form/input/FormInputToggleButtonGroup.tsx'
import {FormInputAutocompleteUser} from '@components/form/input/FormInputAutocompleteUser.tsx'
import {TaskStateIcon} from '@components/event/task/TaskStateIcon.tsx'

const TaskDialog = (props: BaseEntityDialogProps<TaskDto> & {eventId: string}) => {
    const {t} = useTranslation()

    const addAction = (formData: TaskUpsertDto) => {
        return addTask({
            path: {eventId: props.eventId},
            body: formData,
        })
    }

    const editAction = (formData: TaskUpsertDto, entity: TaskDto) => {
        return updateTask({
            path: {taskId: entity.id, eventId: props.eventId},
            body: formData,
        })
    }

    const defaultValues: TaskUpsertDto = {
        name: '',
        description: '',
        state: 'OPEN',
        responsibleUsers: [],
    }

    const formContext = useForm<TaskUpsertDto>()

    const onOpen = useCallback(() => {
        formContext.reset(props.entity ? mapDtoToForm(props.entity) : defaultValues)
    }, [props.entity])

    return (
        <EntityDialog
            {...props}
            formContext={formContext}
            onOpen={onOpen}
            showSaveAndNew={true}
            addAction={addAction}
            editAction={editAction}>
            <Stack spacing={4}>
                <FormInputText name="name" label={t('event.name')} required />
                <FormInputText name="description" label={t('entity.description')} />
                <FormInputDateTime name="dueDate" label={t('task.dueDate')} />
                <FormInputToggleButtonGroup
                    exclusive
                    required
                    enforceAtLeastOneSelected
                    name="state"
                    label={t('task.state')}
                    options={[
                        {
                            id: 'OPEN',
                            label: <TaskStateIcon state={'OPEN'} showLabel={true} />,
                        },
                        {
                            id: 'IN_PROGRESS',
                            label: <TaskStateIcon state={'IN_PROGRESS'} showLabel={true} />,
                        },
                        {
                            id: 'DONE',
                            label: <TaskStateIcon state={'DONE'} showLabel={true} />,
                        },
                        {
                            id: 'CANCELED',
                            label: <TaskStateIcon state={'CANCELED'} showLabel={true} />,
                        },
                    ]}
                />
                <FormInputAutocompleteUser
                    name="responsibleUsers"
                    label={t('task.responsibleUsers')}
                />
                <FormInputText multiline={true} rows={5} name="remark" label={t('entity.remark')} />
            </Stack>
        </EntityDialog>
    )
}

function mapDtoToForm(dto: TaskDto): TaskUpsertDto {
    return {
        name: dto.name,
        description: dto.description,
        dueDate: dto.dueDate,
        remark: dto.remark,
        state: dto.state,
        responsibleUsers: dto.responsibleUsers.map(u => u.id),
    }
}

export default TaskDialog
