import React from 'react';
import UserMenu from './UserMenu';

const mockUser = {
  id: '1',
  firstName: 'Jomon',
  lastName: 'Vacha',
  email: 'jomon@example.com',
};

export default function AppHeader() {
  return (
    <header
      className="sticky top-0 z-40 bg-white/80 dark:bg-neutral-900/80 backdrop-blur border-b dark:border-neutral-800">
      <div className="max-w-7xl mx-auto px-4 h-14 flex items-center">
        <div className="font-semibold">MyApp</div>
        <div className="flex-1"/>
        <UserMenu user={mockUser}/>
      </div>
    </header>
  );
}

