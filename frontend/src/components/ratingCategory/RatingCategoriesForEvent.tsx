import {useState} from 'react'
import {
    Box,
    Button,
    DialogActions,
    DialogContent,
    DialogTitle,
    IconButton,
    List,
    ListItem,
    ListItemText,
    MenuItem,
    Stack,
    Typography,
} from '@mui/material'
import {
    Add as AddIcon,
    ArrowDownward as ArrowDownwardIcon,
    ArrowUpward as ArrowUpwardIcon,
    Delete as DeleteIcon,
} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import {useFeedback, useFetch} from '@utils/hooks'
import {
    assignRatingCategoriesToEvent,
    getRatingCategories,
    getRatingCategoriesForEvent,
    removeRatingCategoryFromEvent,
    updateRatingCategoryOrderForEvent,
} from '@api/sdk.gen'
import {RatingCategoryToEventDto} from '@api/types.gen'
import Throbber from '@components/Throbber'
import {eventRoute} from '@routes'
import BaseDialog from '@components/BaseDialog'
import {FormContainer, useForm} from 'react-hook-form-mui'
import {SubmitButton} from '@components/form/SubmitButton'
import FormInputNumber from '@components/form/input/FormInputNumber'
import {useConfirmation} from '@contexts/confirmation/ConfirmationContext'
import {FormInputText} from '@components/form/input/FormInputText'

type RatingCategoryForm = {
    ratingCategoryId: string
    yearFrom?: number | null
    yearTo?: number | null
}

