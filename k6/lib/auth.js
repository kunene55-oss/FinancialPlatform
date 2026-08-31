import http from 'k6/http';
import encoding from 'k6/encoding';
import { check } from 'k6';
import { config } from './config.js';

// Module-level cache: k6 gives each VU its own JS runtime, so this object
// persists across iterations *within* a VU (not shared across VUs) -
// exactly what we want, since it means every VU authenticates once and
// reuses/refreshes its own token instead of hammering Keycloak's token
// endpoint on every single request.
const tokenCache = {};

function decodeExpiry(jwt) {
  try {
    const payload = JSON.parse(encoding.b64decode(jwt.split('.')[1], 'rawurl', 's'));
    return payload.exp || 0;
  } catch (e) {
    return 0;
  }
}

/**
 * Returns a cached token for (clientId, clientSecret) if it still has more
 * than 30s left before expiry, otherwise fetches a fresh one. Fails the
 * check (and the calling script's threshold) rather than throwing, so a
 * single auth hiccup shows up as a failed request instead of aborting the
 * whole VU.
 */
export function getToken(clientId, clientSecret) {
  const cached = tokenCache[clientId];
  const now = Date.now() / 1000;
  if (cached && cached.exp - now > 30) {
    return cached.token;
  }

  const res = http.post(
    `${config.KEYCLOAK_URL}/realms/${config.REALM}/protocol/openid-connect/token`,
    {
      grant_type: 'client_credentials',
      client_id: clientId,
      client_secret: clientSecret,
    },
    { tags: { name: 'KeycloakToken' } }
  );

  const ok = check(res, {
    'token request succeeded': (r) => r.status === 200,
  });
  if (!ok) {
    return null;
  }

  const token = res.json('access_token');
  tokenCache[clientId] = { token, exp: decodeExpiry(token) };
  return token;
}

export function authHeader(token) {
  return { Authorization: `Bearer ${token}` };
}
