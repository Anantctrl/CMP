import type RegisterData from "@/models/RegisterData";
import apiClient from "@/config/apiClient";
import type LoginData from "@/models/LoginData";
import type LoginResponseData from "@/models/LoginResponseData";
import type User from "@/models/User";

// Register function with optional supervisor flag
export const registerUser = async (signupData: RegisterData) => {
  const response = await apiClient.post(`/auth/register`, {
    name: signupData.name,
    email: signupData.email,
    password: signupData.password,
    policePost: signupData.policePost,
    supervisor: signupData.supervisor,
  });
  return response.data;
};

// Login
export const loginUser = async (loginData: LoginData) => {
  const response = await apiClient.post<LoginResponseData>(
    "/auth/login",
    loginData
  );
  return response.data;
};

export const logoutUser = async () => {
    try {
        const response = await apiClient.post("/auth/logout");
        return response.data;
    } catch (error) {
        console.error("Logout API failed:", error);
        throw error;
    } finally {
        localStorage.removeItem("token");
    }
};

// Get current login user
export const getCurrentUser = async (emailId: string | undefined) => {
  const response = await apiClient.get<User>(`/users/email/${emailId}`);
  return response.data;
};

// Refresh token
export const refreshToken = async () => {
  const response = await apiClient.post<LoginResponseData>(`/auth/refresh`);
  return response.data;
};
