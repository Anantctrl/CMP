
export interface UserRole {
  name: string;
}

export default interface User {
  id: string;
  email: string;
  name: string;
  policePost?: string;
  role?: UserRole;
  enable: boolean;
  image?: string;
  updatedAt?: string;
  createdAt?: string;
  provider: string;
}
