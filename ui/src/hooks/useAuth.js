import {createContext, useContext} from 'react';

export const AuthContext = createContext({
  user: null,
  loading: true,
  error: null,
  refreshMe: async () => {
  },
  idleWarning: false,
  idleCountdown: 0,
  extendSession: () => {
  },
  confirmSignout: () => {
  },
});

export function useAuth() {
  return useContext(AuthContext);
}
