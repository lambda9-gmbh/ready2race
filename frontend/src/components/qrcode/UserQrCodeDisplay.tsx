import React from 'react';
import QRCode from 'react-qr-code';
import { Box, Paper, Typography } from '@mui/material';
import { useThemeConfig } from '../../contexts/theme/ThemeContext';
import Config from '../../Config.ts';

interface UserQrCodeDisplayProps {
  qrCodeData: string;
  label?: string;
  displayId?: string;
}

/**
 * Anteil der QR-Breite, den das Logo-Fenster einnimmt. Der Code wird mit
 * Fehlerkorrektur „H" erzeugt (bis ~30 % der Module rekonstruierbar); ein Fenster
 * von 35 % Kantenlänge deckt nur gut 12 % der Fläche ab und lässt damit reichlich
 * Reserve für Druckfehler, Knicke im Band und schräge Kamerawinkel.
 */
const LOGO_WINDOW_RATIO = 0.35;

export const UserQrCodeDisplay: React.FC<UserQrCodeDisplayProps> = ({ qrCodeData, label, displayId }) => {
  const { themeConfig } = useThemeConfig();
  const customLogo = themeConfig?.customLogo;
  const logoUrl =
    customLogo?.enabled && customLogo.filename
      ? `${Config.logosUrl}/${customLogo.filename}`
      : null;

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
        <Box sx={{ position: 'relative', display: 'inline-block', lineHeight: 0 }}>
          <QRCode
            value={qrCodeData}
            size={256}
            level="H"
          />
          {logoUrl && (
            // Freigeschnittene Mitte: Der weiße Rahmen trennt das Logo sichtbar von
            // den Modulen, damit Scanner die verdeckte Fläche sauber als Schaden
            // erkennen statt sie als Muster fehlzudeuten.
            <Box
              sx={{
                position: 'absolute',
                top: '50%',
                left: '50%',
                transform: 'translate(-50%, -50%)',
                width: `${LOGO_WINDOW_RATIO * 100}%`,
                backgroundColor: 'white',
                padding: '4px',
                borderRadius: '8px',
                lineHeight: 0,
              }}
            >
              <Box
                component="img"
                src={logoUrl}
                alt=""
                sx={{
                  width: '100%',
                  height: 'auto',
                  display: 'block',
                  borderRadius: '5px',
                }}
              />
            </Box>
          )}
        </Box>
      </Paper>
      {displayId && (
        <Typography variant="caption" display="block" sx={{ mt: 1 }}>
          ID: {displayId}
        </Typography>
      )}
    </Box>
  );
};
