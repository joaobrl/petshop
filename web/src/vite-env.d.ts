/// <reference types="vite/client" />

interface ImportMetaEnv {
  // URL base do gateway-service (ex.: http://localhost:8080). Opcional.
  readonly VITE_API_BASE_URL?: string;
  // Keycloak (SDD-024 F3). Opcionais: padrão http://localhost:8180, petshop, web.
  readonly VITE_KEYCLOAK_URL?: string;
  readonly VITE_KEYCLOAK_REALM?: string;
  readonly VITE_KEYCLOAK_CLIENT_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
