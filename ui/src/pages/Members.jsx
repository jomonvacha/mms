import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { listMembers, getMemberByUserId } from '../api/client.js';
import { useAuth } from '../hooks/useAuth.js';

export default function Members() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [members, setMembers] = useState([]);
  const [myMember, setMyMember] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        // Determine if user is privileged to list all members
        const roles = user?.roles || [];
        const isPrivileged = roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MODERATOR') || roles.includes('ROLE_MANAGER');
        if (isPrivileged) {
          const data = await listMembers();
          if (!cancelled) setMembers(data || []);
        } else if (user?.id) {
          const mine = await getMemberByUserId(user.id);
          if (!cancelled) setMyMember(mine || null);
        } else {
          // No user loaded
          if (!cancelled) setError('User not loaded');
        }
      } catch (err) {
        if (err?.status === 401) {
          navigate('/signin', { replace: true, state: { from: { pathname: '/members' } } });
          return;
        }
        if (err?.status === 403 && user?.id) {
          try {
            const mine = await getMemberByUserId(user.id);
            if (!cancelled) setMyMember(mine || null);
          } catch (e2) {
            if (!cancelled) setError(e2?.message || 'Access denied');
          }
        } else if (err?.status === 404) {
          if (!cancelled) setMyMember(null);
        } else if (!cancelled) {
          setError(err?.message || 'Failed to load members');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }
    load();
    return () => { cancelled = true; };
  }, [navigate]);

  return (
    <div>
      <div className="d-flex align-items-center justify-content-between mb-3">
        <h1 className="h4 mb-0">Members</h1>
        {user && <span className="text-muted small">Signed in as {user.name || user.email}</span>}
      </div>

      {loading && (
        <div className="text-center py-5 text-muted">Loading members…</div>
      )}
      {error && (
        <div className="alert alert-danger" role="alert">{error}</div>
      )}

      {!loading && !error && members && members.length > 0 && (
        <div className="table-responsive">
          <table className="table table-striped align-middle">
            <thead>
              <tr>
                <th scope="col">#</th>
                <th scope="col">Name</th>
                <th scope="col">Email</th>
              </tr>
            </thead>
            <tbody>
              {members.map((m, idx) => (
                <tr key={m.id || idx}>
                  <th scope="row">{idx + 1}</th>
                  <td>{m.name || m.fullName || m.username || '—'}</td>
                  <td>{m.email || '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {!loading && !error && (!members || members.length === 0) && myMember && (
        <div className="card">
          <div className="card-body">
            <h5 className="card-title mb-3">Your Membership</h5>
            <div className="row g-3">
              <div className="col-sm-6">
                <div className="text-muted small">Membership ID</div>
                <div>{myMember.membershipId || '—'}</div>
              </div>
              <div className="col-sm-6">
                <div className="text-muted small">Status</div>
                <div>{myMember.status || '—'}</div>
              </div>
              <div className="col-sm-6">
                <div className="text-muted small">Type</div>
                <div>{myMember.membershipType || '—'}</div>
              </div>
              <div className="col-sm-6">
                <div className="text-muted small">Active</div>
                <div>{String(myMember.isActive ?? '')}</div>
              </div>
              <div className="col-sm-6">
                <div className="text-muted small">Start</div>
                <div>{myMember.membershipStartDate || '—'}</div>
              </div>
              <div className="col-sm-6">
                <div className="text-muted small">End</div>
                <div>{myMember.membershipEndDate || '—'}</div>
              </div>
            </div>
          </div>
        </div>
      )}

      {!loading && !error && (!members || members.length === 0) && !myMember && (
        <div className="alert alert-info">No members to display. You may not have permission to view the full list, and no personal membership was found.</div>
      )}
    </div>
  );
}
