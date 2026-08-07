import { useEffect, useMemo, useState } from 'react';
import { Box, Paper, Typography } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import {
  getCourseAssignments,
  getCourses,
  getEnrollments,
  getFaculties,
  getStudents,
} from '../../api/admin';

interface StatCard {
  label: string;
  value: number | null;
  path: string;
}

const AdminDashboardHome = () => {
  const navigate = useNavigate();
  const [now, setNow] = useState(new Date());
  const [stats, setStats] = useState<Record<string, number>>({});

  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    Promise.allSettled([
      getStudents(), getFaculties(), getCourses(), getEnrollments(), getCourseAssignments(),
    ]).then(([students, faculties, courses, enrollments, assignments]) => {
      setStats({
        students: students.status === 'fulfilled' ? students.value.data.length : 0,
        faculties: faculties.status === 'fulfilled' ? faculties.value.data.length : 0,
        courses: courses.status === 'fulfilled' ? courses.value.data.length : 0,
        enrollments: enrollments.status === 'fulfilled' ? enrollments.value.data.length : 0,
        assignments: assignments.status === 'fulfilled' ? assignments.value.data.length : 0,
      });
    });
  }, []);

  const timeText = useMemo(
    () => now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }),
    [now],
  );
  const dateText = useMemo(() => now.toLocaleDateString([], { month: 'long', day: 'numeric', year: 'numeric' }), [now]);
  const dayText = useMemo(() => now.toLocaleDateString([], { weekday: 'long' }), [now]);

  const cards: StatCard[] = [
    { label: 'Students', value: stats.students ?? null, path: '/admin/students' },
    { label: 'Faculty', value: stats.faculties ?? null, path: '/admin/faculties' },
    { label: 'Courses', value: stats.courses ?? null, path: '/admin/courses' },
    { label: 'Enrollments', value: stats.enrollments ?? null, path: '/admin/enrollments' },
    { label: 'Course Assignments', value: stats.assignments ?? null, path: '/admin/course-assignments' },
  ];

  return (
    <Box>
      <Paper sx={{ p: { xs: 3, md: 4 }, mb: 3, display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: 2 }}>
        <Box>
          <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b' }}>CO-PO Assessment System</Typography>
          <Typography sx={{ fontSize: 16, color: '#64748b' }}>Administrator Dashboard</Typography>
        </Box>
        <Box sx={{ textAlign: 'right' }}>
          <Typography sx={{ fontSize: 28, fontWeight: 700, color: '#1e293b', fontVariantNumeric: 'tabular-nums' }}>{timeText}</Typography>
          <Typography sx={{ fontSize: 14, color: '#64748b' }}>{dayText}, {dateText}</Typography>
        </Box>
      </Paper>

      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', sm: 'repeat(5, 1fr)' }, gap: 2 }}>
        {cards.map((card) => (
          <Paper
            key={card.label}
            sx={{ p: 2.5, cursor: 'pointer', transition: 'box-shadow 0.15s', '&:hover': { boxShadow: 4 } }}
            onClick={() => navigate(card.path)}
          >
            <Typography sx={{ fontSize: 13, color: '#64748b', mb: 0.5 }}>{card.label}</Typography>
            <Typography sx={{ fontSize: 32, fontWeight: 700, color: '#1d4ed8' }}>
              {card.value ?? '-'}
            </Typography>
          </Paper>
        ))}
      </Box>

      <Typography sx={{ fontSize: 14, color: '#94a3b8', mt: 3 }}>
        Click a card to jump to that section, or use the sidebar navigation.
      </Typography>
    </Box>
  );
};

export default AdminDashboardHome;
