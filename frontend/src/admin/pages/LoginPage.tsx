import { useState, type FormEvent } from "react";
import { isUnauthorized } from "../../api";
import { Button, Card, ErrorMessage, Field, Input } from "../components/ui";
import { useLogin } from "../queries";

export function LoginPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const login = useLogin();

  const submit = (e: FormEvent) => {
    e.preventDefault();
    login.mutate({ username, password }, { onError: () => setPassword("") });
  };

  return (
    <div className="flex min-h-dvh items-center justify-center bg-zinc-50 px-4">
      <Card className="w-full max-w-sm">
        <form onSubmit={submit} className="space-y-5 p-6">
          <h1 className="text-lg font-semibold tracking-tight text-zinc-900">
            Auksjon <span className="font-normal text-zinc-400">admin</span>
          </h1>
          <Field label="Brukernavn">
            <Input
              name="username"
              autoComplete="username"
              autoCapitalize="none"
              autoFocus
              required
              value={username}
              onChange={(e) => setUsername(e.target.value)}
            />
          </Field>
          <Field label="Passord">
            <Input
              name="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          </Field>
          {login.error && (
            <ErrorMessage error={isUnauthorized(login.error) ? "Feil brukernavn eller passord" : login.error} />
          )}
          <Button type="submit" className="w-full" disabled={login.isPending}>
            {login.isPending ? "Logger inn…" : "Logg inn"}
          </Button>
        </form>
      </Card>
    </div>
  );
}
