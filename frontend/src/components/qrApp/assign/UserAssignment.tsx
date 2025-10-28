import {
    Box,
    Card,
    CardActionArea,
    CardContent,
    Stack,
    Typography,
} from '@mui/material'
import React, {useMemo} from 'react'
import {useFetch} from '@utils/hooks.ts'
import {getUsersForEvent} from '@api/sdk.gen.ts'
import {useTranslation} from 'react-i18next'
import PersonIcon from '@mui/icons-material/Person'
import AssignmentSearchField from "@components/qrApp/assign/AssignmentSearchField.tsx";

interface UserAssignmentProps {
    eventId: string
    onSelectUser: (user: {id: string; firstname: string; lastname: string}) => void
}

const UserAssignment: React.FC<UserAssignmentProps> = ({eventId, onSelectUser}) => {
    const {t} = useTranslation()
    const [searchQuery, setSearchQuery] = React.useState('')

    const users = useFetch(signal => getUsersForEvent({signal, path: {eventId}})).data

    const filteredUsers = useMemo(() => {
        return users?.data
            .filter(
                user =>
                    searchQuery === '' ||
                    `${user.firstname} ${user.lastname}`.toLowerCase().includes(searchQuery.toLowerCase()),
            )
            .sort((a, b) => {
                const nameA = `${a.firstname} ${a.lastname}`.toLowerCase()
                const nameB = `${b.firstname} ${b.lastname}`.toLowerCase()
                return nameA.localeCompare(nameB)
            })
    },[searchQuery, users])

    return (
        <Stack sx={{width: '100%'}} spacing={2}>
            <Typography variant="subtitle1" fontWeight="medium">
                {t('qrAssign.users')}
            </Typography>

            <AssignmentSearchField setSearchQuery={setSearchQuery} />

            <Stack spacing={1}>
                {filteredUsers && filteredUsers.length > 0 ? (
                    filteredUsers.map(user => (
                        <Card
                            key={user.id}
                            sx={{
                                opacity: user.qrCodeId ? 0.6 : 1,
                                cursor: user.qrCodeId ? 'not-allowed' : 'pointer',
                            }}>
                            <CardActionArea
                                onClick={() => !user.qrCodeId && onSelectUser(user)}
                                disabled={user.qrCodeId !== undefined}>
                                <CardContent sx={{py: 2}}>
                                    <Box sx={{display: 'flex', alignItems: 'center', gap: 1}}>
                                        <PersonIcon sx={{color: 'text.secondary'}} />
                                        <Typography variant="body1" fontWeight="medium">
                                            {user.firstname} {user.lastname}
                                        </Typography>
                                    </Box>
                                </CardContent>
                            </CardActionArea>
                        </Card>
                    ))
                ) : (
                    <Typography align="center" color="text.secondary">
                        {t('qrAssign.noData')}
                    </Typography>
                )}
            </Stack>
        </Stack>
    )
}

export default UserAssignment
