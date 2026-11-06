// One-off dev seed: signs in as the bootstrap admin and registers a handful of
// catalog products so the frontend has something to show against the real
// backend. Run with: node scripts/seed-backend.mjs
// Requires docker-compose up (see ../../.env for BOOTSTRAP_ADMIN_*).
//
// SDD-024 F3 (login via Keycloak):
// - No more /auth/login or /auth/change-password (they no longer exist). The admin
//   token comes from Keycloak with the same Authorization Code + PKCE flow of the SPA
//   (public client "web"), submitting the Keycloak login form from here. The realm has
//   no password grant. Needs BOOTSTRAP_ADMIN_PASSWORD set (admin with a password).
// - Staff accounts are no longer created here: the customer-management creates them
//   (local + Keycloak, known dev password) when it runs with the "dev" profile
//   (SPRING_PROFILES_ACTIVE=dev, see DevStaffSeedRunner and customer-management/README.md).
// - Customers keep coming from the public sign-up (/cadastro).

import { createHash, randomBytes } from "node:crypto";

const PRODUCTS = "http://localhost:8081";

const KEYCLOAK_URL = (process.env.KEYCLOAK_URL ?? "http://localhost:8180").replace(/\/$/, "");
const KEYCLOAK_REALM = process.env.KEYCLOAK_REALM ?? "petshop";
const KEYCLOAK_CLIENT_ID = process.env.KEYCLOAK_CLIENT_ID ?? "web";
// Any redirect_uri allowed for the "web" client; never opened (the code is read from Location).
const REDIRECT_URI = process.env.KEYCLOAK_REDIRECT_URI ?? "http://localhost:5173/auth/callback";

const ADMIN_EMAIL = process.env.BOOTSTRAP_ADMIN_EMAIL ?? "admin@petshop.local";
const ADMIN_PASSWORD = process.env.BOOTSTRAP_ADMIN_PASSWORD ?? "Admin@12345";

async function json(res) {
  const text = await res.text();
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

const base64url = (buffer) => buffer.toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");

function cookieHeader(jar) {
  return [...jar.entries()].map(([name, value]) => `${name}=${value}`).join("; ");
}

function keepCookies(jar, res) {
  for (const cookie of res.headers.getSetCookie()) {
    const [pair] = cookie.split(";");
    const eq = pair.indexOf("=");
    jar.set(pair.slice(0, eq).trim(), pair.slice(eq + 1).trim());
  }
}

function decodeHtml(value) {
  return value.replace(/&amp;/g, "&").replace(/&#x3d;|&#61;/gi, "=").replace(/&quot;/g, '"');
}

// Authorization Code + PKCE (S256) against the Keycloak login form.
async function keycloakLogin(username, password) {
  const realmUrl = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}/protocol/openid-connect`;
  const verifier = base64url(randomBytes(32));
  const challenge = base64url(createHash("sha256").update(verifier).digest());
  const authUrl = new URL(`${realmUrl}/auth`);
  authUrl.search = new URLSearchParams({
    client_id: KEYCLOAK_CLIENT_ID,
    redirect_uri: REDIRECT_URI,
    response_type: "code",
    scope: "openid",
    state: base64url(randomBytes(16)),
    code_challenge: challenge,
    code_challenge_method: "S256",
  }).toString();

  const jar = new Map();
  const page = await fetch(authUrl, { redirect: "manual" });
  keepCookies(jar, page);
  const html = await page.text();
  const formTag = html.match(/<form[^>]*\bid="kc-form-login"[^>]*>/)?.[0];
  const action = formTag?.match(/\baction="([^"]+)"/)?.[1];
  if (!action) throw new Error(`Keycloak login form not found (status ${page.status}). Is Keycloak up at ${KEYCLOAK_URL}?`);

  const submit = await fetch(decodeHtml(action), {
    method: "POST",
    redirect: "manual",
    headers: { "Content-Type": "application/x-www-form-urlencoded", Cookie: cookieHeader(jar) },
    body: new URLSearchParams({ username, password, credentialId: "" }),
  });
  const location = submit.headers.get("location");
  const code = location ? new URL(location).searchParams.get("code") : null;
  if (!code) {
    throw new Error(
      `Keycloak login of ${username} failed (status ${submit.status}): wrong password, user without ` +
        "password (BOOTSTRAP_ADMIN_PASSWORD empty) or a pending required action.",
    );
  }

  const tokenRes = await fetch(`${realmUrl}/token`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "authorization_code",
      client_id: KEYCLOAK_CLIENT_ID,
      code,
      redirect_uri: REDIRECT_URI,
      code_verifier: verifier,
    }),
  });
  const token = await json(tokenRes);
  if (!tokenRes.ok) throw new Error(`token exchange failed: ${tokenRes.status} ${JSON.stringify(token)}`);
  return token.access_token;
}

async function post(baseUrl, path, token, body) {
  const res = await fetch(`${baseUrl}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify(body),
  });
  const data = await json(res);
  return { ok: res.ok, status: res.status, data };
}

