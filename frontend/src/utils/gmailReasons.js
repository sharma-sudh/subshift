// Reason codes come from GmailOAuthService / OutlookOAuthService via /dashboard?gmail=error&reason=<code>
// or /dashboard?mail=error&reason=<code>
export const MAIL_REASONS = {
  access_denied: 'Sign-in was cancelled or refused.',
  invalid_request: 'The mail provider sent back an incomplete response. Please try connecting again.',
  invalid_state: 'The connection link expired or was tampered with. Please try connecting again.',
  no_refresh_token:
    'The provider did not grant lasting access. Remove SubShift from your account\'s connected apps, then connect again.',
  scope_missing: 'Mail read access was not granted. Please tick the mail permission on the consent screen.',
  token_exchange_failed: 'Could not complete the sign-in. Please try again.',
  server_error: 'The server hit an error while saving the connection. Please try again.',
};

export const mailReasonText = (code) => MAIL_REASONS[code] || 'The email connection failed. Please try again.';

// Kept for older imports.
export const GMAIL_REASONS = MAIL_REASONS;
export const gmailReasonText = mailReasonText;
