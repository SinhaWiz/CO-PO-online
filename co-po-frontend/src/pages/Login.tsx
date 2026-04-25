import { useState, useContext } from 'react';
import { 
  Container, Box, Typography, TextField, Button, Alert, 
  Paper, CircularProgress, MenuItem
} from '@mui/material';
import { AuthContext } from '../context/AuthContext';
import api from '../api/axios';

const Login = () => {
  const { login } = useContext(AuthContext);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState('ADMIN');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setError('Please fill in all fields');
      return;
    }

    setLoading(true);
    setError('');

    try {
      const response = await api.post('/auth/login', { email, password, role });
      const { token, name, role: userRole } = response.data;
      
      login({ email, name, role: userRole }, token);
    } catch (err: any) {
      setError(err.response?.data || 'Login failed. Invalid credentials.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Container component="main" maxWidth="sm" sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }}>
      <Box sx={{ width: '100%' }}>
        <Paper
          elevation={0}
          sx={{
            p: 4,
            width: '100%',
            borderRadius: 3,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            border: '1px solid #e5e7eb',
            boxShadow: '0 8px 24px rgba(0,0,0,0.08)',
          }}
        >
          <Box
            sx={{
              width: 84,
              height: 84,
              borderRadius: '50%',
              bgcolor: '#eff6ff',
              border: '2px solid #bfdbfe',
              color: '#1d4ed8',
              display: 'grid',
              placeItems: 'center',
              fontWeight: 800,
              mb: 2,
            }}
          >
            CP
          </Box>

          <Typography component="h1" variant="h5" sx={{ mb: 0.5, fontWeight: 700, color: '#1e293b' }}>
            CO-PO Assessment System
          </Typography>
          <Typography sx={{ mb: 2.5, color: '#64748b' }}>Sign in to continue</Typography>
          
          {error && <Alert severity="error" sx={{ width: '100%', mb: 2 }}>{error}</Alert>}
          
          <Box component="form" onSubmit={handleSubmit} sx={{ mt: 1, width: '100%' }}>
            <TextField
              margin="normal"
              select
              required
              fullWidth
              id="role"
              label="Login As"
              name="role"
              value={role}
              onChange={(e) => setRole(e.target.value)}
            >
              <MenuItem value="ADMIN">Administrator</MenuItem>
              <MenuItem value="FACULTY">Faculty Member</MenuItem>
            </TextField>

            <TextField
              margin="normal"
              required
              fullWidth
              id="email"
              label="Email Address"
              name="email"
              autoComplete="email"
              autoFocus
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
            <TextField
              margin="normal"
              required
              fullWidth
              name="password"
              label="Password"
              type="password"
              id="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            
            <Button
              type="submit"
              fullWidth
              variant="contained"
              sx={{ mt: 3, mb: 1, py: 1.1, fontWeight: 700 }}
              disabled={loading}
            >
              {loading ? <CircularProgress size={24} /> : 'Login'}
            </Button>
          </Box>
        </Paper>
      </Box>
    </Container>
  );
};

export default Login;
