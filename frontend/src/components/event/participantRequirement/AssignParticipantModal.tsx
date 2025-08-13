import React, { useState, useMemo } from 'react'
import {
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Button,
    TextField,
    List,
    ListItem,
    ListItemButton,
    ListItemText,
    ListItemIcon,
    ListItemSecondaryAction,
    Box,
    InputAdornment,
    IconButton,
    Checkbox,
    FormControlLabel,
    Typography,
    Divider,
    Chip,
} from '@mui/material'
import { Search, Person, QrCode, Clear, Delete } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import { NamedParticipantDto, NamedParticipantRequirementForEventDto } from '@api/types.gen.ts'

interface AssignParticipantModalProps {
    open: boolean
    onClose: () => void
    onAssign: (namedParticipantId: string, qrCodeRequired: boolean) => Promise<void>
    onToggleQrCode: (namedParticipantId: string, currentQrCodeRequired: boolean) => Promise<void>
    onRemove: (namedParticipantId: string) => Promise<void>
    namedParticipants: NamedParticipantDto[]
    requirementName: string
    assignedRequirements: NamedParticipantRequirementForEventDto[]
}

const AssignParticipantModal: React.FC<AssignParticipantModalProps> = ({
    open,
    onClose,
    onAssign,
    onToggleQrCode,
    onRemove,
    namedParticipants,
    requirementName,
    assignedRequirements,
}) => {
    const { t } = useTranslation()
    const [searchTerm, setSearchTerm] = useState('')
    const [selectedParticipant, setSelectedParticipant] = useState<string | null>(null)
    const [qrCodeRequired, setQrCodeRequired] = useState(false)

    // Get specific assignments (not global)
    const specificAssignments = assignedRequirements.filter(req => req.id && req.id !== '')
    
    // Get available participants (not already assigned)
    const assignedParticipantIds = specificAssignments.map(req => req.id)
    const availableParticipants = useMemo(() => {
        return namedParticipants.filter(p => !assignedParticipantIds.includes(p.id))
    }, [namedParticipants, assignedParticipantIds])

    const filteredAvailableParticipants = useMemo(() => {
        if (!searchTerm) return availableParticipants
        
        const lowerSearch = searchTerm.toLowerCase()
        return availableParticipants.filter(
            (p) => p.name.toLowerCase().includes(lowerSearch)
        )
    }, [availableParticipants, searchTerm])

    const handleAssign = async () => {
        if (selectedParticipant) {
            await onAssign(selectedParticipant, qrCodeRequired)
            // Reset selection after successful assignment
            setSelectedParticipant(null)
            setQrCodeRequired(false)
        }
    }

    const handleClose = () => {
        setSearchTerm('')
        setSelectedParticipant(null)
        setQrCodeRequired(false)
        onClose()
    }

    return (
        <Dialog open={open} onClose={handleClose} maxWidth="md" fullWidth>
            <DialogTitle>
                {t('participantRequirement.manageParticipants')}
                <Typography variant="body2" color="text.secondary">
                    {requirementName}
                </Typography>
            </DialogTitle>
            <DialogContent>
                {/* Assigned Participants Section */}
                {specificAssignments.length > 0 && (
                    <>
                        <Typography variant="subtitle1" sx={{ mb: 1 }}>
                            {t('participantRequirement.assignedParticipants')}
                        </Typography>
                        <List sx={{ mb: 2 }}>
                            {specificAssignments.map((req) => {
                                const participant = namedParticipants.find(p => p.id === req.id)
                                return (
                                    <ListItem key={req.id}>
                                        <ListItemIcon>
                                            <Person />
                                        </ListItemIcon>
                                        <ListItemText 
                                            primary={participant?.name || req.name}
                                            secondary={req.qrCodeRequired ? (
                                                <Chip 
                                                    size="small" 
                                                    icon={<QrCode />} 
                                                    label={t('participantRequirement.qrCodeRequired' as any)} 
                                                />
                                            ) : null}
                                        />
                                        <ListItemSecondaryAction>
                                            <IconButton
                                                edge="end"
                                                onClick={() => onToggleQrCode(req.id, req.qrCodeRequired)}
                                                title={t('participantRequirement.toggleQrCode')}
                                            >
                                                <QrCode color={req.qrCodeRequired ? 'primary' : 'action'} />
                                            </IconButton>
                                            <IconButton
                                                edge="end"
                                                onClick={() => onRemove(req.id)}
                                                title={t('common.remove')}
                                            >
                                                <Delete />
                                            </IconButton>
                                        </ListItemSecondaryAction>
                                    </ListItem>
                                )
                            })}
                        </List>
                        <Divider sx={{ my: 2 }} />
                    </>
                )}

                {/* Available Participants Section */}
                <Typography variant="subtitle1" sx={{ mb: 1 }}>
                    {t('participantRequirement.availableParticipants')}
                </Typography>
                
                <Box sx={{ mb: 2 }}>
                    <TextField
                        fullWidth
                        placeholder={t('common.search')}
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        InputProps={{
                            startAdornment: (
                                <InputAdornment position="start">
                                    <Search />
                                </InputAdornment>
                            ),
                            endAdornment: searchTerm && (
                                <InputAdornment position="end">
                                    <IconButton
                                        size="small"
                                        onClick={() => setSearchTerm('')}
                                    >
                                        <Clear />
                                    </IconButton>
                                </InputAdornment>
                            ),
                        }}
                    />
                </Box>

                <List sx={{ maxHeight: 300, overflow: 'auto' }}>
                    {filteredAvailableParticipants.length === 0 ? (
                        <ListItem>
                            <ListItemText 
                                primary={t('common.noResults')}
                                secondary={searchTerm ? t('common.tryDifferentSearch') : t('participantRequirement.allParticipantsAssigned')}
                            />
                        </ListItem>
                    ) : (
                        filteredAvailableParticipants.map((participant) => (
                            <ListItem key={participant.id} disablePadding>
                                <ListItemButton
                                    selected={selectedParticipant === participant.id}
                                    onClick={() => setSelectedParticipant(participant.id)}
                                >
                                    <ListItemIcon>
                                        <Person />
                                    </ListItemIcon>
                                    <ListItemText primary={participant.name} />
                                </ListItemButton>
                            </ListItem>
                        ))
                    )}
                </List>

                {selectedParticipant && (
                    <Box sx={{ mt: 2 }}>
                        <FormControlLabel
                            control={
                                <Checkbox
                                    checked={qrCodeRequired}
                                    onChange={(e) => setQrCodeRequired(e.target.checked)}
                                    icon={<QrCode />}
                                    checkedIcon={<QrCode color="primary" />}
                                />
                            }
                            label={t('participantRequirement.requireQrCode')}
                        />
                    </Box>
                )}
            </DialogContent>
            <DialogActions>
                <Button onClick={handleClose}>{t('common.close')}</Button>
                {selectedParticipant && (
                    <Button 
                        onClick={handleAssign} 
                        variant="contained"
                    >
                        {t('common.assign')}
                    </Button>
                )}
            </DialogActions>
        </Dialog>
    )
}

export default AssignParticipantModal