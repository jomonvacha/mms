import React from 'react';
import {Link} from 'react-router-dom';
import {useAuth} from '../hooks/useAuth.js';

export default function Home() {
  const {user, loading} = useAuth();

  return (
    <div className="py-4">
      <div className="p-5 mb-4 bg-body-tertiary rounded-3 border">
        <div className="container-fluid py-5">
          <h1 className="display-6 fw-semibold">Welcome to MMS</h1>
          <p className="col-md-8 fs-5 text-body-secondary">
            Manage members and access protected resources. Sign in to continue,
            or explore the members area if you already have access.
          </p>
          {!loading && (
            user ? (
              <Link className="btn btn-primary btn-lg" to="/members">
                Go to Members
              </Link>
            ) : (
              <div className="d-flex gap-2">
                <Link className="btn btn-primary btn-lg" to="/signin">
                  Sign In
                </Link>
                <Link className="btn btn-outline-secondary btn-lg" to="/signup">
                  Sign Up
                </Link>
              </div>
            )
          )}
        </div>
      </div>

      <div className="row g-4">
        <div className="col-md-4">
          <div className="card h-100">
            <div className="card-body">
              <h5 className="card-title">Secure Authentication</h5>
              <p className="card-text text-body-secondary">
                Sign in with email and password or continue with Google or Apple.
              </p>
            </div>
          </div>
        </div>
        <div className="col-md-4">
          <div className="card h-100">
            <div className="card-body">
              <h5 className="card-title">Members Area</h5>
              <p className="card-text text-body-secondary">
                Access member data and resources after authentication.
              </p>
            </div>
          </div>
        </div>
        <div className="col-md-4">
          <div className="card h-100">
            <div className="card-body">
              <h5 className="card-title">Responsive UI</h5>
              <p className="card-text text-body-secondary">
                Built with Bootstrap 5 and React for an enterprise look and feel.
              </p>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
