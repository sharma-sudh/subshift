/**
 * Label + input + inline error, wired for accessibility.
 * Usage: <Field id="email" label="Email" error={errors.email}>{(p) => <input {...p} type="email" />}</Field>
 * The render-prop receives { id, 'aria-invalid', 'aria-describedby' } to spread onto the control.
 */
export default function Field({ id, label, error, hint, children }) {
  const describedBy = [error ? `${id}-error` : null, hint ? `${id}-hint` : null].filter(Boolean).join(' ') || undefined;
  return (
    <div className={`field${error ? ' field-invalid' : ''}`}>
      <label htmlFor={id}>{label}</label>
      {children({ id, 'aria-invalid': error ? 'true' : undefined, 'aria-describedby': describedBy })}
      {hint && !error && (
        <p className="field-hint" id={`${id}-hint`}>
          {hint}
        </p>
      )}
      {error && (
        <p className="field-error" id={`${id}-error`} role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
