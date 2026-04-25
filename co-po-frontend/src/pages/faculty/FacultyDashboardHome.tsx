import { Box, Paper, Typography } from '@mui/material';

const FacultyDashboardHome = () => {
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
          textAlign: 'center',
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

          <Typography sx={{ fontSize: 34, fontWeight: 700, color: '#1e293b' }}>
            CO-PO Assessment System
          </Typography>
          <Typography sx={{ fontSize: 20, fontWeight: 500, color: '#64748b' }}>
            Faculty Dashboard
          </Typography>
        </Box>

        <Paper
          variant="outlined"
          sx={{
            width: '100%',
            maxWidth: 650,
            p: 3,
            borderRadius: 3,
            borderColor: '#dbeafe',
            background: 'linear-gradient(180deg, #ffffff 0%, #f8fafc 100%)',
          }}
        >
          <Typography sx={{ fontSize: 22, fontWeight: 700, color: '#1e293b', mb: 1 }}>
            Assigned Courses
          </Typography>
          <Typography sx={{ fontSize: 15, color: '#64748b' }}>
            Select a course from your assigned course list, then use the sidebar to manage questions, marks, and reports.
          </Typography>
        </Paper>

        <Typography sx={{ fontSize: 16, color: '#64748b' }}>
          Use the sidebar navigation to continue
        </Typography>
      </Paper>
    </Box>
  );
};

export default FacultyDashboardHome;