import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { listMembers } from '../api/client.js';
import { useAuth } from '../hooks/useAuth.js';

export default function Members() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [members, setMembers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      try {
        const data = await listMembers();
        if (!cancelled) setMembers(data || []);
      } catch (err) {
        if (err?.status === 401) {
          navigate('/signin', { replace: true, state: { from: { pathname: '/members' } } });
          return;
        }
        if (!cancelled) setError(err?.message || 'Failed to load members');
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

      {!loading && !error && (
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
              {members.length === 0 && (
                <tr>
                  <td colSpan={3} className="text-center text-muted">No members found</td>
                </tr>
              )}
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
    </div>
  );
}

