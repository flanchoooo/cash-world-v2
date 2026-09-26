import { useState, useSyncExternalStore } from "react";
import { Navigate } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import {
  ArrowRight,
  ShieldCheck,
  LockKeyhole,
  Eye,
  EyeOff,
  LoaderCircle,
  CircleAlert,
} from "lucide-react";
import { getSession, signIn, subscribe } from "../lib/session";
import { Brand } from "../components/Brand";
const schema = z.object({
  username: z.string().trim().min(1, "Enter your username.").max(100),
  password: z.string().min(1, "Enter your password."),
});
export function Login() {
  const state = useSyncExternalStore(subscribe, getSession);
  const [visible, setVisible] = useState(false);
  const [error, setError] = useState("");
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema) });
  if (state.phase === "authenticated") return <Navigate to="/" replace />;
  return (
    <div className="login-page">
      <aside className="login-story">
        <Brand large />
        <div className="story-copy">
          <span className="eyebrow">ADMINISTRATION</span>
          <h1>Business operations</h1>
          <p>Manage customers, wallets, payments and access.</p>
        </div>
        <div className="story-footer">
          <ShieldCheck size={18} />
          <span>Secure administrator sign-in</span>
          <span className="edition">CASHWORD / 01</span>
        </div>
      </aside>
      <main className="login-main">
        <div className="login-top">
          <span>Cashword Administration</span>
          <span className="badge neutral">
            <LockKeyhole size={12} /> Restricted access
          </span>
        </div>
        <div className="login-form">
          <div className="login-symbol">
            <LockKeyhole size={24} />
          </div>
          <span className="eyebrow green">SIGN IN</span>
          <h2>Administrator sign-in</h2>
          <p className="muted">
            Use the credentials provided by your administrator.
          </p>
          <form
            onSubmit={handleSubmit(async (values) => {
              setError("");
              try {
                await signIn(values.username, values.password);
              } catch (e) {
                setError(e instanceof Error ? e.message : "Unable to sign in.");
              }
            })}
            noValidate
          >
            {(error || state.message) && (
              <div className="alert" role="alert">
                <CircleAlert size={18} />
                <span>{error || state.message}</span>
              </div>
            )}
            <label htmlFor="username">Username</label>
            <input
              id="username"
              autoComplete="username"
              placeholder="Enter your username"
              aria-invalid={!!errors.username}
              aria-describedby={errors.username ? "username-error" : undefined}
              {...register("username")}
            />
            {errors.username && (
              <small id="username-error" className="field-error">
                {errors.username.message}
              </small>
            )}
            <label htmlFor="password">Password</label>
            <div className="password-field">
              <input
                id="password"
                type={visible ? "text" : "password"}
                autoComplete="current-password"
                placeholder="Enter your password"
                aria-invalid={!!errors.password}
                aria-describedby={
                  errors.password ? "password-error" : undefined
                }
                {...register("password")}
              />
              <button
                type="button"
                className="icon-button"
                aria-label={visible ? "Hide password" : "Show password"}
                onClick={() => setVisible(!visible)}
              >
                {visible ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            {errors.password && (
              <small id="password-error" className="field-error">
                {errors.password.message}
              </small>
            )}
            <button
              className="button primary login-submit"
              disabled={isSubmitting}
              type="submit"
            >
              {isSubmitting ? (
                <>
                  <LoaderCircle className="spin" size={18} /> Signing in…
                </>
              ) : (
                <>
                  Sign in <ArrowRight size={18} />
                </>
              )}
            </button>
          </form>
          <div className="login-help">
            <ShieldCheck size={17} />
            <p>
              Having trouble signing in?
              <br />
              <span>Contact your organisation’s administrator.</span>
            </p>
          </div>
        </div>
        <footer className="login-bottom">
          <span>© {new Date().getFullYear()} Cashword</span>
        </footer>
      </main>
    </div>
  );
}
