# EquipLink Web Access

This folder adds a responsive browser interface to the existing EquipLink Java desktop system.
It is **one system, one PostgreSQL database, one set of business rules**:

```
Desktop UI ─┐
            ├─► src/controller/* ─► src/service/* ─► PostgreSQL
Browser UI ─┘        ▲                    ▲
              web-app/backend       same compiled classes
```

No desktop source file was changed. The web backend compiles the existing `src/` together with
new `web-app/backend/src/` classes and calls the **same controllers and services** used by the
desktop app. No Maven or new JAR is required; Java 17 includes the HTTP server used here.

Chat is intentionally not included: the desktop application has no chat feature, so the web
interface mirrors the real system and uses in-app notifications instead.

---

## 1. Requirements

- Java 17 JDK (`javac` and `java` on `PATH`)
- PostgreSQL running with the existing EquipLink database
- Existing JDBC settings from `%USERPROFILE%\.equiplink\database.properties` (the web server
  reuses `src.database.DatabaseConfig`; credentials are never copied into this folder)

## 2. Build and start

From the EquipLink root folder:

```bat
web-app\scripts\start-web.bat
```

The script compiles automatically on first run (or after backend changes) and starts the server
on port **8080**, bound to `0.0.0.0` so LAN devices can reach it. The console prints both the
local URL and detected Wi-Fi IPv4 addresses, for example:

```
Local:   http://localhost:8080
Network: http://192.168.192.143:8080
```

To rebuild manually after editing backend Java:

```bat
web-app\backend\build-web.bat
```

## 3. Open from a phone or tablet

1. Connect the phone/tablet to the **same Wi-Fi** as this computer.
2. Find this computer's LAN IPv4 address:
   ```bat
   ipconfig
   ```
   Look for `IPv4 Address` under the active Wi-Fi adapter (for example `192.168.192.143`).
3. In the mobile browser open:
   ```
   http://192.168.192.143:8080
   ```
4. Sign in with an existing EquipLink account. Roles and permissions are identical to desktop.

If Windows Firewall blocks the phone, run this **as Administrator** once:

```bat
web-app\scripts\allow-firewall.bat
```

It opens inbound TCP port 8080 only. PostgreSQL is never opened to the network; browsers talk
only to the Java web server.

## 4. Stop the server

```bat
web-app\scripts\stop-web.bat
```

You can also close the `EquipLink Web Server` console window.

## 5. Configuration

Edit `web-app\config\web-app.properties`:

| Property | Default | Meaning |
|---|---:|---|
| `port` | `8080` | HTTP port |
| `bind` | `0.0.0.0` | Listen address; keep for LAN access |
| `sessionMinutes` | `480` | Idle session lifetime |
| `maxBodyBytes` | `2097152` | Max JSON request body |

Restart after changing it.

## 6. What is shared with desktop

- Login, registration, password reset review and profile updates (`AuthController`)
- Requests, assignment, approval, release, return, receipts, deletes/restores
  (`BorrowRequestController`)
- Inventory and all derived availability counters (`EquipmentController`)
- Notifications, overdue sweeps and reminders (`NotificationController`)
- `RequestStatus` display/status logic and all model DTOs
- `room.md` venue list (read/write from the same file used by desktop)
- Database connection and Philippine-time handling (`Database.getConnection()`)
- Maroon palette, Poppins typography, light/dark themes and existing logo artwork

Server-side sessions use an HttpOnly, SameSite cookie. Every API request re-loads the account
from PostgreSQL and re-checks its role; hidden buttons are never the only permission check.
Cross-origin mutation requests are rejected.

## 7. Implemented screens

Login / registration / password reset; role-aware Dashboard; Available Equipment; Request
Equipment (multi-date, multi-item, urgency); My Requests; Borrowing Request history; staff
Requests queue with assign / decline / needs-action / release / return inspection; Schedule &
Assignment with room conflicts; Transaction History; Facility Admin review and All Requests;
Campus Director approval; User Management and account details; Password Requests (ICT);
Notifications; Settings (profile, picture, sound, theme) and ICT venue management.

## 8. Verification status (this machine)

Tested:
- Java compilation of existing desktop sources plus web sources.
- Server start, static pages, health endpoint and LAN bind.
- Login and authenticated read APIs for temporary ICT_STAFF and BORROWER test accounts.
- Create request from browser API → visible in staff queue → assignment saved → status advanced
  to `PENDING_ADMIN`; admin deletion and role-gate rejection (403) also verified.
- Temporary test accounts and their test request were removed afterward; no seeded account
  password was changed.

Not end-to-end exercised with a real seeded login here:
- The four seeded account passwords no longer match the seeder values on this machine, so a
  human login with those exact seeded credentials was not used. Use your existing desktop
  password in the browser.
- Full Admin/Director approval buttons, printable form download in a browser tab, return
  inspection with damaged/missing quantities, profile picture upload and password reset review
  were compiled and wired but not clicked through in a real browser session.
- Actual phone/tablet access and Windows Firewall rule were not exercised; run the commands in
  sections 3–4 on the target network.

## 9. Backup

A timestamped copy of the pre-web working tree was created at `backup/pre-web-20261123-1346/`
before any web file was added. No `git checkout`, `clean`, schema migration or seed change was
performed.

