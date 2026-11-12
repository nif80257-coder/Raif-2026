# Security policy

- Only HTTPS addon and provider endpoints are accepted by the client helpers, except loopback HTTP for local development.
- Stremio addons are treated as remote HTTP data providers; their responses are parsed as data and never evaluated as code.
- Nuvio providers are executable JavaScript. They run only after the user enables that provider, inside a JavaScriptCore context with no filesystem, keychain or native API access, HTTPS-only `fetch`, bounded timers, a 25 second timeout and a 4 MB response cap. Modules such as cheerio or crypto-js are rejected, not loaded.
- Provider output is parsed as data, and a failing or timed-out provider never affects other sources.
- Do not commit credentials, API keys, private addon URLs or user data. A TMDB key entered in the app is stored in device settings only.
- Use only sources and media you are authorized to access.
