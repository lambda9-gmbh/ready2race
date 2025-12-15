import {pdfjs} from 'react-pdf'

// Initialize PDF.js worker once globally
// Using locally served worker file for maximum reliability
pdfjs.GlobalWorkerOptions.workerSrc = '/pdf.worker.min.mjs'