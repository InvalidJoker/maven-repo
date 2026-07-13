import type { ReactNode } from "react";
import { useAuth } from "./auth";
import { useInstance } from "./instance";
import { navigate, segments, useHashRoute } from "./router";
import { DemoBanner } from "./components/DemoBanner";
import { Layout } from "./components/Layout";
import { Login } from "./pages/Login";
import { Dashboard } from "./pages/Dashboard";
import { Browser } from "./pages/Browser";
import { Tokens } from "./pages/Tokens";
import { Admin } from "./pages/Admin";
import { AdminUsers } from "./pages/AdminUsers";
import { AdminConfig } from "./pages/AdminConfig";
import { RepoPermissions } from "./pages/RepoPermissions";
import { Button } from "./ui";
import type { User } from "./api";

function Centered({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-full items-center justify-center text-sm text-neutral-500">
      {children}
    </div>
  );
}

function NeedsAuth() {
  return (
    <Centered>
      <div className="text-center">
        <p className="mb-3">Please sign in to access this page.</p>
        <Button onClick={() => navigate("/login")}>Sign in</Button>
      </div>
    </Centered>
  );
}

function resolve(parts: string[], user: User | null): ReactNode {
  if (parts[0] === "repo" && parts[1]) {
    return <Browser repo={parts[1]} path={parts.slice(2).join("/")} />;
  }

  if (parts[0] === "tokens") {
    return user ? <Tokens /> : <NeedsAuth />;
  }

  if (parts[0] === "admin") {
    if (!user) return <NeedsAuth />;
    if (!user.admin)
      return <Centered>You don't have access to this page.</Centered>;
    if (parts[1] === "repos" && parts[2])
      return <RepoPermissions repo={parts[2]} />;
    if (parts[1] === "users") return <AdminUsers />;
    if (parts[1] === "config") return <AdminConfig />;
    return <Admin />;
  }

  return <Dashboard />;
}

export default function App() {
  const { user, loading } = useAuth();
  const { demo } = useInstance();
  const route = useHashRoute();

  let content: ReactNode;
  if (loading) {
    content = <Centered>Loading…</Centered>;
  } else {
    const parts = segments(route);
    content =
      !user && parts[0] === "login" ? (
        <Login />
      ) : (
        <Layout>{resolve(parts, user)}</Layout>
      );
  }

  return (
    <div className="flex min-h-full flex-col">
      {demo && <DemoBanner />}
      <main className="flex flex-1 flex-col">{content}</main>
    </div>
  );
}
