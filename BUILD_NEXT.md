# Next build: Crew, Chat, and Skills

Read this whole file before writing any code. It's the handoff from a
planning session in the `gaca` (API) repo — you're starting fresh with none
of that conversation's context, so this is everything you need.

> **Update, 2026-09-20, later the same day: the backend is done, merged, and
> deployed for real.** Everything below is now the settled, live shape — not
> a plan. `main` in `gaca` has it (PR #11), and it's running in production
> on the `gaca-api` box (`https://goodandcheapagent.com/api`), verified
> healthy. If you started Slice 1 before this update using the earlier
> `/crew/host` shape, re-read §2 below — that endpoint is gone, replaced by
> `/agent` as its own resource. `gaca/frontend/src/components/profile/CrewSection.tsx`
> is a working reference for the two-phase (connect, then manage-crew) UI
> split, if useful — different framework, same idea, don't port it literally.
>
> **Update, 2026-09-20, still later the same day**: the backend also grew an
> *optional* capability where gaca-api can create a new crew member on the
> Hermes box directly, instead of you setting one up by hand and typing in
> its port/key. This went through two shapes before landing — an early draft
> put it behind its own `/agent/provisioning` resource (status/set/test
> endpoints); that was removed the same day after the user pointed out the
> app shouldn't need to know it exists at all. **Final shape: it's just an
> optional `access` field on `POST /crew` itself** (and on
> `POST /crew/discover`, which finds crew members that already exist on the
> box but were never registered through gaca-api). Give `access` once,
> gaca-api remembers it for every call after. `CrewOut` (what `GET /crew`
> and every mutating crew call returns) gained `can_create: bool` so a
> client can tell whether its "add crew member" form needs to ask for
> `access` or can just ask for a name. If you build UI for this, it's one
> form on one existing screen (Settings' crew list) — not a separate setup
> flow. See `api-contract.md` §2's "Having gaca-api create a crew member for
> you" for the exact shape.

## 1. Orientation — read these first, in order

1. **The design** — a clickable canvas of all nine target screens, in three
   flows (alert → chat, cold-start chat, crew/skills/settings):
   `https://claude.ai/artifact/XWMrj74b37U9egu92KY89V`
   Open it with your Artifact tool (`action: "read"`, that `url`). It's the
   user's own private artifact — you can open it as its owner.
2. **The API contract** — the source of truth for every endpoint, request/
   response shape, and status code you'll build against:
   `/home/david/Projects/gaca/gaca-planning/alerts/api-contract.md`
   (absolute path — read it directly even though it lives in the other repo).
   If this doc and `<base>/api/openapi.json` ever disagree, the live schema
   wins.
3. **This app's existing conventions** — skim before adding a new
   screen/repo/DTO so you match the house style instead of inventing a new
   one:
   - `app/src/main/java/dev/monts/gaca/data/remote/GacaApi.kt` — the Retrofit
     interface, one method per endpoint.
   - `app/src/main/java/dev/monts/gaca/data/remote/Dtos.kt` — kotlinx.serialization
     DTOs, `@SerialName` for snake_case → camelCase.
   - `app/src/main/java/dev/monts/gaca/data/repo/` — repositories wrap the API
     with domain types (see `AlertsRepository.kt` / `Alert.kt` for the pattern:
     a `Dto` at the network edge, a plain domain class everywhere else).
   - `app/src/main/java/dev/monts/gaca/ui/` — one package per screen
     (`inbox/`, `detail/`, `settings/`, …), each with a `Screen.kt` (Compose)
     and a `ViewModel.kt` (`StateFlow<UiState>`), wired into
     `ui/nav/GacaNavHost.kt`.
   - `app/src/main/java/dev/monts/gaca/ui/theme/Color.kt` — every color as a
     named constant. Add crew colors here too rather than inlining hex
     strings in a screen.

## 2. What already changed on the backend (this is done, not yours to build)

The `gaca-api` split its whole multi-profile concept into two resources —
**agent** (the one Hermes box you connect to) and **crew** (named profiles
under it) — fixed a real bug (chat was completely broken — a leftover field
from before this work started), and added two new capabilities. All of this
is merged to `main` (`uv run pytest` in `gaca-api/`, 214 passed) and
deployed to production. Full detail is in the contract doc's §2 (agent/crew)
— the short version:

- **Agent — connect once.** `GET /api/agent` → `{connected, host}`.
  `POST /api/agent/connect` → body `{host, port, api_key, name?}`, `422` if
  already connected. This is the *only* place a first crew member gets
  created — it makes one named `name` (default `"default"`) from the given
  `port`/`api_key` so there's already someone to chat with. `PUT /api/agent`
  → body `{host}`, moves an already-connected agent to a new box.
- **Crew — profiles under that agent, added afterward.** `GET/POST /api/crew`
  (POST adds *another* profile, never the first one), `PUT`/
  `DELETE /api/crew/{name}`, `POST /api/crew/{name}/default`. A `CrewMember`
  carries a **`color`** field (hex, auto-assigned from a fixed palette) —
  this is new, and it's the answer to "how do we make it obvious which
  profile you're talking to." Use it everywhere a crew member shows up:
  avatar, chat header accent, that crew member's own message bubbles, filter
  chips, a settings-row border.
- **Chat actually works now** — `GET/POST /api/sessions`,
  `GET /api/sessions/{id}`, `POST /api/sessions/{id}/messages`,
  `GET /api/sessions/{id}/events` (SSE). A session is bound to one crew
  member for its whole life (`Session.crew`), and carries the bidirectional
  alert↔chat link: `Session.alert_id` is set when a session was opened by
  delegating an alert (`Alert.session_id` is the existing forward pointer).
- **Skills** — `GET /api/crew/{name}/skills` — read-only, live from that
  crew member's own Hermes, never cached. `{name, description, category}`
  per skill.

**Not your problem, but expect it**: the port number and the fact that a
crew member is technically a separate Hermes process is Hermes plumbing, not
a concept the API wants surfaced as something to explain in the UI — treat
`port` as an opaque field the create/edit forms need, not something worth a
tooltip.

**Known follow-up, not done yet on the Android side**, already done on the
web: `gaca/frontend/`'s `CrewSection.tsx` now implements the agent-connect
vs. crew-manage split described above — worth a look before you build the
equivalent Settings flow here.

**Real data exists in production now, for what it's worth while testing**:
the account this app signs into already has a real, already-connected
agent with three real crew members (`default`, `toolshed`, `trading`) —
that data predates all of today's work and survived the migration
untouched. You shouldn't need to connect a fresh agent by hand to see a
populated crew list against the real backend.

## 3. How to work — read this even if it seems obvious

This project is built slowly and on purpose, one screen at a time, not as a
big-bang dump. Concretely:

- **Build one slice below, run it in the emulator, confirm it actually
  works, then stop and check in** before starting the next slice. Use the
  `run` skill (or whatever this session offers) to actually launch the app
  and click through the thing you just built — don't mark a slice done from
  reading the code alone.
- **Explain the Kotlin/Compose/Android ideas as you go**, not just the
  what — e.g. why a `StateFlow` here and not a plain `var`, why this needs a
  `NavType`, what a `SavedStateHandle` buys you. Treat this as a teaching
  collaboration, not a ticket to close.
- **Small, reviewable diffs.** A slice that touches ten files because it
  "also" refactored something unrelated is a sign to split it up.
- **Ask before a real architectural fork** (e.g. how to do SSE on Android —
  there's no single obvious answer) rather than silently picking one and
  running with it.

## 4. The slices, in order

Each references the matching screen name from the design canvas.

### Slice 1 — Rename Agent → Crew, and add the Agent-connect step

Two parts — do the rename first if you haven't (some of this may already be
done, e.g. `InboxViewModel`/`AuthRepository.getCrew()`), then add the
missing connect step.

**Rename**: `GacaApi.kt`'s `/auth/agents*` calls → `/crew*` (no more
`/auth/agent-host` — see below); `AgentDto`/`AgentsDto` →
`CrewMemberDto`/`CrewDto` (+ new `color: String` field); the repo/domain
`AgentProfile` (in `data/repo/`) → `CrewMember`; the `AGENT` picker chips in
`AlertDetailScreen.kt` and `NewDelegationScreen.kt` → relabel and start
rendering each chip in that crew member's own `color`. `AlertDto.agent` →
`AlertDto.crew` (and the `?agent=` query param → `?crew=` in `listAlerts`).

**Add the connect step** (this is the new part): `GacaApi.kt` needs
`GET /agent` (→ `AgentStatusDto { connected: Boolean, host: String? }`) and
`POST /agent/connect` (body `{host, port, api_key, name}` → returns the same
`CrewDto` shape `GET /crew` does) and `PUT /agent` (body `{host}`, same
return). Restructure `SettingsScreen.kt`'s crew section into two states: no
agent connected yet → a "Connect your agent" form (host/port/key, calls
`POST /agent/connect`); connected → "YOUR CREW" (the existing list) plus an
"Add crew member" form that calls `POST /crew` (unchanged shape, no host
field on it anymore) and a small "change host" affordance that calls
`PUT /agent`.

Note: the design canvas's "9. Add crew member (sheet)" predates this split
and only shows the *add another one* form — there's no separate mock for the
first-time "connect your agent" screen. Reuse the same visual language (same
fields plus host, same sheet style) rather than inventing a new look; ask
the user if it's worth a proper mock before building it blind.

### Slice 2 — Color shows up everywhere

Now that `CrewMemberDto.color` exists: render it as the avatar background
and chip tint on the Inbox alert rows, the crew filter chips, and the
Settings crew list (left border, per the design). This is entirely visual —
no new endpoints.

### Slice 3 — Chat data layer

Add to `GacaApi.kt`: `GET /sessions` (list), `POST /sessions`,
`POST /sessions/{id}/messages`, and the SSE stream
`GET /sessions/{id}/events`. Retrofit doesn't do SSE natively — decide with
the user how to consume it (a raw OkHttp call reading the response body
line-by-line is the simplest option; there are small SSE client libraries
too). Add `SessionDto`/`MessageDto` fields for `crew` and `alertId`. Add a
`SessionsRepository` following the existing repo pattern.

### Slice 4 — Chat tab: list + new-chat sheet

Build **"4. Chat tab"** and **"5. Choose crew (sheet)"** from the design.
Add a bottom navigation bar (Inbox / Chat / Skills — new for this app;
Settings stays a top-corner icon, it's not a daily destination) and wire it
into `GacaNavHost.kt`. The crew-picker sheet lists `GET /crew`'s crew members
with their color + a one-line description; tapping one calls
`POST /sessions` and navigates into the thread.

### Slice 5 — Chat thread

Build **"6. Chat, cold start"** and **"3. Chat, from the alert"**. Reuse the
conversation-bubble composable already in `AlertDetailScreen.kt` (pull it out
into a shared composable rather than duplicating it) — tint the crew
member's own bubbles with their `color` instead of the current fixed
`SuggestBackground`. Live-update the thread as `assistant.delta` /
`tool.started` / `run.completed` events arrive over SSE.

### Slice 6 — Bidirectional alert↔chat link

In `AlertDetailScreen.kt`: replace the current single "Ask Hermes something
else" free-text box with **both** paths from the design — the quick-ask box
stays for a single instruction, and a new prominent "Open full chat with
{crew}" button (tinted in that crew member's color) navigates into the real
thread (delegating first via `POST /alerts/{id}/delegate` if it hasn't been
delegated yet, since that's what creates the session). In the chat thread,
when `Session.alertId` is non-null, show the "from alert" pill from the
design that navigates back to `AlertDetailScreen`.

### Slice 7 — Skills tab

Build **"7. Skills tab"**. A crew-selector segmented row at the top (colored
per crew member), `GET /crew/{name}/skills` for the list, grouped by
`category` into sections like the design.

### Slice 8 — Add-crew-member color picker

Build **"9. Add crew member (sheet)"**'s color-swatch row: `POST /crew`
already auto-assigns a color server-side if none is given, so the simplest
version just shows the auto-assigned result after creation — the picker
itself (letting the user override before saving) is a nice-to-have, confirm
with the user whether it's worth doing now or later.

## 5. Getting started

Tell the user which slice you're starting once you've read this file and the
two links in §1, then build slice 1 and stop for a check-in before moving on.
