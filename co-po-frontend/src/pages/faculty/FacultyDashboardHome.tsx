import { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { getMyAssignments, type MyAssignment } from '../../api/faculty';

const FacultyDashboardHome = () => {
  const navigate = useNavigate();
  const [assignments, setAssignments] = useState<MyAssignment[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    getMyAssignments()
      .then((res) => setAssignments(res.data))
      .catch((error) => console.error('Failed to load assignments', error))
      .finally(() => setLoading(false));
  }, []);

  return (
    <Box>
      <Paper sx={{ p: { xs: 3, md: 4 }, mb: 3 }}>
        <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b' }}>CO-PO Assessment System</Typography>
        <Typography sx={{ fontSize: 16, color: '#64748b' }}>Faculty Dashboard</Typography>
      </Paper>

      <Paper sx={{ p: 2.5 }}>
        <Typography sx={{ fontSize: 20, fontWeight: 700, color: '#1e293b', mb: 1.5 }}>Your Course Assignments</Typography>

        {loading && <Typography sx={{ color: '#64748b' }}>Loading...</Typography>}

        {!loading && assignments.length === 0 && (
          <Typography sx={{ color: '#64748b' }}>
            You have no course assignments yet - an admin needs to assign you a course first.
          </Typography>
        )}

        {!loading && assignments.length > 0 && (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Course</TableCell>
                  <TableCell>Programme</TableCell>
                  <TableCell>Academic Year</TableCell>
                  <TableCell>Department</TableCell>
                  <TableCell>Quick Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {assignments.map((a) => (
                  <TableRow key={`${a.courseCode}||${a.programme}||${a.academicYear}||${a.department}`}>
                    <TableCell sx={{ fontWeight: 600 }}>{a.courseCode} - {a.courseName}</TableCell>
                    <TableCell>{a.programme}</TableCell>
                    <TableCell>{a.academicYear}</TableCell>
                    <TableCell>{a.department}</TableCell>
                    <TableCell>
                      <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
                        <Button size="small" variant="outlined" onClick={() => navigate('/faculty/assessments')}>
                          Questions
                        </Button>
                        <Button size="small" variant="outlined" onClick={() => navigate('/faculty/marks')}>
                          Marks
                        </Button>
                        <Button size="small" variant="outlined" onClick={() => navigate('/faculty/results')}>
                          Results
                        </Button>
                      </Box>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>
    </Box>
  );
};

export default FacultyDashboardHome;
