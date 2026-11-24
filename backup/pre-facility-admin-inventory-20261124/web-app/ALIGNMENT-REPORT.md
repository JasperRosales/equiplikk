# EquipLink Web Package — Alignment Report

Compared inputs:
- `src.zip`: supplied Java desktop source
- `web-app.zip`: supplied web application

## Changes made
1. Replaced five broken references to a logo PNG absent from the supplied archives with `frontend/assets/equiplink-mark.png`, generated from the final/resting frame in `src/assets/logo_sheet.png`.
2. Removed Facility Admin's Transaction History navigation entry because it is not present in `MainLayoutPart1.navigationFor("ADMIN")`.
3. Updated browser status labels to match `UiSupport.displayStatus()` for special request states.
4. Adjusted status badge tones for released/borrowed, approved, pending, and declined states to better match `UiSupport.statusColor()`.
5. Added a short logo entrance animation and a `prefers-reduced-motion` override. Existing page/card/modal/button motion remains in place.
6. Added source alignment notes and limitations to `README.md`.
7. Removed generated backend `.class` build output from the ZIP; `backend/build-web.bat` regenerates it from the desktop and web Java sources.

## Design tokens checked
- Brand: `#660000`
- Hover: `#8C1A1A`
- Pressed: `#4D0000`
- Dark page/header: `#1A0808`
- Dark cards: `#240D0D`
- Dark inputs: `#301212`
- Light surface: `#FFFFFF`
- Light deep surface: `#F8F4F4`
- Border: `#E4D8D8`
- Font: Poppins

## Important source limitation
The supplied desktop source has no Chatbox UI/controller/service/model. The web package therefore does not invent a Chatbox implementation.

## Verification performed
- Checked all literal local asset references in frontend HTML/CSS for missing files.
- Compared role navigation against the desktop navigation definitions.
- Compared status labels and theme tokens against desktop source.
- JavaScript syntax and full backend runtime still need to be verified in the user's actual EquipLink project environment with its required PostgreSQL JDBC driver and database configuration.

---

# Improvement pass (2026-11-23)

## Dark mode redesign
The old dark theme copied the desktop's `#1A0808 / #240D0D` values, which are near-black with almost no
separation between page, card and input, and the dark maroon buttons nearly vanished. The new palette
(`css/theme.css`) keeps the brand wine but lifts and layers it:

| Token | Old | New |
|---|---|---|
| Page | `#1A0808` | `#2A1B1D` |
| Header | `#1A0808` | `#301F21` |
| Card | `#240D0D` | `#382527` |
| Input | `#301212` | `#2D1D1F` |
| Border | `#532020` | `#5E4144` |
| Primary button | `#640505` | `#A8323A` (hover `#BE424A`) |
| Red accent text | `#640505` | `#FF9D9D` |
| Sidebar | `#660000` | `#5A1A1F` |
| Text / muted | `#FFFFFF` / `#D9CACA` | `#F8EEEC` / `#CFBAB7` |

Contrast (WCAG): body text 12.6:1, muted text 7.8:1, white on primary button 6.6:1, status tones 5.7–7.6:1.
Hard-coded `#660000` / `rgba(102,0,0,…)` values in the CSS were replaced by tokens so nothing turns
invisible in dark mode. Light mode is unchanged and still equals `AppTheme.java`.

## Cards, hover and motion (from DashboardCard / PrimaryButton / NavigationButton / Motion)
- Stat cards: per-card accent colour, accent pill that grows on hover, accent ring + tint, diagonal shine
  sweep, 3px lift, inset "pressed" state, press ripple, cascade entrance (70ms steps) and 450ms count-up.
- Buttons: raised look, gloss border, shine sweep on hover, ripple on press, solid Danger style like desktop.
- Sidebar: raised hover tile with 4px label nudge, active pill sweeps open with accent bar.
- Inputs: hover/focus ring using the brand tint. Tables: row fade-in stagger, zebra rows, tinted hover.
- Modals/toasts: pop-in with overshoot; toasts slide in and out. Smooth colour cross-fade when toggling theme.
- Thin pill scrollbars like the desktop. `prefers-reduced-motion` is still honoured (ripple and count-up skip).

## Content aligned with the desktop app
- Dashboard: "Good day, <name>!" welcome card with the role's main action, stat cards with the desktop's
  labels, captions and colours for Borrower / Staff / Admin / Director, "Recent requests" (code, venue · date,
  status badge; click opens details) and "Quick actions" with the same role-based routes and descriptions.
- Settings: added **Security settings** (change password via existing `/auth/password` endpoint),
  **System information**, and a visible active Light/Dark mode button.

## Files changed
`css/theme.css`, `css/app.css`, `js/motion.js` (new), `js/pages/dashboard.js`, `js/pages/settings.js`,
`js/state.js`, `js/ui.js`, `js/api.js`, `js/app.js`.

## Not verified here
No browser or database was available in this environment, so visuals and the backend were not run.
JS syntax and CSS brace balance were checked, and contrast ratios were computed.
