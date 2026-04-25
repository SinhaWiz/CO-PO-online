import { useContext } from 'react';
import type { ReactElement } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';

interface ProtectedRouteProps {
  children: ReactElement;
  allowedRoles?: string[];
}

const ProtectedRoute = ({ children, allowedRoles }: ProtectedRouteProps) => {
  const { isAuthenticated, user } = useContext(AuthContext);
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (allowedRoles && user && !allowedRoles.includes(user.role)) {
    // Role not authorized, redirect to their specific dashboard
    const redirectPath = user.role === 'ROLE_ADMIN' ? '/admin' : '/faculty';
    return <Navigate to={redirectPath} replace />;
  }

  return children;
};

export default ProtectedRoute;
