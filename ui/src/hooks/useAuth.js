import { createContext, useContext } from 'react';

export const AuthContext = createContext({
  user: null,
  loading: true,
  error: null,
  refreshMe: async () => {},
});

export function useAuth() {
  return useContext(AuthContext);
}

