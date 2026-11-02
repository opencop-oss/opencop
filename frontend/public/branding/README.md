# Branding assets

This directory contains OpenCOP's default user-interface branding.

## Files and consumers

| File | Use |
| --- | --- |
| `favicon.svg` | Browser icon loaded by the application entry page. |
| `logo.svg` | Sidebar logo loaded by the shared `Logo` component. |
| `logo-full.svg` | Optional scalable OpenCOP wordmark; it is not loaded by the application by default. |
| `logo-full.png` | Optional raster OpenCOP wordmark; it is not loaded by the application by default. |

## Deployment overrides

A deployment can replace these static files before building the application or mount replacements in the `branding/` directory beneath its web server's document root. The provided container serves `/usr/share/caddy`, so its mount target is `/usr/share/caddy/branding/<filename>`. Keep the filenames and public `/branding/<filename>` URLs stable so existing consumers continue to work.

## Ownership and fonts

OpenCOP owns the four branding assets listed above. Publication and trademark use remain subject to project approval.

`logo-full.svg` names TT Firs Neue font families but does not embed any font bytes. Rendering its text with those fonts requires a separately licensed local installation; this repository does not grant rights to redistribute TT Firs Neue.
