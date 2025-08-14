import React from 'react';
import { Routes, Route } from 'react-router-dom';
import Home from './pages/Home.jsx';
import SignIn from './pages/SignIn.jsx';
import SignUp from './pages/SignUp.jsx';
import Members from './pages/Members.jsx';
import SignOut from './pages/SignOut.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';

export default function RoutesConfig() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/signin" element={<SignIn />} />
      <Route path="/signup" element={<SignUp />} />
      <Route
        path="/members"
        element={
          <ProtectedRoute>
            <Members />
          </ProtectedRoute>
        }
      />
      <Route path="/signout" element={<SignOut />} />
      <Route path="*" element={<Home />} />
    </Routes>
  );
}

