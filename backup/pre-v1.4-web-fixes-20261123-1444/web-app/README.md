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
