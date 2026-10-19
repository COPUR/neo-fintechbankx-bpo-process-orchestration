# bpo-staff-web

The presentation layer for staff user tasks: plain ES modules, no build step, no runtime dependencies. One
screen set per human actor over `bpo-process-api`, Turkish and English, WCAG 2.2 AA. Design:
`docs/ux/staff-task-ux.md`.

| File | Holds |
|---|---|
| `index.html`, `styles.css` | page shell, landmarks, live regions, theme |
| `src/app.js` | sign-in, navigation between actors, list and detail |
| `src/views.js`, `src/dom.js` | screens; DOM from elements and text nodes only |
| `src/view-model.js` | filters, SLA state, decision options, case link, messages (pure, tested) |
| `src/api.js` | the API client: DPoP on every call, decision-only body |
| `src/dpop.js`, `src/auth.js`, `src/key-store.js` | DPoP proofs, PKCE sign-in, the non-extractable key |
| `src/i18n.js` | Turkish and English labels |

Run the tests with `npm test` (Node 22, no install). Copy `config.example.json` to `config.json` and fill the
realm, API origin and the case view's URL template; the deployment serves both files with a Content Security
Policy that names those origins.
