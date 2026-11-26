export type UserRole = 'CUSTOMER' | 'OFFICER';

export interface AuthTokenPayload {
  userId: number;
  username: string;
  role: UserRole;
  profileId: number;
}

declare global {
  namespace Express {
    interface Request {
      user?: AuthTokenPayload;
    }
  }
}
