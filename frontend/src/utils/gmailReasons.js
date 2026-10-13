// Reason codes come from GmailOAuthService.failure(...) via /dashboard?gmail=error&reason=<code>
export const GMAIL_REASONS = {
  access_denied: 'Google sign-in was cancelled or refused.',
  invalid_request: 'Google sent back an incomplete response. Please try connecting again.',
  invalid_state: 'The connection link expired or was tampered with. Please try connecting again.',
  no_refresh_token:
    'Google did not grant lasting access. Remove SubShift under your Google Account > Security > Third-party access, then connect again.',
  scope_missing: 'Gmail read access was not granted. Please tick the Gmail permission on the Google consent screen.',
  token_exchange_failed: 'Could not complete the sign-in with Google. Please try again.',
  server_error: 'The server hit an error while saving the connection. Please try again.',
};

export const gmailReasonText = (code) => GMAIL_REASONS[code] || 'Gmail connection failed. Please try again.';
