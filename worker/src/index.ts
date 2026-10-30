/**
 * Smoke Grid sync API. One account: the email in ACCOUNT_EMAIL with the password hashed in PASSWORD_HASH.
 *
 *   POST /login   {email, password, device}        -> {token}
 *   POST /sync    {since, days[], firstDay?}       -> {now, days[], firstDay}   (Bearer token)
 *   POST /logout                                   -> {}                        (Bearer token)
 *
 * A day is {date: "2026-10-30", count, updatedAt}. The newest updatedAt wins.
 */

// Minimal shapes of the Workers runtime types we use (wrangler's bundler strips types).
interface D1Result<T> { results: T[] }
interface D1Statement {
  bind(...values: unknown[]): D1Statement;
  first<T>(): Promise<T | null>;
  all<T>(): Promise<D1Result<T>>;
  run(): Promise<unknown>;
}
interface D1Database {
  prepare(sql: string): D1Statement;
  batch(statements: D1Statement[]): Promise<unknown>;
}

interface Env {
  DB: D1Database;
  ACCOUNT_EMAIL: string;
  /** "pbkdf2$<iterations>$<salt b64>$<hash b64>", made by scripts/hash-password.mjs */
  PASSWORD_HASH: string;
}

interface Day { date: string; count: number; updatedAt: number }

const MAX_FAILURES = 10;
const FAILURE_WINDOW_MS = 15 * 60 * 1000;
const DATE = /^\d{4}-\d{2}-\d{2}$/;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const { pathname } = new URL(request.url);
    if (request.method !== "POST") return json({ error: "not found" }, 404);
    try {
      if (pathname === "/login") return await login(request, env);
      if (pathname === "/sync" || pathname === "/logout") {
        const tokenHash = await authorize(request, env);
        if (!tokenHash) return json({ error: "unauthorized" }, 401);
        if (pathname === "/logout") {
          await env.DB.prepare("DELETE FROM tokens WHERE hash = ?").bind(tokenHash).run();
          return json({});
        }
        return await sync(request, env);
      }
      return json({ error: "not found" }, 404);
    } catch (e) {
      console.error(e);
      return json({ error: "server error" }, 500);
    }
  },
};

async function login(request: Request, env: Env): Promise<Response> {
  const body = (await request.json().catch(() => null)) as { email?: string; password?: string; device?: string } | null;
  if (!body?.email || !body?.password) return json({ error: "email and password required" }, 400);

  const now = Date.now();
  const windowStart = Number((await getMeta(env, "fail_since")) ?? 0);
  const failures = now - windowStart < FAILURE_WINDOW_MS ? Number((await getMeta(env, "fail_count")) ?? 0) : 0;
  if (failures >= MAX_FAILURES) return json({ error: "too many attempts, try again in 15 minutes" }, 429);

  const emailOk = body.email.trim().toLowerCase() === env.ACCOUNT_EMAIL.trim().toLowerCase();
  const passwordOk = await verifyPassword(body.password, env.PASSWORD_HASH);
  if (!emailOk || !passwordOk) {
    await env.DB.batch([
      setMeta(env, "fail_count", String(failures + 1)),
      setMeta(env, "fail_since", String(failures === 0 ? now : windowStart)),
    ]);
    return json({ error: "wrong email or password" }, 401);
  }

  const token = base64url(crypto.getRandomValues(new Uint8Array(32)));
  await env.DB.batch([
    env.DB.prepare("INSERT INTO tokens (hash, device, created_at) VALUES (?, ?, ?)")
      .bind(await sha256Hex(token), String(body.device ?? "phone").slice(0, 80), now),
    setMeta(env, "fail_count", "0"),
  ]);
  return json({ token });
}

async function sync(request: Request, env: Env): Promise<Response> {
  const body = (await request.json().catch(() => null)) as { since?: number; days?: unknown[]; firstDay?: string } | null;
  if (!body) return json({ error: "bad request" }, 400);
  const since = Number.isFinite(body.since) ? Number(body.since) : 0;
  const days = (body.days ?? []).filter(isDay);
  const now = Date.now();

  const writes: D1Statement[] = days.map((d) =>
    env.DB.prepare(
      `INSERT INTO days (date, count, updated_at, synced_at) VALUES (?, ?, ?, ?)
       ON CONFLICT (date) DO UPDATE SET count = excluded.count, updated_at = excluded.updated_at, synced_at = excluded.synced_at
       WHERE excluded.updated_at > days.updated_at`,
    ).bind(d.date, d.count, d.updatedAt, now),
  );
  if (body.firstDay && DATE.test(body.firstDay)) {
    // The earliest first day any phone has seen.
    writes.push(
      env.DB.prepare(
        `INSERT INTO meta (key, value) VALUES ('first_day', ?)
         ON CONFLICT (key) DO UPDATE SET value = excluded.value WHERE excluded.value < meta.value`,
      ).bind(body.firstDay),
    );
  }
  if (writes.length) await env.DB.batch(writes);

  const changed = await env.DB.prepare("SELECT date, count, updated_at FROM days WHERE synced_at >= ? ORDER BY date")
    .bind(since)
    .all<{ date: string; count: number; updated_at: number }>();
  return json({
    now,
    days: changed.results.map((r) => ({ date: r.date, count: r.count, updatedAt: r.updated_at })),
    firstDay: await getMeta(env, "first_day"),
  });
}

function isDay(value: unknown): value is Day {
  const d = value as Day;
  return !!d && typeof d.date === "string" && DATE.test(d.date)
    && Number.isInteger(d.count) && d.count >= 0 && d.count <= 1000
    && Number.isFinite(d.updatedAt);
}

async function authorize(request: Request, env: Env): Promise<string | null> {
  const header = request.headers.get("Authorization") ?? "";
  if (!header.startsWith("Bearer ")) return null;
  const hash = await sha256Hex(header.slice(7));
  const row = await env.DB.prepare("SELECT hash FROM tokens WHERE hash = ?").bind(hash).first<{ hash: string }>();
  return row ? hash : null;
}

async function verifyPassword(password: string, stored: string): Promise<boolean> {
  const [scheme, iterations, salt, expected] = (stored ?? "").split("$");
  if (scheme !== "pbkdf2" || !iterations || !salt || !expected) return false;
  const key = await crypto.subtle.importKey("raw", new TextEncoder().encode(password), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt: fromBase64(salt), iterations: Number(iterations) },
    key,
    256,
  );
  const actual = new Uint8Array(bits);
  const wanted = fromBase64(expected);
  if (actual.length !== wanted.length) return false;
  let diff = 0;
  for (let i = 0; i < actual.length; i++) diff |= actual[i] ^ wanted[i];
  return diff === 0;
}

async function getMeta(env: Env, key: string): Promise<string | null> {
  const row = await env.DB.prepare("SELECT value FROM meta WHERE key = ?").bind(key).first<{ value: string }>();
  return row?.value ?? null;
}

function setMeta(env: Env, key: string, value: string): D1Statement {
  return env.DB.prepare("INSERT INTO meta (key, value) VALUES (?, ?) ON CONFLICT (key) DO UPDATE SET value = excluded.value")
    .bind(key, value);
}

async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function base64url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function fromBase64(text: string): Uint8Array {
  return Uint8Array.from(atob(text), (c) => c.charCodeAt(0));
}

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), { status, headers: { "Content-Type": "application/json" } });
}
