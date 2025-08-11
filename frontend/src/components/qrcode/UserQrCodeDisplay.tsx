import React from 'react';
import QRCode from 'react-qr-code';
import { Box, Paper, Typography } from '@mui/material';

interface UserQrCodeDisplayProps {
  qrCodeData: string;
  label?: string;
  displayId?: string;
}

export const UserQrCodeDisplay: React.FC<UserQrCodeDisplayProps> = ({ qrCodeData, label, displayId }) => {

  return (
    <Box sx={{ textAlign: 'center' }}>
      {label && (
        <Typography variant="h6" gutterBottom>
          {label}
        </Typography>
      )}
      <Paper 
        elevation={3} 
        sx={{ 
          p: 2, 
          display: 'inline-block',
          backgroundColor: 'white'
        }}
      >
        <QRCode
          value={qrCodeData}
          size={256}
          level="H"
        />
      </Paper>
      {displayId && (
        <Typography variant="caption" display="block" sx={{ mt: 1 }}>
          ID: {displayId}
        </Typography>
      )}
    </Box>
  );
};