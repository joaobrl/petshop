// Login via Keycloak (SDD-024 F3): Authorization Code + PKCE (S256, padrão do
// oidc-client-ts) no client público `web` do realm `petshop`.
//
// - Access/refresh token só em memória (InMemoryWebStorage): nada de token em
//   localStorage/sessionStorage. Recarregar a página perde os tokens; a sessão volta
//   pelo SSO do Keycloak (signinSilent num iframe com prompt=none, cookie do Keycloak).
// - Só o estado transitório do redirect (state + code_verifier do PKCE) fica em
//   sessionStorage, porque precisa sobreviver à ida e volta ao Keycloak; é apagado no
//   callback e não contém token.
// - Renovação: refresh token do Keycloak (automaticSilentRenew) e, no 401, uma
//   tentativa compartilhada de renewToken() (ver http.ts).
import { InMemoryWebStorage, UserManager, WebStorageStateStore, type User } from "oidc-client-ts";

export const KEYCLOAK_URL: string = (import.meta.env.VITE_KEYCLOAK_URL ?? "http://localhost:8180").replace(/\/$/, "");
export const KEYCLOAK_REALM: string = import.meta.env.VITE_KEYCLOAK_REALM ?? "petshop";
export const KEYCLOAK_CLIENT_ID: string = import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? "web";

const AUTHORITY = `${KEYCLOAK_URL}/realms/${KEYCLOAK_REALM}`;

// "Minha conta / alterar senha": Account Console do Keycloak.
export const ACCOUNT_CONSOLE_URL = `${AUTHORITY}/account`;

export const CALLBACK_PATH = "/auth/callback";
export const SILENT_CALLBACK_PATH = "/auth/silent";

const origin = window.location.origin;

export const userManager = new UserManager({
  authority: AUTHORITY,
  client_id: KEYCLOAK_CLIENT_ID,
  redirect_uri: `${origin}${CALLBACK_PATH}`,
  silent_redirect_uri: `${origin}${SILENT_CALLBACK_PATH}`,
  post_logout_redirect_uri: `${origin}/`,
  response_type: "code",
  scope: "openid",
  userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
  stateStore: new WebStorageStateStore({ store: window.sessionStorage }),
  automaticSilentRenew: true,
  monitorSession: false,
  loadUserInfo: false,
});

// Antes da SDD-024 F3 o token ficava em localStorage: limpa o resto de versões antigas.
try {
  window.localStorage.removeItem("petshop.token");
} catch {
  // storage indisponível (modo privado): nada a limpar
}

let currentUser: User | null = null;

function track(user: User | null) {
  currentUser = user;
}

userManager.events.addUserLoaded((user) => track(user));
userManager.events.addUserUnloaded(() => track(null));

export function getAccessToken(): string | null {
  return currentUser && !currentUser.expired ? currentUser.access_token : null;
}

// There is a signed-in user (possibly with an expired access token to renew).
export function hasSession(): boolean {
  return currentUser !== null;
}

// One renewal at a time: concurrent 401s share the same request (SDD-019 CA6).
let renewing: Promise<string | null> | null = null;

export function renewToken(): Promise<string | null> {
  if (!renewing) {
    renewing = userManager
      .signinSilent()
      .then((user) => {
        track(user);
        return getAccessToken();
      })
      .catch(() => {
        track(null);
        return null;
      })
      .finally(() => {
        renewing = null;
      });
  }
  return renewing;
}

// Session recovery on page load (StrictMode runs effects twice: shared promise).
let restoring: Promise<User | null> | null = null;

export function restoreSession(): Promise<User | null> {
  if (!restoring) {
    restoring = userManager
      .signinSilent()
      .then((user) => {
        track(user);
        return user;
      })
      .catch(() => null);
  }
  return restoring;
}

export interface LoginState {
  next?: string;
}

// StrictMode runs the login pages' effect twice: a single redirect per page load.
let redirecting: Promise<void> | null = null;

export function login(options: { next?: string; loginHint?: string } = {}): Promise<void> {
  if (!redirecting) {
    const state: LoginState = { next: options.next };
    redirecting = userManager.signinRedirect({ state, login_hint: options.loginHint }).catch((err) => {
      redirecting = null;
      throw err;
    });
  }
  return redirecting;
}

// The authorization code is single-use: StrictMode would exchange it twice.
let completing: Promise<User> | null = null;

export function completeLogin(): Promise<User> {
  if (!completing) {
    completing = userManager.signinRedirectCallback().then((user) => {
      track(user);
      return user;
    });
  }
  return completing;
}

export async function logout(): Promise<void> {
  const idToken = currentUser?.id_token;
  track(null);
  await userManager.removeUser();
  // end_session do Keycloak: encerra a sessão SSO e volta para a home.
  await userManager.signoutRedirect({ id_token_hint: idToken });
}
