import React from 'react';
import { useAuth } from '../hooks/useAuth.js';

export default function SessionTimeoutModal() {
  const { idleWarning, idleCountdown, extendSession, confirmSignout } = useAuth();
  if (!idleWarning) return null;

  // Simple Bootstrap-like modal without relying on JS API
  return (
    <>
      <div className="modal fade show" style={{ display: 'block' }} tabIndex="-1" role="dialog" aria-modal="true">
        <div className="modal-dialog modal-dialog-centered" role="document">
          <div className="modal-content">
            <div className="modal-header">
              <h5 className="modal-title">Session Timeout</h5>
            </div>
            <div className="modal-body">
              <p>Your session is about to expire due to inactivity.</p>
              <p className="mb-0">You will be signed out in <strong>{idleCountdown}</strong> seconds.</p>
            </div>
            <div className="modal-footer">
              <button type="button" className="btn btn-secondary" onClick={confirmSignout}>Sign Out Now</button>
              <button type="button" className="btn btn-primary" onClick={extendSession}>Stay Signed In</button>
            </div>
          </div>
        </div>
      </div>
      <div className="modal-backdrop fade show"></div>
    </>
  );
}

