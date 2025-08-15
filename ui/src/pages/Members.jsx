import React, {useEffect, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {getMemberByUserId, listMembers, updateMember, deleteMember, deactivateMember, validateEndpoint} from '../api/client.js';
import {useAuth} from '../hooks/useAuth.js';

export default function Members() {
  const navigate = useNavigate();
  const {user} = useAuth();
  const [members, setMembers] = useState([]);
  const [myMember, setMyMember] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [endpoints, setEndpoints] = useState({ canUpdate: true, canDelete: true, canDeactivate: true });
  const [editing, setEditing] = useState(null);
  const [confirm, setConfirm] = useState(null); // { action: 'delete'|'deactivate', member }
  // Auto-dismiss error alerts
  useEffect(() => {
    if (!error) return;
    const t = setTimeout(() => setError(null), 4000);
    return () => clearTimeout(t);
  }, [error]);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      try {
        // Determine if user is privileged to list all members
        const roles = user?.roles || [];
        const isPrivileged = roles.includes('ROLE_ADMIN') || roles.includes('ROLE_MANAGER') || roles.includes('ROLE_MODERATOR');
        if (isPrivileged) {
          const data = await listMembers();
          if (!cancelled) setMembers(data || []);
          // Check endpoints availability once for better UX
          const [canUpdate, canDelete, canDeactivatePost, canDeactivatePut] = await Promise.all([
            validateEndpoint('/api/members/1', 'PUT'),
            validateEndpoint('/api/members/1', 'DELETE'),
            validateEndpoint('/api/members/1/deactivate', 'POST'),
            validateEndpoint('/api/members/1', 'PUT'),
          ]);
          if (!cancelled) setEndpoints({
            canUpdate: canUpdate !== false,
            canDelete: canDelete !== false,
            canDeactivate: (canDeactivatePost !== false) || (canDeactivatePut !== false),
          });
        } else if (user?.id) {
          const mine = await getMemberByUserId(user.id);
          if (!cancelled) setMyMember(mine || null);
        } else {
          // No user loaded
          if (!cancelled) setError('User not loaded');
        }
      } catch (err) {
        if (err?.status === 401) {
          navigate('/signin', {replace: true, state: {from: {pathname: '/members'}}});
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
    return () => {
      cancelled = true;
    };
  }, [navigate]);

  const isAdmin = (user?.roles || []).some((r) => ['ROLE_ADMIN','ROLE_MANAGER','ROLE_MODERATOR'].includes(r));

  async function reload() {
    try {
      setLoading(true);
      const data = await listMembers();
      setMembers(data || []);
    } catch (e) {
      setError(e?.message || 'Failed to refresh');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div>
      <div className="d-flex align-items-center justify-content-between mb-3">
        <h1 className="h4 mb-0">Members</h1>
        {user && <span className="text-body-secondary small">Signed in as {user.name || user.email}</span>}
      </div>

      {loading && (
        <div className="text-center py-5 text-body-secondary">Loading members…</div>
      )}
      {error && (
        <div className="alert alert-danger d-flex justify-content-between align-items-center" role="alert" aria-live="polite">
          <div>{error}</div>
          <button type="button" className="btn-close" aria-label="Close" onClick={() => setError(null)}></button>
        </div>
      )}

      {!loading && !error && members && members.length > 0 && (
        <div className="table-responsive">
          <table className="table table-striped align-middle">
            <thead>
            <tr>
              <th scope="col">#</th>
              <th scope="col">Name</th>
              <th scope="col">Email</th>
              {isAdmin && <th scope="col" className="text-end">Actions</th>}
            </tr>
            </thead>
            <tbody>
            {members.map((m, idx) => {
              const u = m.user || {};
              const name = u.firstName || u.lastName ? `${u.firstName || ''}${u.lastName ? ' ' + u.lastName : ''}`.trim() : (u.username || '—');
              const email = u.email || '—';
              const active = m.isActive ?? true;
              return (
                <tr key={m.id || idx}>
                  <th scope="row">{idx + 1}</th>
                  <td>{name || '—'}</td>
                  <td>{email}</td>
                  {isAdmin && (
                    <td className="text-end">
                      <div className="btn-group">
                        <button
                          type="button"
                          className="btn btn-sm btn-outline-secondary"
                          disabled={!endpoints.canUpdate}
                          onClick={() => setEditing({ member: m })}
                          aria-label="Edit member"
                          title="Edit"
                        >
                          {/* Pencil icon */}
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04a1.003 1.003 0 0 0 0-1.42l-2.34-2.34a1.003 1.003 0 0 0-1.42 0l-1.83 1.83 3.75 3.75 1.84-1.82z"/></svg>
                        </button>
                        <button
                          type="button"
                          className="btn btn-sm btn-outline-secondary"
                          disabled={!endpoints.canDeactivate || !active}
                          onClick={() => setConfirm({ action: 'deactivate', member: m })}
                          aria-label="Deactivate member"
                          title="Deactivate"
                        >
                          {/* Power icon */}
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M13 3h-2v10h2z"/><path d="M17.83 5.17l-1.41 1.41A7 7 0 1 1 7.58 6.58L6.17 5.17a9 9 0 1 0 11.66 0z"/></svg>
                        </button>
                        <button
                          type="button"
                          className="btn btn-sm btn-outline-danger"
                          disabled={!endpoints.canDelete}
                          onClick={() => setConfirm({ action: 'delete', member: m })}
                          aria-label="Delete member"
                          title="Delete"
                        >
                          {/* Trash icon */}
                          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M6 19a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V7H6zm3-9h2v8H9zm4 0h2v8h-2z"/><path d="M15.5 4l-1-1h-5l-1 1H5v2h14V4z"/></svg>
                        </button>
                      </div>
                    </td>
                  )}
                </tr>
              );
            })}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        <EditMemberModal
          member={editing.member}
          onClose={() => setEditing(null)}
          onSaved={reload}
        />
      )}

      {confirm && (
        <ConfirmAction
          action={confirm.action}
          member={confirm.member}
          onCancel={() => setConfirm(null)}
          onConfirm={async () => {
            const m = confirm.member;
            try {
              if (confirm.action === 'delete') {
                await deleteMember(m.id);
              } else if (confirm.action === 'deactivate') {
                await deactivateMember(m.id);
              }
              setConfirm(null);
              await reload();
            } catch (e) {
              setError(e?.message || 'Action failed');
              setConfirm(null);
            }
          }}
        />
      )}

      {!loading && !error && (!members || members.length === 0) && myMember && (
        <div className="card">
          <div className="card-body">
            <h5 className="card-title mb-3">Your Membership</h5>
            <div className="row g-3">
              <div className="col-sm-6">
                <div className="text-body-secondary small">Membership ID</div>
                <div>{myMember.membershipId || '—'}</div>
              </div>
              <div className="col-sm-6">
                <div className="text-body-secondary small">Status</div>
                <div>{myMember.status || '—'}</div>
              </div>
              <div className="col-sm-6">
                <div className="text-body-secondary small">Type</div>
                <div>{myMember.membershipType || '—'}</div>
              </div>
              <div className="col-sm-6">
                <div className="text-body-secondary small">Active</div>
                <div>{String(myMember.isActive ?? '')}</div>
              </div>
              <div className="col-sm-6">
                <div className="text-body-secondary small">Start</div>
                <div>{myMember.membershipStartDate || '—'}</div>
              </div>
              <div className="col-sm-6">
                <div className="text-body-secondary small">End</div>
                <div>{myMember.membershipEndDate || '—'}</div>
              </div>
            </div>
          </div>
        </div>
      )}

      {!loading && !error && (!members || members.length === 0) && !myMember && (
        <div className="alert alert-info">No members to display. You may not have permission to view the full list, and
          no personal membership was found.</div>
      )}
    </div>
  );
}

function ConfirmAction({action, member, onCancel, onConfirm}) {
  const [working, setWorking] = React.useState(false);
  const title = action === 'delete' ? 'Delete Member' : 'Deactivate Member';
  const description = action === 'delete'
    ? 'This action permanently removes the member. This cannot be undone.'
    : 'This action deactivates the member. They will lose access until reactivated.';
  async function run() {
    if (working) return;
    setWorking(true);
    try {
      await onConfirm();
    } finally {
      setWorking(false);
    }
  }
  const u = member?.user || {};
  const name = u.firstName || u.lastName ? `${u.firstName || ''}${u.lastName ? ' ' + u.lastName : ''}`.trim() : (u.username || '—');
  const email = u.email || '—';
  return (
    <div className="position-fixed top-0 start-0 w-100 h-100" style={{zIndex: 1060}}>
      <div className="position-absolute top-0 start-0 w-100 h-100 bg-dark opacity-50" onClick={() => !working && onCancel()}></div>
      <div className="position-absolute top-0 start-0 w-100 h-100 d-flex align-items-start justify-content-center p-3 p-md-4 overflow-auto">
        <div className="bg-body rounded shadow" style={{maxWidth: '28rem', width: '100%'}}>
          <div className="p-3 border-bottom d-flex justify-content-between align-items-center">
            <div className="h6 mb-0">{title}</div>
            <button className="btn btn-sm btn-outline-secondary" onClick={() => !working && onCancel()} disabled={working}>×</button>
          </div>
          <div className="p-3">
            <p className="text-body-secondary mb-2">{description}</p>
            <div className="border rounded p-2 small mb-3">
              <div><span className="text-body-secondary">Name:</span> {name}</div>
              <div><span className="text-body-secondary">Email:</span> {email}</div>
              <div><span className="text-body-secondary">ID:</span> {member?.id || '—'}</div>
            </div>
            <div className="d-flex justify-content-end gap-2">
              <button type="button" className="btn btn-outline-secondary" disabled={working} onClick={() => !working && onCancel()}>Cancel</button>
              <button type="button" className={"btn " + (action === 'delete' ? 'btn-danger' : 'btn-warning')} onClick={run} disabled={working}>
                {working ? 'Working…' : (action === 'delete' ? 'Delete' : 'Deactivate')}
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// Inline edit modal with minimal fields
function EditMemberModal({member, onClose, onSaved}) {
  const [saving, setSaving] = React.useState(false);
  const [status, setStatus] = React.useState(member?.status || '');
  const [membershipType, setMembershipType] = React.useState(member?.membershipType || '');
  const [active, setActive] = React.useState(Boolean(member?.isActive));
  const [error, setError] = React.useState(null);

  async function submit(e) {
    e.preventDefault();
    setError(null);
    setSaving(true);
    try {
      const payload = { status, membershipType, isActive: active };
      await updateMember(member.id, payload);
      onSaved && onSaved();
      onClose();
    } catch (err) {
      setError(err?.message || 'Update failed');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="position-fixed top-0 start-0 w-100 h-100" style={{zIndex: 1060}}>
      <div className="position-absolute top-0 start-0 w-100 h-100 bg-dark opacity-50" onClick={() => !saving && onClose()}></div>
      <div className="position-absolute top-0 start-0 w-100 h-100 d-flex align-items-start justify-content-center p-3 p-md-4 overflow-auto">
        <div className="bg-body rounded shadow" style={{maxWidth: '32rem', width: '100%'}}>
          <div className="p-3 border-bottom d-flex justify-content-between align-items-center">
            <div className="h6 mb-0">Edit Member</div>
            <button className="btn btn-sm btn-outline-secondary" onClick={() => !saving && onClose()} disabled={saving}>×</button>
          </div>
          <form className="p-3" onSubmit={submit}>
            {error && <div className="alert alert-danger">{error}</div>}
            <div className="mb-3">
              <label className="form-label">Status</label>
              <input className="form-control" value={status} onChange={(e) => setStatus(e.target.value)} placeholder="e.g., active, pending" />
            </div>
            <div className="mb-3">
              <label className="form-label">Membership Type</label>
              <input className="form-control" value={membershipType} onChange={(e) => setMembershipType(e.target.value)} placeholder="e.g., gold, standard" />
            </div>
            <div className="form-check form-switch mb-3">
              <input className="form-check-input" type="checkbox" id="editActive" checked={active} onChange={(e) => setActive(e.target.checked)} />
              <label className="form-check-label" htmlFor="editActive">Active</label>
            </div>
            <div className="d-flex justify-content-end gap-2">
              <button type="button" className="btn btn-outline-secondary" disabled={saving} onClick={() => !saving && onClose()}>Cancel</button>
              <button type="submit" className="btn btn-primary" disabled={saving}>{saving ? 'Saving…' : 'Save'}</button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