const PRODUCTS_SEED = [
  { name: "Ração Premium Cães Adultos 15kg", description: "Alimento completo para cães adultos de porte médio e grande, rico em proteína animal.", price: 189.9, stock: 40, dimensions: { weight: 15, height: 60, width: 40, length: 12 } },
  { name: "Ração Premium Gatos Castrados 10kg", description: "Fórmula balanceada para gatos castrados, com controle de peso e saúde urinária.", price: 159.9, stock: 35, dimensions: { weight: 10, height: 50, width: 35, length: 10 } },
  { name: "Areia Higiênica Aglomerante 4kg", description: "Areia de baixa poeira com alto poder de aglomeração e controle de odor.", price: 34.9, stock: 60, dimensions: { weight: 4, height: 25, width: 20, length: 8 } },
  { name: "Brinquedo Mordedor de Corda", description: "Corda de algodão trançado, ajuda na limpeza dos dentes durante a brincadeira.", price: 24.9, stock: 80, dimensions: { weight: 0.3, height: 20, width: 8, length: 8 } },
  { name: "Coleira Ajustável Nylon", description: "Coleira resistente com fivela de engate rápido, tamanhos P ao G.", price: 39.9, stock: 50, dimensions: { weight: 0.15, height: 2, width: 40, length: 3 } },
  { name: "Shampoo Neutro para Banho 500ml", description: "Shampoo suave, livre de corantes, indicado para cães e gatos de pele sensível.", price: 29.9, stock: 45, dimensions: { weight: 0.55, height: 18, width: 6, length: 6 } },
  { name: "Arranhador para Gatos", description: "Base em sisal natural com pelúcia, estimula o comportamento natural de arranhar.", price: 119.9, stock: 15, dimensions: { weight: 3.2, height: 45, width: 35, length: 35 } },
  { name: "Caminha Acolchoada Média", description: "Caminha macia e lavável, ideal para cães e gatos de até 15kg.", price: 149.9, stock: 20, dimensions: { weight: 1.8, height: 15, width: 60, length: 50 } },
];

async function main() {
  console.log("Signing in to Keycloak as bootstrap admin:", ADMIN_EMAIL);
  const token = await keycloakLogin(ADMIN_EMAIL, ADMIN_PASSWORD);

  console.log("\nSeeding products...");
  for (const product of PRODUCTS_SEED) {
    const { ok, status, data } = await post(PRODUCTS, "/api/v1/products/register", token, product);
    console.log(` ${ok ? "OK " : "ERR"} ${status} ${product.name}`, ok ? "" : JSON.stringify(data));
  }

  console.log(
    "\nStaff accounts: created by the customer-management with SPRING_PROFILES_ACTIVE=dev " +
      "(DevStaffSeedRunner; password in customer-management/README.md).",
  );
  console.log("\nDone.");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
