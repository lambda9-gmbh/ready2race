import {Box, IconButton, Paper, Stack, Typography} from '@mui/material'
import {Document, Page} from 'react-pdf'
import {useCallback, useEffect, useRef, useState} from 'react'
import {GapDocumentPlaceholderType, TextAlign} from '@api/types.gen.ts'
import 'react-pdf/dist/Page/AnnotationLayer.css'
import 'react-pdf/dist/Page/TextLayer.css'
import {Delete, DragIndicator} from '@mui/icons-material'
import {useTranslation} from 'react-i18next'
import '@utils/pdfWorker'

type PlaceholderData = {
    id: string
    name?: string
    type: GapDocumentPlaceholderType
    page: number
    relLeft: number
    relTop: number
    relWidth: number
    relHeight: number
    textAlign: TextAlign
}

type Props = {
    pdfFile: File | Blob | null
    placeholders: PlaceholderData[]
    onPlaceholdersChange: (placeholders: PlaceholderData[]) => void
    onAddPlaceholder: (type: GapDocumentPlaceholderType, page: number) => void
    selectedPlaceholder: string | null
    onSelectPlaceholder: (id: string | null) => void
}

const PdfPlaceholderEditor = (props: Props) => {
    const {t} = useTranslation()
    const [numPages, setNumPages] = useState<number>(0)
    const [pageWidth, setPageWidth] = useState<number>(0)
    const [pageDimensions, setPageDimensions] = useState<{width: number; height: number}>({
        width: 0,
        height: 0,
    })
    const containerRef = useRef<HTMLDivElement>(null)
    const [draggedPlaceholder, setDraggedPlaceholder] = useState<string | null>(null)
    const [resizingPlaceholder, setResizingPlaceholder] = useState<{
        id: string
        edge: 'se' | 'sw' | 'ne' | 'nw'
    } | null>(null)
    const [dragStart, setDragStart] = useState<{x: number; y: number} | null>(null)
    const [workerReady, setWorkerReady] = useState<boolean>(false)

    useEffect(() => {
        const updateWidth = () => {
            if (containerRef.current) {
                setPageWidth(containerRef.current.clientWidth - 40)
            }
        }
        updateWidth()
        window.addEventListener('resize', updateWidth)
        return () => window.removeEventListener('resize', updateWidth)
    }, [])

    useEffect(() => {
        // Wait a bit to ensure worker is loaded before rendering PDF
        const timer = setTimeout(() => {
            setWorkerReady(true)
        }, 200)
        return () => clearTimeout(timer)
    }, [])

    const onDocumentLoadSuccess = ({numPages}: {numPages: number}) => {
        setNumPages(numPages)
    }

    const onPageLoadSuccess = (page: {width: number; height: number}) => {
        // Update dimensions based on the actual rendered page
        setPageDimensions({
            width: page.width,
            height: page.height,
        })
    }

    const handleMouseDown = (
        e: React.MouseEvent,
        placeholderId: string,
        isResize?: boolean,
        edge?: 'se' | 'sw' | 'ne' | 'nw',
    ) => {
        e.stopPropagation()
        props.onSelectPlaceholder(placeholderId)

        if (isResize && edge) {
            setResizingPlaceholder({id: placeholderId, edge})
        } else {
            setDraggedPlaceholder(placeholderId)
        }
        setDragStart({x: e.clientX, y: e.clientY})
    }

    const handleMouseMove = useCallback(
        (e: MouseEvent) => {
            if (!dragStart) return

            const deltaX = e.clientX - dragStart.x
            const deltaY = e.clientY - dragStart.y

            if (draggedPlaceholder) {
                const placeholder = props.placeholders.find(p => p.id === draggedPlaceholder)
                if (!placeholder) return

                const relDeltaX = deltaX / pageDimensions.width
                const relDeltaY = deltaY / pageDimensions.height

                const newLeft = Math.max(
                    0,
                    Math.min(1 - placeholder.relWidth, placeholder.relLeft + relDeltaX),
                )
                const newTop = Math.max(
                    0,
                    Math.min(1 - placeholder.relHeight, placeholder.relTop + relDeltaY),
                )

                props.onPlaceholdersChange(
                    props.placeholders.map(p =>
                        p.id === draggedPlaceholder ? {...p, relLeft: newLeft, relTop: newTop} : p,
                    ),
                )
                setDragStart({x: e.clientX, y: e.clientY})
            } else if (resizingPlaceholder) {
                const placeholder = props.placeholders.find(p => p.id === resizingPlaceholder.id)
                if (!placeholder) return

                const relDeltaX = deltaX / pageDimensions.width
                const relDeltaY = deltaY / pageDimensions.height

                let newWidth = placeholder.relWidth
                let newHeight = placeholder.relHeight
                let newLeft = placeholder.relLeft
                let newTop = placeholder.relTop

                const edge = resizingPlaceholder.edge
                if (edge.includes('e')) {
                    newWidth = Math.max(
                        0.01,
                        Math.min(1 - placeholder.relLeft, placeholder.relWidth + relDeltaX),
                    )
                } else if (edge.includes('w')) {
                    const adjustedDelta = Math.max(
                        -placeholder.relLeft,
                        Math.min(placeholder.relWidth - 0.01, relDeltaX),
                    )
                    newLeft = placeholder.relLeft + adjustedDelta
                    newWidth = placeholder.relWidth - adjustedDelta
                }

                if (edge.includes('s')) {
                    newHeight = Math.max(
                        0.01,
                        Math.min(1 - placeholder.relTop, placeholder.relHeight + relDeltaY),
                    )
                } else if (edge.includes('n')) {
                    const adjustedDelta = Math.max(
                        -placeholder.relTop,
                        Math.min(placeholder.relHeight - 0.01, relDeltaY),
                    )
                    newTop = placeholder.relTop + adjustedDelta
                    newHeight = placeholder.relHeight - adjustedDelta
                }

                props.onPlaceholdersChange(
                    props.placeholders.map(p =>
                        p.id === resizingPlaceholder.id
                            ? {
                                  ...p,
                                  relLeft: newLeft,
                                  relTop: newTop,
                                  relWidth: newWidth,
                                  relHeight: newHeight,
                              }
                            : p,
                    ),
                )
                setDragStart({x: e.clientX, y: e.clientY})
            }
        },
        [draggedPlaceholder, resizingPlaceholder, dragStart, pageDimensions, props],
    )

    const handleMouseUp = useCallback(() => {
        setDraggedPlaceholder(null)
        setResizingPlaceholder(null)
        setDragStart(null)
    }, [])

    useEffect(() => {
        if (draggedPlaceholder || resizingPlaceholder) {
            document.addEventListener('mousemove', handleMouseMove)
            document.addEventListener('mouseup', handleMouseUp)
            return () => {
                document.removeEventListener('mousemove', handleMouseMove)
                document.removeEventListener('mouseup', handleMouseUp)
            }
        }
    }, [draggedPlaceholder, resizingPlaceholder, handleMouseMove, handleMouseUp])

    const handleDeletePlaceholder = (id: string) => {
        props.onPlaceholdersChange(props.placeholders.filter(p => p.id !== id))
        if (props.selectedPlaceholder === id) {
            props.onSelectPlaceholder(null)
        }
    }

    const renderPlaceholders = (page: number) => {
        return props.placeholders
            .filter(p => p.page === page)
            .map(placeholder => {
                const isSelected = props.selectedPlaceholder === placeholder.id
                return (
                    <Box
                        key={placeholder.id}
                        onMouseDown={e => handleMouseDown(e, placeholder.id)}
                        sx={{
                            position: 'absolute',
                            left: `${placeholder.relLeft * 100}%`,
                            top: `${placeholder.relTop * 100}%`,
                            width: `${placeholder.relWidth * 100}%`,
                            height: `${placeholder.relHeight * 100}%`,
                            border: isSelected ? '2px solid #1976d2' : '2px dashed #666',
                            backgroundColor: isSelected
                                ? 'rgba(25, 118, 210, 0.1)'
                                : 'rgba(0, 0, 0, 0.05)',
                            cursor: 'move',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            boxSizing: 'border-box',
                            '&:hover': {
                                backgroundColor: 'rgba(25, 118, 210, 0.15)',
                                borderColor: '#1976d2',
                            },
                        }}>
                        <Stack direction="row" spacing={0.5} alignItems="center">
                            <DragIndicator fontSize="small" />
                            <Typography
                                variant="caption"
                                sx={{userSelect: 'none', fontWeight: 'bold'}}>
                                {placeholder.name ||
                                    t(`gap.document.placeholder.type.${placeholder.type}`)}
                            </Typography>
                            {isSelected && (
                                <IconButton
                                    size="small"
                                    onClick={e => {
                                        e.stopPropagation()
                                        handleDeletePlaceholder(placeholder.id)
                                    }}
                                    sx={{p: 0.5}}>
                                    <Delete fontSize="small" />
                                </IconButton>
                            )}
                        </Stack>
                        {/* Resize handles */}
                        {isSelected && (
                            <>
                                <Box
                                    onMouseDown={e =>
                                        handleMouseDown(e, placeholder.id, true, 'se')
                                    }
                                    sx={{
                                        position: 'absolute',
                                        bottom: -4,
                                        right: -4,
                                        width: 8,
                                        height: 8,
                                        backgroundColor: '#1976d2',
                                        cursor: 'se-resize',
                                        borderRadius: '50%',
                                    }}
                                />
                                <Box
                                    onMouseDown={e =>
                                        handleMouseDown(e, placeholder.id, true, 'sw')
                                    }
                                    sx={{
                                        position: 'absolute',
                                        bottom: -4,
                                        left: -4,
                                        width: 8,
                                        height: 8,
                                        backgroundColor: '#1976d2',
                                        cursor: 'sw-resize',
                                        borderRadius: '50%',
                                    }}
                                />
                                <Box
                                    onMouseDown={e =>
                                        handleMouseDown(e, placeholder.id, true, 'ne')
                                    }
                                    sx={{
                                        position: 'absolute',
                                        top: -4,
                                        right: -4,
                                        width: 8,
                                        height: 8,
                                        backgroundColor: '#1976d2',
                                        cursor: 'ne-resize',
                                        borderRadius: '50%',
                                    }}
                                />
                                <Box
                                    onMouseDown={e =>
                                        handleMouseDown(e, placeholder.id, true, 'nw')
                                    }
                                    sx={{
                                        position: 'absolute',
                                        top: -4,
                                        left: -4,
                                        width: 8,
                                        height: 8,
                                        backgroundColor: '#1976d2',
                                        cursor: 'nw-resize',
                                        borderRadius: '50%',
                                    }}
                                />
                            </>
                        )}
                    </Box>
                )
            })
    }

    if (!props.pdfFile) {
        return (
            <Box sx={{p: 4, textAlign: 'center'}}>
                <Typography variant="h6" color="text.secondary">
                    {t('gap.document.template.noPdfSelected')}
                </Typography>
            </Box>
        )
    }

    if (!workerReady) {
        return (
            <Box sx={{p: 4, textAlign: 'center'}}>
                <Typography variant="body1" color="text.secondary">
                    {t('gap.document.template.loading')}
                </Typography>
            </Box>
        )
    }

    return (
        <Box ref={containerRef} sx={{overflow: 'auto', maxHeight: '70vh'}}>
            <Document file={props.pdfFile} onLoadSuccess={onDocumentLoadSuccess}>
                <Stack spacing={2}>
                    {Array.from(new Array(numPages), (_, index) => (
                        <Paper
                            key={`page_${index + 1}`}
                            elevation={3}
                            sx={{position: 'relative', display: 'inline-block'}}>
                            <Typography
                                variant="caption"
                                sx={{
                                    position: 'absolute',
                                    top: -20,
                                    left: 0,
                                    fontWeight: 'bold',
                                }}>
                                {t('gap.document.template.page', {page: index + 1})}
                            </Typography>
                            <Box
                                sx={{
                                    position: 'relative',
                                    width: pageDimensions.width,
                                    height: pageDimensions.height,
                                }}>
                                <Page
                                    pageNumber={index + 1}
                                    width={pageWidth}
                                    onLoadSuccess={onPageLoadSuccess}
                                    renderTextLayer={false}
                                    renderAnnotationLayer={false}
                                />
                                {renderPlaceholders(index + 1)}
                            </Box>
                        </Paper>
                    ))}
                </Stack>
            </Document>
        </Box>
    )
}

export default PdfPlaceholderEditor
