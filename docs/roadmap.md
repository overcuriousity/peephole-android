# Roadmap

What is planned for the app, in order, with what each item costs and what it
brings. The server side of an item is tracked in peephole's
[roadmap](https://github.com/overcuriousity/peephole/blob/master/docs/roadmap.md).

**Cost**: S = about a day, M = a few days, L = a week or more, including
tests and docs. **Benefit**: what it adds for the person holding the phone.

The direction comes from the owner's feedback on v0.1.0 (2026-10-10): the app
should be a lookup device to carry around. Scan a QR code that holds a URL, or
type an IP, hostname or URL, and look it up.

## v0.2: built, release pending

- [x] 1. Look up a hostname or URL (peephole-android #6, #7).
- [x] 2. Scan any QR code and look up its host (#7).
- [x] 3. Visual polish (#8).
- [x] Probe price before you commit (#3).
- [x] Job status and log: a status header with progress and elapsed time,
  one timestamped row per fact, Copy and Share. api-v1 has no event times,
  so a row's time is when the phone first saw it, and the copied log says so.

A job cannot be cancelled once it started: api-v1 has no `cancelled` status
and no endpoint to stop a running job, so the app shows only `done` and
`failed`. Stopping a job needs a server change first.

The items as planned:

### 1. Look up a hostname or URL

M (server S, app S–M), high. Today the lookup screen takes IPs only, and the
owner's first finding was "i cannot lookup domains".

- Server: `/api/v1/lookup` also accepts names, through the same multi-node
  resolution `/admin/lookup` already uses. It returns the resolved addresses,
  how far the resolvers agreed, and the usual record for each address.
- App: one input takes an IP, a hostname or a URL. A URL is reduced to its
  host on the phone. The result shows the name, the addresses it resolved
  to and the resolver agreement, the way the web lookup does.

### 2. Scan any QR code and look up its host

S–M, app only once item 1 exists. High: this is the lookup device.

- The scanner tells a pairing QR apart from any other code. A URL or bare
  host goes to lookup; anything else is shown as text.
- A scanned URL is untrusted input. The app never opens it, never fetches
  it and never resolves it on the phone. Only the host goes to the server,
  which does the resolving.

### 3. Visual polish

M, medium. The owner found v0.1.0 "a little bit rough". UXDesigner first
reviews the shipped screens against [the design](design/android-v1.md),
then the app gets the fixes: spacing, type, empty and error states, dark
theme.

v0.2.0 is items 1–3 plus the probe price. It is tagged and released as a
signed APK, like v0.1.0.

## Not yet tried on a phone

These two flows are covered by unit tests and enforced by the server, but
nobody has run them on a real device yet. Check them in the v0.2 QA pass.

- Pairing aborts when the TLS key does not match the QR.
- A stale scan quote shows its error and charges nothing.

## Not planned

- Push notifications and live feeds.
- Editing node settings, the cluster or ownership from the app.
- iOS.
- A Play Store or F-Droid listing. The app stays free of Play services, so
  F-Droid remains possible.
