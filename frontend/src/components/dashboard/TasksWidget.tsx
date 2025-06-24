import React, {useState} from 'react'
import {
    Box,
    Button,
    Divider,
    IconButton,
    List,
    ListItem,
    ListItemText,
    Stack,
    Typography,
} from '@mui/material'
import {ClockIcon} from '@mui/x-date-pickers'
import {useTranslation} from 'react-i18next'
import {DashboardWidget} from '@components/dashboard/DashboardWidget.tsx'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getOpenTasksForUser} from '@api/sdk.gen.ts'
import {Link} from '@tanstack/react-router'
import {TaskStateIcon} from '@components/event/task/TaskStateIcon.tsx'
import TaskDialog from '@components/event/task/TaskDialog.tsx'
import {Edit} from '@mui/icons-material'
import {TaskDto} from '@api/types.gen.ts'
import {EVENT_ORGANISATION_TAB_INDEX} from '../../pages/event/EventPage.tsx'

export function TasksWidget({userId}: {userId: string}) {
    const feedback = useFeedback()
    const {t} = useTranslation()
    const [showDialog, setShowDialog] = useState(false)
    const [lastRequested, setLastRequested] = useState(Date.now())
    const [selectedEntity, setSelectedEntity] = useState<TaskDto | undefined>()

    const {data: tasks} = useFetch(
        signal =>
            getOpenTasksForUser({
                signal,
                path: {userId},
                query: {
                    sort: JSON.stringify([{field: 'CREATED_AT', direction: 'DESC'}]),
                },
            }),
        {
            onResponse: ({error}) => {
                if (error) {
                    feedback.error(
                        t('common.load.error.single', {
                            entity: t('task.task'),
                        }),
                    )
                    console.log(error)
                }
            },
            deps: [lastRequested],
        },
    )

    const onEdit = (task: TaskDto) => {
        setSelectedEntity(task)
        setShowDialog(true)
    }

    return (
        <React.Fragment>
            <DashboardWidget
                color={'#e9c46a75'}
                size={12}
                header={`${t('task.myTasks')} (${tasks?.pagination.total ?? 0})`}
                content={
                    <React.Fragment>
                        <List>
                            <TaskDialog
                                eventId={selectedEntity?.event ?? ''}
                                entity={selectedEntity}
                                entityName={t('task.task')}
                                dialogIsOpen={showDialog}
                                closeDialog={() => setShowDialog(false)}
                                reloadData={() => setLastRequested(Date.now())}
                            />
                            {tasks?.data?.map((task, index) => (
                                <>
                                    {index !== 0 && (
                                        <Divider key={'div-' + task.id} variant={'middle'} />
                                    )}
                                    <ListItem key={task.id}>
                                        <ListItemText
                                            primary={
                                                <Stack
                                                    direction={'row'}
                                                    spacing={2}
                                                    alignItems={'center'}>
                                                    <Box width={150}>
                                                        <TaskStateIcon
                                                            state={task.state}
                                                            showLabel={true}
                                                        />
                                                    </Box>
                                                    <Typography>{task.name}</Typography>
                                                    <IconButton onClick={() => onEdit(task)}>
                                                        <Edit
                                                            color={'primary'}
                                                            fontSize={'small'}
                                                        />
                                                    </IconButton>
                                                    <Typography>|</Typography>
                                                    <Link
                                                        to={'/event/$eventId'}
                                                        search={{
                                                            tabIndex: EVENT_ORGANISATION_TAB_INDEX,
                                                        }}
                                                        params={{eventId: task.event}}>
                                                        <Button>
                                                            <Typography color={'primary'}>
                                                                {task.eventName}
                                                            </Typography>
                                                        </Button>
                                                    </Link>
                                                    <Stack
                                                        flexGrow={1}
                                                        direction={'row'}
                                                        alignItems={'center'}
                                                        justifyContent={'end'}
                                                        color={'textSecondary'}
                                                        spacing={1}>
                                                        <ClockIcon fontSize={'small'} />
                                                        <Typography
                                                            fontSize={'small'}
                                                            color={'textSecondary'}>
                                                            {new Date(
                                                                task.createdAt,
                                                            ).toLocaleString()}
                                                        </Typography>
                                                    </Stack>
                                                </Stack>
                                            }
                                        />
                                    </ListItem>
                                </>
                            ))}
                        </List>
                    </React.Fragment>
                }
            />
        </React.Fragment>
    )
}
