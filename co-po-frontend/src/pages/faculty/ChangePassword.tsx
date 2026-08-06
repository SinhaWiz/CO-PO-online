import { useState } from 'react';
import { Alert, Box, Button, Paper, TextField, Typography } from '@mui/material';
import { changeFacultyPassword } from '../../api/faculty';

const ChangePassword = () => {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);

  const handleSubmit = async () => {
    setMessage(null);

    if (!currentPassword || !newPassword || !confirmPassword) {
      setMessage({ type: 'error', text: 'All fields are required!' });
      return;
    }

    if (newPassword.length < 6) {
      setMessage({ type: 'error', text: 'New password must be at least 6 characters long!' });
      return;
    }

    if (newPassword !== confirmPassword) {
      setMessage({ type: 'error', text: 'New passwords do not match!' });
      return;
    }

    if (newPassword === currentPassword) {
      setMessage({ type: 'error', text: 'New password must be different from current password!' });
      return;
    }

    setSubmitting(true);
    try {
      const res = await changeFacultyPassword(currentPassword, newPassword, confirmPassword);
      setMessage({ type: 'success', text: res.data.message || 'Password changed successfully!' });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (error: any) {
      const text = error?.response?.data?.message || error?.response?.data || 'Failed to change password.';
      setMessage({ type: 'error', text });
    } finally {
      setSubmitting(false);
    }
  };

  const handleClear = () => {
    setCurrentPassword('');
    setNewPassword('');
    setConfirmPassword('');
    setMessage(null);
  };

  return (
    <Box>
      <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', mb: 2 }}>Change Password</Typography>

      {message && (
        <Alert severity={message.type} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      <Paper sx={{ p: 2.5, maxWidth: 620 }}>
        <Box sx={{ display: 'grid', gap: 1.5 }}>
          <TextField
            label="Current Password"
            type="password"
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
            size="small"
            fullWidth
          />
          <TextField
            label="New Password"
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            size="small"
            fullWidth
          />
          <TextField
            label="Confirm New Password"
            type="password"
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            size="small"
            fullWidth
          />
        </Box>

        <Box sx={{ mt: 2, display: 'flex', gap: 1.25 }}>
          <Button variant="contained" onClick={handleSubmit} disabled={submitting}>
            {submitting ? 'Changing...' : 'Change Password'}
          </Button>
          <Button variant="outlined" onClick={handleClear} disabled={submitting}>
            Clear
          </Button>
        </Box>
      </Paper>
    </Box>
  );
};

export default ChangePassword;
