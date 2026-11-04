/// <reference types="vite/client" />

interface ImportMetaEnv {
  // URL base do gateway-service (ex.: http://localhost:8080). Opcional.
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
