import useAuth from "@/auth/store";
import { Navigate, Outlet } from "react-router";

export default function Userlayout() {
  const checkLogin = useAuth((state) => state.checkLogin);

  if (!checkLogin()) {
    return <Navigate to={"/login"} />;
  }

  return (
    <div>
      <Outlet />
    </div>
  );
}
