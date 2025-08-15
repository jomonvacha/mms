import React from 'react';
import {Navigate, useLocation} from 'react-router-dom';
import {useAuth} from '../hooks/useAuth.js';

export default function ProtectedRoute({children}) {
  const {user, loading} = useAuth();
  const location = useLocation();

  if (loading) {
    return <div className="text-center text-body-secondary py-5">Checking access…</div>;
  }

  if (!user) {
    return <Navigate to="/signin" replace state={{from: location}}/>;
  }

  return children;
}
