import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError } from "../services/http";

const GENERIC_ERROR = "Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.";

// ApiError already carries a message fit for the user; anything else (network
// failure, TypeError, bug) gets a generic one. 401s are handled globally by
// apiFetch (session expired), so screens don't special-case them.
export function messageOf(e: unknown): string {
  return e instanceof ApiError ? e.message : GENERIC_ERROR;
}

interface ApiState<T> {
  data: T | undefined;
  error: string | null;
  loading: boolean;
}

// Loads data with explicit loading / error / success states. Re-runs when
// `deps` change or on `retry()`. A result from a stale call (older deps/retry)
// or from after unmount is ignored. Pass `enabled = false` to skip the call.
export function useApi<T>(fn: () => Promise<T>, deps: unknown[], enabled = true) {
  const fnRef = useRef(fn);
  const callId = useRef(0);
  const [state, setState] = useState<ApiState<T>>({ data: undefined, error: null, loading: enabled });

  useEffect(() => {
    fnRef.current = fn;
  });

  const run = useCallback(() => {
    const id = ++callId.current;
    setState((s) => ({ data: s.data, error: null, loading: true }));
    fnRef.current().then(
      (data) => {
        if (id === callId.current) setState({ data, error: null, loading: false });
      },
      (e: unknown) => {
        if (id === callId.current) setState((s) => ({ data: s.data, error: messageOf(e), loading: false }));
      },
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    if (!enabled) return;
    run();
    return () => {
      // eslint-disable-next-line react-hooks/exhaustive-deps
      callId.current++; // invalidate in-flight call on unmount / deps change
    };
  }, [run, enabled]);

  return { data: state.data, error: state.error, loading: state.loading && enabled, retry: run };
}
