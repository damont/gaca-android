# Part 12 (gacabits in the app): what the app needs from gaca-api

Slice 3 (share sheet) notes: the app uses a 5-minute read timeout for `move-to-cloud`, and if the connection drops it polls
`GET /artifacts/{id}` every 3 s (up to 2 minutes) and reads `moving` / `home` / `cloud_status`. Item 3 shows up as: the person sees the
share checks only after the move (they confirm the move first, then the checks, then the link).

Slice 4 (Trust and add) notes: the preview shows title, owner, role and what the role allows (worked out from the role). Check
results are NOT shown, because the cloud's preview carries none (item 2). The app takes a pasted link, or `gaca://join/<token>`; it does
not register an `https://<site>/join/...` App Link, because that needs a host in the manifest and the website's `assetlinks.json`
(item 9). The preview has no page id, so "already a member" re-runs the (idempotent) join to find the id before opening.

Status after gaca-api PR #66 and #67: items 1, 4 and 5 are DONE and used by the app; 2, 3, 6 are open on the server side; 7 is
answered (no 402 at free launch; the app shows the server's message if it ever comes); 8 and 9 are open (see each).

Written against gaca-api `origin/main` as of Part 8b (not against the plan). The app does not work around any of
this; each item is a request. "Blocks" says which app slice is held up. Nothing here changes the hard rules: the app
talks only to its own server, and the server forwards.

## Needed for the badges and screens

1. **DONE (#67)** **`published_template_id` on artifact list items** (blocks slice 2's "published" badge, slice 5).
   `ArtifactListItem` has `home`, `role`, `shared_by`, `member_count`, `moving`, `cloud_status`, but nothing says a
   cloud page has been published. The cloud's `Gacabit` already has `published_template_id`; pass it through
   (and, if cheap, the template's `status`: `checking | listed | blocked | unlisted`).
2. **Invite preview needs check results** (blocks slice 4). `POST /cloud/invites/preview` is a pass-through of the
   cloud, which answers only `{gacabit_title, role, invited_by, already_member}`. The "Trust and add" screen should
   show the check results (a `status` plus findings with severity). Ask the cloud to add them to the preview, or have
   the server add them. "What it can do" the app derives from `role` itself (fixed by the permissions matrix).
3. **Share checks for a private page** (blocks slice 3's confirmation wording). `POST /cloud/gacabits/{id}/validations`
   only works for a page already in the cloud, but sharing a private page means moving it first. So the person can
   only see the share checks after the move. Either add the scan to `GET /artifacts/{id}/move-preview`, or add a
   check route for local pages.

## Would make the back and forth better

4. **DONE (#67)** **`GET /cloud/link` should say whether the cloud is set up.** Today the app only learns `GACA_CLOUD_URL` is empty
   when `link/start` answers `409 cloud_not_configured`. Add `configured: bool` so the app can hide or explain the
   card up front.
5. **DONE (#67)** **`GET /cloud/link` while pending should return the attempt** (`user_code`, `verification_uri`, `expires_at`).
   If the person leaves Settings mid-link, the app only knows "pending" and has to start over with a new code.
6. **Left as is by the server: poll `GET /artifacts/{id}` (`moving`, `cloud_status`) if the connection drops.** Still open: does the server finish a move when the phone disconnects mid-request? **Move to cloud is one long blocking request** (`POST /artifacts/{id}/move-to-cloud`, "blocks until done").
   Phone connections drop and OkHttp's default read timeout is 10 s. The app will use a long timeout and retry
   (the route says it is safe to retry), but a `202` plus polling (or documenting the worst-case time) would be
   sturdier.
7. **Billing at launch** (blocks slice 3 and 5 wording). The cloud contract answers `402 billing_required` for
   create/move when there is no payment method. For a free launch, confirm the cloud will not return it. If it can
   still appear, the app will show the server's message and nothing billing-specific.
8. **Template shape for the Gallery — checked on a phone, works (2026-11-15; the list was empty-ish, 35-byte body, so field names for real templates are still unseen)** (slice 2: coded against `Template` in the cloud's `openapi.yaml`; unchecked against a live answer). `GET /cloud/templates` is a raw pass-through. The app will code
   against `Template` / `TemplateList` in the cloud's `openapi.yaml`; please confirm that is what arrives, including
   the author's handle and a stable id for `install`.
9. **Invite link host.** `POST .../invites` returns the cloud's `url` as is. The app copies it and never builds one.
   Opening a `https://<site>/join/<token>` link in the app needs Android App Links: an `assetlinks.json` served by
   the website (not gaca-api). Until then the app takes a pasted link.

## Checked and fine (no change)

* Errors: every non-2xx has a string `detail`, plus `code`, `message` and structured `info` (also for answers passed
  through from the cloud). The app reads `code` and `info`.
* Link: `start`, `poll` (honours `interval`/`retry_in`), `unlink` with `force`, and the `409 cloud_artifacts_present`
  `info` counts are enough for slice 1.
