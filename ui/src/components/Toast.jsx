import React, { useEffect } from 'react';

export default function Toast({ show, onClose, variant = 'success', message, autoHide = 3000 }) {
  useEffect(() => {
    if (!show) return;
    if (!autoHide) return;
    const t = setTimeout(() => onClose && onClose(), autoHide);
    return () => clearTimeout(t);
  }, [show, autoHide, onClose]);

  if (!show) return null;
  return (
    <div className="position-fixed bottom-0 end-0 p-3" style={{ zIndex: 2000 }}>
      <div className={`toast show border-0 text-white bg-${variant}`} role="status" aria-live="polite" aria-atomic="true">
        <div className="d-flex align-items-center">
          <div className="toast-body">{message}</div>
          <button type="button" className="btn-close btn-close-white me-2 m-auto" aria-label="Close" onClick={onClose}></button>
        </div>
      </div>
    </div>
  );
}

