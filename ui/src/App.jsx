import React from 'react';
import {Outlet} from 'react-router-dom';
import Navbar from './components/Navbar.jsx';
import RoutesConfig from './routes.jsx';
import SessionTimeoutModal from './components/SessionTimeoutModal.jsx';

export default function App() {
  // RoutesConfig renders the Routes, with Navbar as shared layout
  return (
    <div className="d-flex flex-column min-vh-100">
      <Navbar/>
      <main className="flex-fill py-4">
        <div className="container">
          <RoutesConfig/>
          <Outlet/>
        </div>
      </main>
      <SessionTimeoutModal/>
      <footer className="mt-auto py-3 bg-body-tertiary border-top">
        <div className="container text-center text-body-secondary small">
          MMS UI
        </div>
      </footer>
    </div>
  );
}
