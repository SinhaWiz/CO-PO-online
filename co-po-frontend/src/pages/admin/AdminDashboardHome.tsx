import { useEffect, useMemo, useState } from 'react';
import { Box, Paper, Typography } from '@mui/material';

const AdminDashboardHome = () => {
  const [now, setNow] = useState(new Date());

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const timeText = useMemo(
    () =>
      now.toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false,
      }),
    [now],
  );

  const dateText = useMemo(
    () => now.toLocaleDateString([], { month: 'long', day: 'numeric', year: 'numeric' }),
    [now],
  );

  const dayText = useMemo(() => now.toLocaleDateString([], { weekday: 'long' }), [now]);

  return (
    <Box
      sx={{
        minHeight: 'calc(100vh - 180px)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <Paper
        sx={{
          width: '100%',
          maxWidth: 950,
          p: { xs: 3, md: 6 },
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 5,
        }}
      >
        <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2.5 }}>
          <Box
            sx={{
              width: 180,
              height: 180,
              borderRadius: '50%',
              border: '3px solid #bfdbfe',
              bgcolor: '#eff6ff',
              color: '#1d4ed8',
              display: 'grid',
              placeItems: 'center',
              fontSize: 34,
              fontWeight: 800,
            }}
          >
            CP
          </Box>

          <Typography sx={{ fontSize: 34, fontWeight: 700, color: '#1e293b', textAlign: 'center' }}>
            CO-PO Assessment System
          </Typography>
          <Typography sx={{ fontSize: 20, fontWeight: 500, color: '#64748b', textAlign: 'center' }}>
            Administrator Dashboard
          </Typography>
        </Box>

        <Paper
          variant="outlined"
          sx={{
            width: '100%',
            maxWidth: 380,
            p: 3,
            textAlign: 'center',
            borderRadius: 3,
            borderColor: '#dbeafe',
            background: 'linear-gradient(180deg, #ffffff 0%, #f8fafc 100%)',
          }}
        >
          <Typography sx={{ fontSize: 48, fontWeight: 700, letterSpacing: 1, color: '#1e293b' }}>{timeText}</Typography>
          <Box sx={{ width: '100%', borderTop: '1px solid #e2e8f0', my: 1.5 }} />
          <Typography sx={{ fontSize: 18, color: '#334155' }}>{dateText}</Typography>
          <Typography sx={{ fontSize: 16, color: '#64748b', mt: 0.75 }}>{dayText}</Typography>
        </Paper>

        <Typography sx={{ fontSize: 16, color: '#64748b', textAlign: 'center' }}>
          Click any menu item from the sidebar to get started
        </Typography>
      </Paper>
    </Box>
  );
};

export default AdminDashboardHome;