const RatingCategoriesForEvent = () => {
    const {t} = useTranslation()
    const feedback = useFeedback()
    const {confirmAction} = useConfirmation()

    const {eventId} = eventRoute.useParams()

    const [submitting, setSubmitting] = useState(false)

    const [dialogOpen, setDialogOpen] = useState(false)
    const [reloadKey, setReloadKey] = useState(0)
    const reload = () => setReloadKey(prev => prev + 1)

    const formContext = useForm<RatingCategoryForm>({
        defaultValues: {
            ratingCategoryId: '',
            yearFrom: null,
            yearTo: null,
        },
    })

    const {data: assignedCategories, pending: loadingAssigned} = useFetch(
        signal => getRatingCategoriesForEvent({signal, path: {eventId}}),
        {
            deps: [eventId, reloadKey],
        },
    )

    const {data: allCategories, pending: loadingAll} = useFetch(signal =>
        getRatingCategories({signal}),
    )

    const handleAdd = async (formData: RatingCategoryForm) => {
        if (!formData.ratingCategoryId) {
            return
        }
        setSubmitting(true)

        const {error} = await assignRatingCategoriesToEvent({
            path: {eventId},
            body: {
                ratingCategories: [
                    {
                        ratingCategory: formData.ratingCategoryId,
                        yearFrom: formData.yearFrom ?? undefined,
                        yearTo: formData.yearTo ?? undefined,
                    },
                ],
            },
        })
        setSubmitting(false)

        if (error) {
            feedback.error(t('event.ratingCategory.add.error'))
        } else {
            feedback.success(t('event.ratingCategory.add.success'))
            setDialogOpen(false)
            formContext.reset()
            reload()
        }
    }

    const handleRemove = async (category: RatingCategoryToEventDto) => {
        confirmAction(
            async () => {
                setSubmitting(true)
                const {error} = await removeRatingCategoryFromEvent({
                    path: {eventId, ratingCategoryId: category.ratingCategory.id},
                })
                setSubmitting(false)

                if (error) {
                    feedback.error(t('event.ratingCategory.delete.error'))
                } else {
                    feedback.success(t('event.ratingCategory.delete.success'))
                    reload()
                }
            },
            {
                title: t('event.ratingCategory.delete.confirm.title'),
            },
        )
    }

    /**
     * Verschiebt eine Kategorie um eine Stelle. Geschickt wird immer die vollstaendige
     * Reihenfolge - so kann eine zweite offene Konfigurationsseite keine halbvertauschte
     * Reihenfolge hinterlassen.
     */
    const handleMove = async (index: number, direction: -1 | 1) => {
        if (!assignedCategories) return

        const order = assignedCategories.map(it => it.ratingCategory.id)
        const target = index + direction
        if (target < 0 || target >= order.length) return
        ;[order[index], order[target]] = [order[target], order[index]]

        setSubmitting(true)
        const {error} = await updateRatingCategoryOrderForEvent({
            path: {eventId},
            body: {ratingCategories: order},
        })
        setSubmitting(false)

        if (error) {
            feedback.error(t('common.error.unexpected'))
        } else {
            reload()
        }
    }

    const availableCategories =
        allCategories?.data.filter(
            cat => !assignedCategories?.some(assigned => assigned.ratingCategory.id === cat.id),
        ) ?? []

    if (loadingAssigned || loadingAll) {
        return <Throbber />
    }

    return (
        <>
            {/* Blanker Abschnitt statt Karte: die Nachbarn im Einstellungen-Tab (Zeitnahme,
                Dokumente, Teilnahmebedingungen) sind h2-Überschrift plus Inhalt darunter, und die
                Anlegen-Schaltfläche sitzt dort rechts über der Liste — siehe EntityTable. */}
            <Box>
                <Typography variant={'h2'}>{t('event.ratingCategory.title')}</Typography>
                <Box display={'flex'} justifyContent={'flex-end'} mb={1} pt={1}>
                    <Button
                        variant={'outlined'}
                        startIcon={<AddIcon />}
                        onClick={() => setDialogOpen(true)}
                        disabled={availableCategories.length === 0}>
                        {/* „zuweisen", nicht „anlegen": die Kategorien selbst entstehen in der
                            Konfiguration, hier wird eine bestehende an die Veranstaltung gehängt. */}
                        {t('event.ratingCategory.add.title')}
                    </Button>
                </Box>
                {assignedCategories && assignedCategories.length > 1 && (
                    <Typography variant={'body2'} color={'text.secondary'}>
                        {t('event.ratingCategory.order.hint')}
                    </Typography>
                )}
                <List sx={{maxWidth: 500}}>
                    {assignedCategories?.map((category, index) => (
                        <ListItem
                            key={category.ratingCategory.id}
                            secondaryAction={
                                <Stack direction={'row'}>
                                    <IconButton
                                        edge="end"
                                        title={t('event.ratingCategory.order.moveUp')}
                                        disabled={submitting || index === 0}
                                        onClick={() => handleMove(index, -1)}>
                                        <ArrowUpwardIcon />
                                    </IconButton>
                                    <IconButton
                                        edge="end"
                                        title={t('event.ratingCategory.order.moveDown')}
                                        disabled={
                                            submitting || index === assignedCategories.length - 1
                                        }
                                        onClick={() => handleMove(index, 1)}>
                                        <ArrowDownwardIcon />
                                    </IconButton>
                                    <IconButton
                                        edge="end"
                                        onClick={() => handleRemove(category)}
                                        className="cursor-pointer">
                                        <DeleteIcon />
                                    </IconButton>
                                </Stack>
                            }>
                            <ListItemText
                                primary={category.ratingCategory.name}
                                secondary={
                                    category.yearFrom || category.yearTo
                                        ? `${t('event.ratingCategory.yearFrom')}: ${category.yearFrom ?? '-'} | ${t('event.ratingCategory.yearTo')}: ${category.yearTo ?? '-'}`
                                        : undefined
                                }
                            />
                        </ListItem>
                    ))}
                    {(!assignedCategories || assignedCategories.length === 0) && (
                        <ListItem>
                            <ListItemText primary={t('event.ratingCategory.noEntries')} />
                        </ListItem>
                    )}
                </List>
            </Box>

            <BaseDialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm">
                <DialogTitle>{t('event.ratingCategory.add.title')}</DialogTitle>
                <FormContainer formContext={formContext} onSuccess={handleAdd}>
                    <DialogContent>
                        <Stack spacing={3}>
                            <FormInputText
                                name="ratingCategoryId"
                                label={t('configuration.ratingCategory.ratingCategory')}
                                select
                                required>
                                {availableCategories.map(cat => (
                                    <MenuItem key={cat.id} value={cat.id}>
                                        {cat.name}
                                    </MenuItem>
                                ))}
                            </FormInputText>
                            <Typography variant={'h6'}>
                                {t('event.ratingCategory.ageRestriction')}
                            </Typography>
                            <FormInputNumber
                                name="yearFrom"
                                label={`${t('event.ratingCategory.yearFrom')} (${t('event.ratingCategory.year')})`}
                            />
                            <FormInputNumber
                                name="yearTo"
                                label={`${t('event.ratingCategory.yearTo')} (${t('event.ratingCategory.year')})`}
                            />
                        </Stack>
                    </DialogContent>
                    <DialogActions>
                        <Button onClick={() => setDialogOpen(false)} className="cursor-pointer">
                            {t('common.cancel')}
                        </Button>
                        <SubmitButton submitting={submitting}>{t('common.add')}</SubmitButton>
                    </DialogActions>
                </FormContainer>
            </BaseDialog>
        </>
    )
}

export default RatingCategoriesForEvent
