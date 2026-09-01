# Docs and operational notes for gateway

This FastAPI gateway is intended to be the canonical ingestion point for events created by the EA. It validates request signatures, deduplicates events using Redis, and can optionally persist events into Firebase for consumption by Portal.

Operational notes
- Configure GATEWAY_SECRET and ADMIN_TOKEN as strong values.
- Ensure REDIS_URL is accessible and persistent.
- If you want events written to Firebase, set FIREBASE_BASE and FIREBASE_AUTH.
- Make sure the EA WebRequest has the gateway endpoint whitelisted in MT5 options.

Security
- Use HTTPS in production.
- Rotate GATEWAY_SECRET periodically.
- Use service accounts when integrating with Firebase.
