import {
  Box,
  Button,
  Divider,
  List,
  ListItem,
  ListItemButton,
  ListItemText,
  Paper,
  Typography,
} from '@mui/material';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useContext } from 'react';
import { AuthContext } from '../../context/AuthContext';

const sidebarWidth = 280;

const getFacultyBreadcrumb = (path: string): string => {
  if (path === '/faculty' || path === '/faculty/dashboard') return 'Home';
  if (path.startsWith('/faculty/assessments')) return 'Course Questions';
  if (path.startsWith('/faculty/marks')) return 'Student Marks';
  if (path.startsWith('/faculty/change-password')) return 'Change Password';
  return 'Home';
};

const FacultyLayout = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { logout, user } = useContext(AuthContext);

  const sections = [
    {
      title: 'NAVIGATION',
      items: [
        { text: '◄ Back to Courses', path: '/faculty', enabled: true },
        { text: '📊  Course Summary', path: '/faculty', enabled: false },
        { text: '📝  Course Questions', path: '/faculty/assessments', enabled: true },
        { text: '✏️  Student Marks', path: '/faculty/marks', enabled: true },
        { text: '📋  View Results', path: '/faculty', enabled: false },
        { text: '⚙️  Course Thresholds', path: '/faculty', enabled: false },
      ],
    },
    {
      title: 'REPORTS',
      items: [
        { text: '📊  CO Report', path: '/faculty', enabled: false },
        { text: '📈  PO Report', path: '/faculty', enabled: false },
        { text: '📑  Summary Report', path: '/faculty', enabled: false },
        { text: '📄  Course Report', path: '/faculty', enabled: false },
        { text: '📋  Detailed Marks Report', path: '/faculty', enabled: false },
      ],
    },
    {
      title: 'IMPORT/EXPORT',
      items: [
        { text: '📤  Export Marks', path: '/faculty', enabled: false },
        { text: '📥  Import Marks', path: '/faculty', enabled: false },
      ],
    },
    {
      title: 'ACCOUNT',
      items: [
        { text: '🔒  Change Password', path: '/faculty/change-password', enabled: true },
      ],
    },
  ];

  const breadcrumb = getFacultyBreadcrumb(location.pathname);

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: '#f5f7fa' }}>
      <Box
        sx={{
          width: sidebarWidth,
          borderRight: '1px solid #e5e7eb',
          bgcolor: '#fff',
          display: 'flex',
          flexDirection: 'column',
          px: 1.5,
          py: 0,
        }}
      >
        <Box sx={{ py: 2.5, display: 'flex', justifyContent: 'center' }}>
          <Box
            sx={{
              width: 80,
              height: 80,
              borderRadius: '50%',
              bgcolor: '#eff6ff',
              border: '2px solid #bfdbfe',
              display: 'grid',
              placeItems: 'center',
              color: '#1d4ed8',
              fontWeight: 800,
              fontSize: 14,
            }}
          >
            CO-PO
          </Box>
        </Box>

        <Box sx={{ px: 2, pb: 2 }}>
          <Typography sx={{ color: '#64748b', fontSize: 11, fontWeight: 700 }}>
            CO-PO ASSESSMENT
          </Typography>
          <Typography sx={{ color: '#1e293b', fontSize: 20, fontWeight: 700 }}>
            Faculty Panel
          </Typography>
        </Box>

        <Box sx={{ overflowY: 'auto', pb: 1 }}>
          {sections.map((section) => (
            <Box key={section.title} sx={{ mb: 1 }}>
              <Typography
                sx={{
                  px: 2,
                  py: 1,
                  color: '#64748b',
                  fontSize: 11,
                  fontWeight: 700,
                }}
              >
                {section.title}
              </Typography>
              <List dense disablePadding sx={{ px: 1 }}>
                {section.items.map((item) => {
                  const selected =
                    item.enabled &&
                    (location.pathname === item.path ||
                      (item.path !== '/faculty' && location.pathname.startsWith(item.path)));

                  return (
                    <ListItem key={item.text} disablePadding sx={{ mb: 0.5 }}>
                      <ListItemButton
                        onClick={() => item.enabled && navigate(item.path)}
                        disabled={!item.enabled}
                        selected={selected}
                        sx={{
                          borderRadius: 2,
                          py: 1,
                          '&.Mui-selected': {
                            backgroundColor: '#dbeafe',
                            color: '#1e40af',
                            borderLeft: '4px solid #3b82f6',
                            pl: '12px',
                            fontWeight: 700,
                          },
                        }}
                      >
                        <ListItemText
                          primary={
                            <Typography
                              sx={{
                                fontSize: 13,
                                fontWeight: selected ? 700 : 500,
                                color: selected ? '#1e40af' : '#475569',
                              }}
                            >
                              {item.text}
                            </Typography>
                          }
                        />
                      </ListItemButton>
                    </ListItem>
                  );
                })}
              </List>
            </Box>
          ))}
        </Box>
      </Box>

      <Box sx={{ flexGrow: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
        <Paper
          square
          sx={{
            borderRadius: 0,
            borderLeft: 0,
            borderRight: 0,
            borderTop: 0,
            px: 3,
            py: 1.5,
            display: 'flex',
            alignItems: 'center',
            gap: 1.5,
          }}
        >
          <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b' }}>
            Faculty Dashboard
          </Typography>
          <Typography sx={{ fontSize: 16, color: '#94a3b8' }}>/</Typography>
          <Typography sx={{ fontSize: 15, fontWeight: 500, color: '#64748b' }}>{breadcrumb}</Typography>
          <Box sx={{ flexGrow: 1 }} />
          <Button
            onClick={() => navigate('/faculty')}
            variant="contained"
            color="secondary"
            sx={{ color: '#064e3b', background: 'linear-gradient(to bottom, #6ee7b7 0%, #10b981 100%)' }}
          >
            Home
          </Button>
          <Button
            onClick={logout}
            variant="contained"
            color="error"
            sx={{ background: 'linear-gradient(to bottom, #ef4444 0%, #dc2626 100%)' }}
          >
            Logout
          </Button>
        </Paper>

        <Box component="main" sx={{ flexGrow: 1, overflow: 'auto', p: 3 }}>
          <Outlet />
        </Box>

        <Divider />
        <Box
          sx={{
            px: 2,
            py: 0.75,
            bgcolor: '#1e293b',
            color: '#e2e8f0',
            fontSize: 13,
          }}
        >
          Ready{user?.name ? ` • Signed in as ${user.name}` : ''}
        </Box>
      </Box>
    </Box>
  );
};

export default FacultyLayout;
