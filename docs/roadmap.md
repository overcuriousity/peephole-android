# Roadmap

What is planned for the app, in order, with what each item costs and what it
brings. The server side of an item is tracked in peephole's
[roadmap](https://github.com/overcuriousity/peephole/blob/master/docs/roadmap.md).

**Cost**: S = about a day, M = a few days, L = a week or more, including
tests and docs. **Benefit**: what it adds for the person holding the phone.

The direction comes from the owner's feedback on v0.1.0 (2026-10-10): the app
should be a lookup device to carry around. Scan a QR code that holds a URL, or
type an IP, hostname or URL, and look it up.

## In progress

- **Probe price before you commit.** S. The probe screen lists vantages
  with their prices and shows `Probe · N credits` on the button. The server
  endpoint and the app screen are both in review (peephole #71,
  peephole-android #3).

## Next: v0.2

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
