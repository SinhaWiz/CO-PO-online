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

const getAdminBreadcrumb = (path: string): string => {
  if (path === '/admin' || path === '/admin/dashboard') return 'Home';
  if (path.startsWith('/admin/faculties')) return 'Manage Faculties';
  if (path.startsWith('/admin/students')) return 'Manage Students';
  if (path.startsWith('/admin/courses')) return 'Manage Courses';
  if (path.startsWith('/admin/enrollments')) return 'Manage Enrollments';
  if (path.startsWith('/admin/course-assignments')) return 'Course Assignments';
  if (path.startsWith('/admin/culmination-courses')) return 'Culmination Courses';
  if (path.startsWith('/admin/thresholds')) return 'Manage Thresholds';
  if (path.startsWith('/admin/graduating-students')) return 'Graduating Students';
  return 'Home';
};

const AdminLayout = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { logout, user } = useContext(AuthContext);

  const sections = [
    {
      title: 'MANAGEMENT',
      items: [
        { text: '👤  Manage Faculties', path: '/admin/faculties', enabled: true },
        { text: '🎓  Manage Students', path: '/admin/students', enabled: true },
        { text: '📚  Manage Courses', path: '/admin/courses', enabled: true },
        { text: '📝  Manage Enrollments', path: '/admin/enrollments', enabled: true },
        { text: '🔗  Course Assignments', path: '/admin/course-assignments', enabled: true },
      ],
    },
    {
      title: 'CONFIGURATION',
      items: [
        { text: '🎯  Culmination Courses', path: '/admin/culmination-courses', enabled: true },
        { text: '⚙️  Manage Thresholds', path: '/admin/thresholds', enabled: true },
        { text: '🎓  Graduating Students', path: '/admin/graduating-students', enabled: true },
      ],
    },
    {
      title: 'REPORTS',
      items: [
        { text: '📊  Cohort PO Report', path: '/admin', enabled: false },
        { text: '📈  View Reports', path: '/admin', enabled: false },
      ],
    },
    {
      title: 'ACCOUNT',
      items: [
        { text: '🔒  Change Password', path: '/admin', enabled: false },
        { text: '👥  Manage Admins', path: '/admin', enabled: false },
      ],
    },
  ];

  const breadcrumb = getAdminBreadcrumb(location.pathname);

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
            Admin Panel
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
                      (item.path !== '/admin' && location.pathname.startsWith(item.path)));

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
            Admin Dashboard
          </Typography>
          <Typography sx={{ fontSize: 16, color: '#94a3b8' }}>/</Typography>
          <Typography sx={{ fontSize: 15, fontWeight: 500, color: '#64748b' }}>{breadcrumb}</Typography>
          <Box sx={{ flexGrow: 1 }} />
          <Button
            onClick={() => navigate('/admin')}
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

export default AdminLayout;
