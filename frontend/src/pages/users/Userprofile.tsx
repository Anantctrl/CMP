
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { motion } from "framer-motion";
import useAuth from "@/auth/store";

function Userprofile() {
  const user = useAuth((state) => state.user);

  return (
    <div className="p-6 max-w-3xl mx-auto space-y-8 select-none">
      {/* Heading */}
      <motion.h1
        initial={{ opacity: 0, y: 10 }}
        animate={{ opacity: 1, y: 0 }}
        className="text-3xl font-bold text-center"
      >
        User Profile
      </motion.h1>

      {/* Profile Card */}
      <Card className="rounded-2xl shadow-md p-6">
        <CardHeader>
          <CardTitle className="text-xl font-semibold">
            Profile Information
          </CardTitle>
        </CardHeader>

        <CardContent className="space-y-6">
          {/* Avatar */}
          <div className="flex flex-col items-center gap-3">
            <Avatar className="w-28 h-28 border shadow-md">
              <AvatarImage src="https://api.dicebear.com/7.x/thumbs/svg?seed=user" />
              <AvatarFallback>U</AvatarFallback>
            </Avatar>

            <Button variant="outline" className="rounded-xl px-5">
              Change Picture
            </Button>
          </div>

          {/* User Details */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">

            {/* Full Name */}
            <div className="space-y-2">
              <Label id="name-label">Full Name</Label>
              <div
                aria-labelledby="name-label"
                className="flex h-9 w-full min-w-0 items-center rounded-xl border border-input bg-transparent px-3 py-1 text-base shadow-xs transition-colors !cursor-default select-none hover:bg-accent md:text-sm"
              >
                {user?.name ?? ""}
              </div>
            </div>

            {/* Email */}
            <div className="space-y-2">
              <Label id="email-label">Email</Label>
              <div
                aria-labelledby="email-label"
                className="flex h-9 w-full min-w-0 items-center rounded-xl border border-input bg-transparent px-3 py-1 text-base shadow-xs transition-colors !cursor-default select-none hover:bg-accent md:text-sm"
              >
                {user?.email ?? ""}
              </div>
            </div>

            {/* Designation */}
            <div className="space-y-2">
              <Label id="policePost-label">Designation</Label>
              <div
                aria-labelledby="policePost-label"
                className="flex h-9 w-full min-w-0 items-center rounded-xl border border-input bg-transparent px-3 py-1 text-base shadow-xs transition-colors !cursor-default select-none hover:bg-accent md:text-sm"
              >
                {user?.policePost ?? ""}
              </div>
            </div>

            {/* Single Application Role */}
            <div className="space-y-2">
              <Label id="role-label">Acquisition</Label>
              <div
                aria-labelledby="role-label"
                className="flex h-9 w-full min-w-0 items-center rounded-xl border border-input bg-transparent px-3 py-1 text-base shadow-xs transition-colors !cursor-default select-none hover:bg-accent md:text-sm"
              >
                {user?.role?.name ?? ""}
              </div>
            </div>

            {/* Provider */}
            <div className="space-y-2">
              <Label id="provider-label">Provider</Label>
              <div
                aria-labelledby="provider-label"
                className="flex h-9 w-full min-w-0 items-center rounded-xl border border-input bg-transparent px-3 py-1 text-base shadow-xs transition-colors !cursor-default select-none hover:bg-accent md:text-sm"
              >
                {user?.provider ?? ""}
              </div>
            </div>

            {/* Enabled */}
            <div className="space-y-2">
              <Label id="enable-label">Enabled</Label>
              <div
                aria-labelledby="enable-label"
                className="flex h-9 w-full min-w-0 items-center rounded-xl border border-input bg-transparent px-3 py-1 text-base shadow-xs transition-colors !cursor-default select-none hover:bg-accent md:text-sm"
              >
                {user?.enable ? "Yes" : "No"}
              </div>
            </div>

          </div>
        </CardContent>
      </Card>

      {/* Account Settings */}
      <Card className="rounded-2xl shadow-md p-6">
        <CardHeader>
          <CardTitle className="text-xl">Account Settings</CardTitle>
        </CardHeader>

        <CardContent className="space-y-4">
          <Button
            variant="outline"
            className="w-full rounded-xl py-3 text-base"
          >
            Change Password
          </Button>

          <Button
            variant="destructive"
            className="w-full rounded-xl py-3 text-base"
          >
            Delete Account
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}

export default Userprofile;
