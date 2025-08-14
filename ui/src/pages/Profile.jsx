import React, { useEffect, useState } from 'react';
import { useAuth } from '../hooks/useAuth.js';
import { changePassword, updateMe } from '../api/client.js';

export default function Profile() {
  const { user, refreshMe } = useAuth();
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileMsg, setProfileMsg] = useState(null);
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [changingPassword, setChangingPassword] = useState(false);
  const [passwordMsg, setPasswordMsg] = useState(null);

  useEffect(() => {
    if (user) {
      setFirstName(user.firstName || '');
      setLastName(user.lastName || '');
      setPhoneNumber(user.phoneNumber || '');
    }
  }, [user]);

  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setProfileMsg(null);
    setSavingProfile(true);
    try {
      await updateMe({ firstName, lastName, phoneNumber });
      await refreshMe();
      setProfileMsg({ type: 'success', text: 'Profile updated successfully.' });
    } catch (err) {
      setProfileMsg({ type: 'danger', text: err?.message || 'Failed to update profile' });
    } finally {
      setSavingProfile(false);
    }
  };

  const handleChangePassword = async (e) => {
    e.preventDefault();
    setPasswordMsg(null);
    if (newPassword !== confirmPassword) {
      setPasswordMsg({ type: 'danger', text: 'New passwords do not match.' });
      return;
    }
    setChangingPassword(true);
    try {
      await changePassword({ currentPassword, newPassword });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setPasswordMsg({ type: 'success', text: 'Password changed successfully.' });
    } catch (err) {
      setPasswordMsg({ type: 'danger', text: err?.message || 'Failed to change password' });
    } finally {
      setChangingPassword(false);
    }
  };

  return (
    <div className="row g-4">
      <div className="col-12 col-lg-6">
        <div className="card h-100">
          <div className="card-body">
            <h2 className="h5 mb-3">Update Profile</h2>
            {profileMsg && (
              <div className={`alert alert-${profileMsg.type}`} role="alert">{profileMsg.text}</div>
            )}
            <form onSubmit={handleSaveProfile}>
              <div className="row g-3">
                <div className="col-sm-6">
                  <label htmlFor="firstName" className="form-label">First name</label>
                  <input id="firstName" className="form-control" value={firstName} onChange={(e) => setFirstName(e.target.value)} />
                </div>
                <div className="col-sm-6">
                  <label htmlFor="lastName" className="form-label">Last name</label>
                  <input id="lastName" className="form-control" value={lastName} onChange={(e) => setLastName(e.target.value)} />
                </div>
                <div className="col-12">
                  <label htmlFor="phoneNumber" className="form-label">Phone</label>
                  <input id="phoneNumber" className="form-control" value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} />
                </div>
              </div>
              <div className="mt-3 d-grid d-sm-flex gap-2">
                <button type="submit" className="btn btn-primary" disabled={savingProfile}>{savingProfile ? 'Saving…' : 'Save Changes'}</button>
              </div>
            </form>
          </div>
        </div>
      </div>
      <div className="col-12 col-lg-6">
        <div className="card h-100">
          <div className="card-body">
            <h2 className="h5 mb-3">Change Password</h2>
            {passwordMsg && (
              <div className={`alert alert-${passwordMsg.type}`} role="alert">{passwordMsg.text}</div>
            )}
            <form onSubmit={handleChangePassword}>
              <div className="mb-3">
                <label htmlFor="currentPassword" className="form-label">Current password</label>
                <input id="currentPassword" type="password" className="form-control" value={currentPassword} onChange={(e) => setCurrentPassword(e.target.value)} required />
              </div>
              <div className="mb-3">
                <label htmlFor="newPassword" className="form-label">New password</label>
                <input id="newPassword" type="password" className="form-control" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required />
              </div>
              <div className="mb-3">
                <label htmlFor="confirmPassword" className="form-label">Confirm new password</label>
                <input id="confirmPassword" type="password" className="form-control" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} required />
              </div>
              <div className="d-grid d-sm-flex gap-2">
                <button type="submit" className="btn btn-warning" disabled={changingPassword}>{changingPassword ? 'Changing…' : 'Change Password'}</button>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  );
}

