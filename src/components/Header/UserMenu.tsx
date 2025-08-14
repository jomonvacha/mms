import React, { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import AccountModal from '../AccountModal/AccountModal';
import { logout } from '../../services/api';

type User = {
  id: string;
  firstName: string;
  lastName: string;
  email?: string;
  avatarUrl?: string;
};

type Props = { user: User };

type MenuItem = { key: 'profile' | 'account' | 'preferences' | 'logout'; label: string; disabled?: boolean };

export default function UserMenu({ user }: Props) {
  const [open, setOpen] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [initialTab, setInitialTab] = useState<'profile' | 'account' | 'preferences'>('profile');
  const btnRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const [activeIndex, setActiveIndex] = useState(0);
  const [position, setPosition] = useState<{ top: number; left: number } | null>(null);

  const items: MenuItem[] = useMemo(() => ([
    { key: 'profile', label: 'Profile' },
    { key: 'account', label: 'Account' },
    { key: 'preferences', label: 'Preferences' },
    { key: 'logout', label: 'Logout' },
  ]), []);

  // Close on outside click and keep dropdown positioned via portal to avoid clipping
  useEffect(() => {
    if (!open) return;
    const updatePos = () => {
      const btn = btnRef.current;
      if (!btn) return;
      const rect = btn.getBoundingClientRect();
      const menuWidth = 256; // w-64
      const left = Math.max(8, Math.min(window.innerWidth - 8 - menuWidth, rect.right - menuWidth));
      const top = rect.bottom + 8;
      setPosition({ top, left });
    };
    updatePos();
    const onClick = (e: MouseEvent) => {
      const t = e.target as Node;
      if (menuRef.current && menuRef.current.contains(t)) return;
      if (btnRef.current && btnRef.current.contains(t)) return;
      setOpen(false);
    };
    const onFocusIn = (e: FocusEvent) => {
      const t = e.target as Node;
      if (menuRef.current && menuRef.current.contains(t)) return;
      if (btnRef.current && btnRef.current.contains(t)) return;
      setOpen(false);
    };
    document.addEventListener('mousedown', onClick);
    document.addEventListener('focusin', onFocusIn);
    window.addEventListener('resize', updatePos);
    window.addEventListener('scroll', updatePos, true);
    return () => {
      document.removeEventListener('mousedown', onClick);
      document.removeEventListener('focusin', onFocusIn);
      window.removeEventListener('resize', updatePos);
      window.removeEventListener('scroll', updatePos, true);
    };
  }, [open]);

  const openMenu = () => {
    setOpen(true);
    setActiveIndex(0);
    // Move focus to menu container next tick so keydown works immediately
    requestAnimationFrame(() => { menuRef.current?.focus(); });
  };
  const closeMenu = () => setOpen(false);

  const onKeyDownButton = (e: React.KeyboardEvent<HTMLButtonElement>) => {
    if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openMenu(); }
    if ((e.key === 'ArrowDown' || e.key === 'ArrowUp') && !open) { e.preventDefault(); openMenu(); }
  };

  const onKeyDownMenu = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (e.key === 'Escape') { closeMenu(); btnRef.current?.focus(); }
    if (e.key === 'ArrowDown') { e.preventDefault(); setActiveIndex(i => (i + 1) % items.length); }
    if (e.key === 'ArrowUp') { e.preventDefault(); setActiveIndex(i => (i - 1 + items.length) % items.length); }
    if (e.key === 'Home') { e.preventDefault(); setActiveIndex(0); }
    if (e.key === 'End') { e.preventDefault(); setActiveIndex(items.length - 1); }
    if (e.key === 'Enter') { e.preventDefault(); activate(items[activeIndex]); }
  };

  const activate = async (item: MenuItem) => {
    if (item.key === 'logout') {
      try { await logout(); } catch { /* noop */ }
      window.location.assign('/login');
      return;
    }
    setInitialTab(item.key);
    setModalOpen(true);
    closeMenu();
  };

  const fullName = `${user.firstName} ${user.lastName}`.trim();

  return (
    <div className="relative">
      <button
        ref={btnRef}
        type="button"
        onClick={() => { open ? closeMenu() : openMenu(); }}
        onKeyDown={onKeyDownButton}
        aria-haspopup="menu"
        aria-controls="user-menu"
        aria-expanded={open}
        className="px-3 py-2 rounded-xl hover:bg-neutral-100 dark:hover:bg-neutral-800 focus:outline-none focus:ring-2"
      >
        {user.firstName}
      </button>

      {open && position && createPortal(
        <div
          id="user-menu"
          role="menu"
          aria-label="User menu"
          ref={menuRef}
          tabIndex={-1}
          onKeyDown={onKeyDownMenu}
          className="fixed z-50 w-64 rounded-2xl shadow-lg ring-1 ring-black/10 bg-white dark:bg-neutral-900 p-2"
          style={{ top: position.top, left: position.left }}
        >
          <div className="flex items-center gap-3 px-3 py-2 rounded-xl">
            <img
              src={user.avatarUrl || 'https://www.gravatar.com/avatar/?d=mp'}
              alt="Avatar"
              className="w-10 h-10 rounded-full object-cover"
            />
            <div className="min-w-0">
              <div className="text-sm font-medium truncate">{fullName}</div>
              {user.email && <div className="text-xs text-neutral-500 dark:text-neutral-400 truncate">{user.email}</div>}
            </div>
          </div>
          <div className="my-1 h-px bg-neutral-100 dark:bg-neutral-800" />
          <div className="flex flex-col" role="none">
            {items.slice(0, 3).map((item, idx) => (
              <button
                key={item.key}
                role="menuitem"
                tabIndex={-1}
                className={`flex items-center gap-3 rounded-xl px-3 py-2 text-left hover:bg-neutral-100 dark:hover:bg-neutral-800 focus:outline-none focus:ring-2 ${idx === activeIndex ? 'ring-2' : ''}`}
                onMouseEnter={() => setActiveIndex(idx)}
                onClick={() => activate(item)}
              >
                <span>{item.label}</span>
              </button>
            ))}
            <div className="my-1 h-px bg-neutral-100 dark:bg-neutral-800" />
            {items.slice(3).map((item, idx) => (
              <button
                key={item.key}
                role="menuitem"
                tabIndex={-1}
                className={`flex items-center gap-3 rounded-xl px-3 py-2 text-left hover:bg-neutral-100 dark:hover:bg-neutral-800 focus:outline-none focus:ring-2 ${activeIndex === idx + 3 ? 'ring-2' : ''}`}
                onMouseEnter={() => setActiveIndex(idx + 3)}
                onClick={() => activate(item)}
              >
                <span>{item.label}</span>
              </button>
            ))}
          </div>
        </div>,
        document.body
      )}

      <AccountModal
        isOpen={modalOpen}
        initialTab={initialTab}
        onClose={() => setModalOpen(false)}
        user={user}
      />
    </div>
  );
}
