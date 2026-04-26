import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import {
  createAdminAccount,
  deleteAdminAccount,
  demoteAdminFromSuper,
  getAdminProfile,
  getAdmins,
  promoteAdminToSuper,
  type AdminListItem,
} from '../../api/admin';

type SensitiveActionType = 'delete' | 'promote' | 'demote';

type SensitiveActionState = {
  open: boolean;
  action: SensitiveActionType;
  admin: AdminListItem | null;
  password: string;
};

const emailRegex = /^[A-Za-z0-9+_.-]+@(.+)$/;

const ManageAdmins = () => {
  const [admins, setAdmins] = useState<AdminListItem[]>([]);
  const [currentAdminEmail, setCurrentAdminEmail] = useState('');
  const [currentIsSuperAdmin, setCurrentIsSuperAdmin] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const [loading, setLoading] = useState(false);
  const [actionState, setActionState] = useState<SensitiveActionState>({
    open: false,
    action: 'delete',
    admin: null,
    password: '',
  });

  const superAdminCount = useMemo(() => admins.filter((a) => a.isSuperAdmin).length, [admins]);
  const regularAdminCount = admins.length - superAdminCount;

  const loadData = async () => {
    setLoading(true);
    try {
      const [profileRes, adminsRes] = await Promise.all([getAdminProfile(), getAdmins()]);
      setCurrentAdminEmail(profileRes.data.email);
      setCurrentIsSuperAdmin(profileRes.data.isSuperAdmin);
      setAdmins(adminsRes.data.admins);
      setMessage(null);
    } catch (error: any) {
      const text = error?.response?.data?.message || error?.response?.data || 'Failed to load administrators.';
      setMessage({ type: 'error', text });
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleAddAdmin = async () => {
    setMessage(null);

    if (!email || !password || !confirmPassword) {
      setMessage({ type: 'error', text: 'All fields are required!' });
      return;
    }

    if (!emailRegex.test(email.trim())) {
      setMessage({ type: 'error', text: 'Invalid email format!' });
      return;
    }

    if (password.length < 6) {
      setMessage({ type: 'error', text: 'Password must be at least 6 characters long!' });
      return;
    }

    if (password !== confirmPassword) {
      setMessage({ type: 'error', text: 'Passwords do not match!' });
      return;
    }

    try {
      await createAdminAccount(email.trim(), password, confirmPassword);
      setMessage({ type: 'success', text: 'Administrator added successfully!' });
      setEmail('');
      setPassword('');
      setConfirmPassword('');
      await loadData();
    } catch (error: any) {
      const text = error?.response?.data?.message || error?.response?.data || 'Failed to add administrator.';
      setMessage({ type: 'error', text });
    }
  };

  const openConfirm = (action: SensitiveActionType, admin: AdminListItem) => {
    setActionState({ open: true, action, admin, password: '' });
  };

  const closeConfirm = () => {
    setActionState((s) => ({ ...s, open: false, password: '', admin: null }));
  };

  const getActionTitle = () => {
    if (actionState.action === 'delete') return 'Confirm Deletion';
    if (actionState.action === 'promote') return 'Confirm Promotion';
    return 'Confirm Demotion';
  };

  const getActionDescription = () => {
    if (!actionState.admin) return '';
    if (actionState.action === 'delete') {
      return `Delete administrator: ${actionState.admin.email}`;
    }
    if (actionState.action === 'promote') {
      return `Promote to Super Admin: ${actionState.admin.email}`;
    }
    return `Demote Super Admin: ${actionState.admin.email}`;
  };

  const executeAction = async () => {
    if (!actionState.admin) return;
    if (!actionState.password) {
      setMessage({ type: 'error', text: 'Password is required for confirmation.' });
      return;
    }

    try {
      if (actionState.action === 'delete') {
        await deleteAdminAccount(actionState.admin.id, actionState.password);
        setMessage({ type: 'success', text: 'Administrator deleted successfully!' });
      } else if (actionState.action === 'promote') {
        await promoteAdminToSuper(actionState.admin.id, actionState.password);
        setMessage({ type: 'success', text: 'Administrator promoted to Super Admin successfully!' });
      } else {
        await demoteAdminFromSuper(actionState.admin.id, actionState.password);
        setMessage({ type: 'success', text: 'Super Admin demoted successfully!' });
      }

      closeConfirm();
      await loadData();
    } catch (error: any) {
      const text = error?.response?.data?.message || error?.response?.data || 'Operation failed.';
      setMessage({ type: 'error', text });
    }
  };

  const sortedAdmins = useMemo(() => [...admins].sort((a, b) => a.id - b.id), [admins]);

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Manage Admins</Typography>

      {message && (
        <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      {!currentIsSuperAdmin && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          You are a regular admin. Only super admins can add, promote, demote, or delete admins.
        </Alert>
      )}

      <Paper sx={{ p: 2, mb: 2 }}>
        <Typography sx={{ fontWeight: 700, mb: 1.5 }}>Add Administrator</Typography>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' }, gap: 1.5 }}>
          <TextField
            label="Email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            size="small"
            disabled={!currentIsSuperAdmin || loading}
          />
          <TextField
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            size="small"
            disabled={!currentIsSuperAdmin || loading}
          />
          <TextField
            label="Confirm Password"
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            size="small"
            disabled={!currentIsSuperAdmin || loading}
          />
        </Box>
        <Box sx={{ mt: 1.5, display: 'flex', gap: 1.25 }}>
          <Button variant="contained" onClick={handleAddAdmin} disabled={!currentIsSuperAdmin || loading}>
            Add Admin
          </Button>
          <Button variant="outlined" onClick={loadData} disabled={loading}>
            Refresh
          </Button>
        </Box>
      </Paper>

      <Paper sx={{ p: 2, mb: 2 }}>
        <Typography sx={{ color: '#64748b' }}>
          Total: {sortedAdmins.length} administrator(s) • {superAdminCount} super admin(s) • {regularAdminCount} regular admin(s)
        </Typography>
      </Paper>

      <TableContainer component={Paper}>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>ID</TableCell>
              <TableCell>Email</TableCell>
              <TableCell>Role</TableCell>
              <TableCell>Created By</TableCell>
              <TableCell>Created At</TableCell>
              <TableCell>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {sortedAdmins.map((admin) => {
              const isSelf = admin.email === currentAdminEmail;
              const isLastSuper = admin.isSuperAdmin && superAdminCount <= 1;

              return (
                <TableRow key={admin.id}>
                  <TableCell>{admin.id}</TableCell>
                  <TableCell>{admin.email}</TableCell>
                  <TableCell sx={{ fontWeight: admin.isSuperAdmin ? 700 : 500, color: admin.isSuperAdmin ? '#7c3aed' : '#475569' }}>
                    {admin.isSuperAdmin ? 'Super Admin' : 'Regular Admin'}
                  </TableCell>
                  <TableCell>{admin.createdBy || '(Original)'}</TableCell>
                  <TableCell>{admin.createdAt ? new Date(admin.createdAt).toLocaleString() : '—'}</TableCell>
                  <TableCell>
                    {isSelf ? (
                      <Typography sx={{ color: '#94a3b8', fontStyle: 'italic' }}>(You)</Typography>
                    ) : (
                      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                        {admin.isSuperAdmin ? (
                          <Button
                            variant="outlined"
                            color="warning"
                            size="small"
                            onClick={() => openConfirm('demote', admin)}
                            disabled={!currentIsSuperAdmin || isLastSuper}
                          >
                            Demote
                          </Button>
                        ) : (
                          <Button
                            variant="outlined"
                            color="secondary"
                            size="small"
                            onClick={() => openConfirm('promote', admin)}
                            disabled={!currentIsSuperAdmin}
                          >
                            Promote
                          </Button>
                        )}

                        <Button
                          variant="contained"
                          color="error"
                          size="small"
                          onClick={() => openConfirm('delete', admin)}
                          disabled={!currentIsSuperAdmin || isLastSuper}
                        >
                          Delete
                        </Button>
                      </Box>
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>

      <Dialog open={actionState.open} onClose={closeConfirm} maxWidth="xs" fullWidth>
        <DialogTitle>{getActionTitle()}</DialogTitle>
        <DialogContent>
          <Typography sx={{ mb: 1.5 }}>{getActionDescription()}</Typography>
          <Typography sx={{ mb: 1.5, color: '#64748b', fontSize: 14 }}>Enter your password to confirm:</Typography>
          <TextField
            label="Your Password"
            type="password"
            value={actionState.password}
            onChange={(e) => setActionState((s) => ({ ...s, password: e.target.value }))}
            fullWidth
            size="small"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={closeConfirm}>Cancel</Button>
          <Button
            onClick={executeAction}
            variant="contained"
            disabled={!actionState.password.trim()}
            color={actionState.action === 'delete' ? 'error' : 'primary'}
          >
            Confirm
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ManageAdmins;