## 10. Desktop 1.4.0 alignment pass (2026-11-23)

A second backup was taken at `backup/pre-v1.4-web-fixes-20261123-1444/` before this pass.

Bugs found and fixed:
- `can't find variable: table` — `pages/notifications.js` used `table()` without importing it.
  The notification page now imports it and renders request details from real database fields.
- `GET /api/notifications` lost every notification field because `Json.object(bean)` only accepts
  `Map` values. `NotificationHandler` now projects the bean explicitly and adds borrower,
  equipment, quantity, time and current-status details through the existing request controller.
- Emoji/unicode navigation and action glyphs were replaced with new `js/icons.js` SVG paths
  matching desktop `LucideIcon`/`EyeIcon`.
- First visit always starts in Light mode; only an explicit toggle saves Dark mode.
- Duplicate router listeners, toast timer leaks and modal backdrop listener leaks were removed.
- Notification sound now uses the four desktop sound profiles and frequencies, honours both
  notification/sound preferences, starts only after a user gesture, and tracks the latest
  notification ID per user so old notices do not replay on login, refresh, panel open or polling.
- All displayed times use 12-hour AM/PM; forms still submit 24-hour `HH:mm` to the database.
- Password fields use show/hide eye controls; no emoji glyphs remain in navigation/actions.
- Static files now send `Cache-Control: no-cache`, and `start-web.bat` always recompiles so LAN
  devices cannot keep stale CSS/JavaScript.
- Desktop sound was audited: only `MainLayoutPart2` actively plays it; `BackgroundNotificationService`
  is not instantiated in 1.4.0. The active path already keeps a separate baseline and the
  2.5s throttle, so no desktop playback code was changed.

Desktop 1.4.0 alignment:
- Palette tokens, dark background/header `#1A0808`, sidebar `#660000`, Poppins typography,
  login brand copy, 252px sidebar, 90px header, 20px stat cards, status badge colours,
  Lucide-style SVGs and the 260/320/420ms motion timings now mirror `src/ui/AppTheme.java`
  and the desktop components. No new colours, sounds, assets or Chatbox were introduced.

Verified in headless Edge during this pass:
- Fresh profile: first visit is Light; explicit toggle switches to Dark and persists on restart.
- Login/logout, browser console (no uncaught exceptions), SVG nav/action/password icons,
  password show/hide, settings sound names `Chime/Bell/Soft/Alert`, and sound preview button.
- Real notification generated while signed in: latest ID advanced, Web Audio started exactly
  two oscillators (Chime), and no extra sound occurred on notifications panel open, details
  modal open or the next poll.
- Notification table/modal showed real borrower, request code, equipment, requested quantity,
  event date/time and status from the database; no `table` runtime error.
- 12:00 AM → `00:00`, 12:00 PM → `12:00`, 1:00 PM → `13:00`, 11:59 PM → `23:59`; display
  helpers rendered the matching AM/PM strings.
- 390x844 and 768x1024 viewports: mobile drawer opens/closes, tables become cards at 390px,
  modals fit, and no page-level horizontal overflow.

Still not exercised in this pass:
- Human login with the four seeded accounts (their passwords differ from `DatabaseSeeder.java`
  on this machine), so staff/admin/director button-by-button workflows and profile picture upload
  were not clicked through again; the pure-Java handlers and role gates remain unchanged.
- Windows Firewall and a real phone/tablet on the LAN.
- Physical audio output on speakers; the Web Audio node starts were measured in-browser instead.

## 11. Source-alignment audit (src.zip + web-app.zip)

This web package was checked against the supplied desktop `src/` source.

### Visual alignment
- Uses the desktop `AppTheme` brand palette: Blood Red `#660000`, hover `#8C1A1A`,
  pressed `#4D0000`, Poppins, white light surfaces, and the approved dark theme
  (`#1A0808` page/header, `#240D0D` cards, `#301212` inputs).
- Keeps the desktop's maroon sidebar and matching status tones.
- Translates desktop motion into browser CSS: 260ms page fade/slide, 320ms pop-in,
  420ms card entrance, hover/press transitions, and the 650ms shine effect.
- Adds a reduced-motion accessibility rule.
- The source ZIP did not contain the full logo PNG referenced by the first web package.
  The web package now uses `equiplink-mark.png`, extracted from the supplied desktop
  `logo_sheet.png` resting frame, so the login and sidebar logo paths resolve.

### Workflow alignment
- Role navigation was compared with `src/layout/MainLayoutPart1.java`.
  Facility Admin does not receive a Transaction History navigation item because it is
  not part of that role's desktop navigation.
- Browser status text now follows `src/ui/UiSupport.displayStatus()` for special
  states such as Borrowed, Staff review, Awaiting Facility Admin Approval, and Declined.
- Existing web handlers continue delegating to the supplied desktop controllers/services;
  do not replace them with browser-only calculations.

### Source limitation
The supplied desktop `src.zip` does not contain a Chatbox screen, Chatbox controller,
Chatbox service, or chat message model/table integration. A real Chatbox cannot be
faithfully exposed from these supplied sources without a separately approved feature
implementation. No fake chat interface or simulated messages have been added.

### Build output
Compiled `.class` files are generated by `backend/build-web.bat`; they are not source
files and are intentionally excluded from this ZIP to avoid shipping stale binaries.

