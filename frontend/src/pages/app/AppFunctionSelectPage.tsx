import { Stack, Typography, Button } from '@mui/material';
import { useUser } from '@contexts/user/UserContext';
import { useApp, AppFunction } from '@contexts/app/AppContext';
import { useNavigate } from '@tanstack/react-router';
import { useEffect, useState } from 'react';

const APP_FUNCTIONS: { fn: AppFunction; label: string }[] = [
  { fn: 'APP_QR_MANAGEMENT', label: 'QR-Code Verwaltung' },
  { fn: 'APP_COMPETITION_CHECK', label: 'Wettkampf Check' },
  { fn: 'APP_EVENT_REQUIREMENT', label: 'Event Requirements' },
];

const AppFunctionSelectPage = () => {
  const user = useUser();
  const { setAppFunction } = useApp();
  const navigate = useNavigate();
  const [available, setAvailable] = useState<AppFunction[]>([]);

  useEffect(() => {
    // Prüfe, welche App-Funktionen der User hat
    const rights: AppFunction[] = [];
    if (user.checkPrivilege({ action: 'UPDATE', resource: 'APP_QR_MANAGEMENT', scope: 'GLOBAL' })) rights.push('APP_QR_MANAGEMENT');
    if (user.checkPrivilege({ action: 'UPDATE', resource: 'APP_COMPETITION_CHECK', scope: 'GLOBAL' })) rights.push('APP_COMPETITION_CHECK');
    if (user.checkPrivilege({ action: 'UPDATE', resource: 'APP_EVENT_REQUIREMENT', scope: 'GLOBAL' })) rights.push('APP_EVENT_REQUIREMENT');
    setAvailable(rights);
    // Wenn der User nur ein Recht hat, direkt weiterleiten
    if (rights.length === 1) {
      setAppFunction(rights[0]);
      navigate({ to: '/app' });
    }
    // Wenn kein Recht, ggf. Forbidden
    if (rights.length === 0) {
      navigate({ to: '/app/forbidden' });
    }
  }, [user, setAppFunction, navigate]);

  const handleSelect = (fn: AppFunction) => {
    setAppFunction(fn);
    navigate({ to: '/app' });
  };

  if (available.length <= 1) return null;

  return (
    <Stack spacing={2} alignItems="center" justifyContent="center" p={4}>
      <Typography variant="h4" textAlign="center">
        Bitte wähle deine Funktion in der App
      </Typography>
      {APP_FUNCTIONS.filter(f => available.includes(f.fn)).map(f => (
        <Button key={f.fn} variant="contained" fullWidth onClick={() => handleSelect(f.fn)}>
          {f.label}
        </Button>
      ))}
    </Stack>
  );
};

export default AppFunctionSelectPage; 