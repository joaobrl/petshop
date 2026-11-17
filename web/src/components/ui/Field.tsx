import { useId } from "react";
import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes } from "react";

interface FieldWrapperProps {
  id: string;
  label: string;
  hint?: string;
  error?: string;
  children: ReactNode;
}

function FieldWrapper({ id, label, hint, error, children }: FieldWrapperProps) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {children}
      {error ? (
        <span className="error" id={`${id}-note`}>
          {error}
        </span>
      ) : hint ? (
        <span className="hint" id={`${id}-note`}>
          {hint}
        </span>
      ) : null}
    </div>
  );
}

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  hint?: string;
  error?: string;
}

export function TextField({ label, hint, error, id, ...rest }: TextFieldProps) {
  const autoId = useId();
  const fieldId = id ?? autoId;
  return (
    <FieldWrapper id={fieldId} label={label} hint={hint} error={error}>
      <input
        id={fieldId}
        aria-describedby={hint || error ? `${fieldId}-note` : undefined}
        aria-invalid={error ? true : undefined}
        {...rest}
      />
    </FieldWrapper>
  );
}

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  hint?: string;
  error?: string;
  children: ReactNode;
}

export function SelectField({ label, hint, error, id, children, ...rest }: SelectFieldProps) {
  const autoId = useId();
  const fieldId = id ?? autoId;
  return (
    <FieldWrapper id={fieldId} label={label} hint={hint} error={error}>
      <select id={fieldId} aria-describedby={hint || error ? `${fieldId}-note` : undefined} {...rest}>
        {children}
      </select>
    </FieldWrapper>
  );
}
