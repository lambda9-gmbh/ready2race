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
    useTheme,
} from '@mui/material'
import {ClockIcon} from '@mui/x-date-pickers'
import {useTranslation} from 'react-i18next'
import {DashboardWidget} from '@components/dashboard/DashboardWidget.tsx'
import {useFeedback, useFetch} from '@utils/hooks.ts'
import {getOpenTasksForUser} from '@api/sdk.gen.ts'
import {Link} from '@tanstack/react-router'
import {TaskStateIcon} from '@components/event/task/TaskStateIcon.tsx'
import TaskDialog from '@components/event/task/TaskDialog.tsx'
import {Edit, TaskAlt} from '@mui/icons-material'
import {TaskDto} from '@api/types.gen.ts'

export function TasksWidget({userId}: {userId: string}) {
    const feedback = useFeedback()
    const {t} = useTranslation()
    const theme = useTheme()
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
                size={{xs: 12, lg: 6}}
                header={`${t('task.myTasks')} (${tasks?.pagination.total ?? 0})`}
                content={
                    <React.Fragment>
                        <TaskDialog
                            eventId={selectedEntity?.event ?? ''}
                            entity={selectedEntity}
                            entityName={t('task.task')}
                            dialogIsOpen={showDialog}
                            closeDialog={() => setShowDialog(false)}
                            reloadData={() => setLastRequested(Date.now())}
                        />
                        {tasks?.data && tasks.data.length === 0 ? (
                            <Stack
                                alignItems={'center'}
                                justifyContent={'center'}
                                spacing={2}
                                py={2}>
                                <TaskAlt sx={{fontSize: 48, color: 'text.secondary'}} />
                                <Typography color={'text.secondary'} textAlign={'center'}>
                                    No open tasks
                                </Typography>
                            </Stack>
                        ) : (
                            <List>
                                {tasks?.data?.map((task, index) => (
                                    <React.Fragment key={task.id}>
                                        {index !== 0 && <Divider variant={'middle'} />}
                                        <ListItem>
                                            <ListItemText
                                                primary={
                                                    <Box
                                                        sx={{
                                                            display: 'flex',
                                                            flexWrap: 'wrap',
                                                            gap: 1,
                                                            alignItems: 'center',
                                                        }}>
                                                        <Box
                                                            sx={{
                                                                [theme.breakpoints.up('md')]: {
                                                                    width: 150,
                                                                },
                                                            }}>
                                                            <TaskStateIcon
                                                                state={task.state}
                                                                showLabel={true}
                                                            />
                                                        </Box>
                                                        <Typography sx={{flexGrow: 1}}>
                                                            {task.name}
                                                        </Typography>
                                                        <IconButton
                                                            onClick={() => onEdit(task)}
                                                            size={'small'}>
                                                            <Edit
                                                                color={'primary'}
                                                                fontSize={'small'}
                                                            />
                                                        </IconButton>
                                                        <Box
                                                            sx={{
                                                                display: {xs: 'none', md: 'block'},
                                                            }}>
                                                            <Typography>|</Typography>
                                                        </Box>
                                                        <Link
                                                            to={'/event/$eventId'}
                                                            search={{
                                                                tab: 'organization',
                                                            }}
                                                            params={{eventId: task.event}}>
                                                            <Button size={'small'}>
                                                                <Typography color={'primary'}>
                                                                    {task.eventName}
                                                                </Typography>
                                                            </Button>
                                                        </Link>
                                                        <Stack
                                                            direction={'row'}
                                                            alignItems={'center'}
                                                            spacing={1}
                                                            sx={{
                                                                marginLeft: {xs: 0, md: 'auto'},
                                                                width: {xs: '100%', md: 'auto'},
                                                                justifyContent: {
                                                                    xs: 'flex-start',
                                                                    md: 'flex-end',
                                                                },
                                                            }}>
                                                            <ClockIcon fontSize={'small'} />
                                                            <Typography
                                                                fontSize={'small'}
                                                                color={'textSecondary'}>
                                                                {new Date(
                                                                    task.createdAt,
                                                                ).toLocaleString()}
                                                            </Typography>
                                                        </Stack>
                                                    </Box>
                                                }
                                            />
                                        </ListItem>
                                    </React.Fragment>
                                ))}
                            </List>
                        )}
                    </React.Fragment>
                }
            />
        </React.Fragment>
    )
}
