# peephole-android v2 — one lookup box, scan-any-QR, polish pass

Status: draft for review (CTO). Issue: MIK-90, child of MIK-88 (owner feedback
on v0.1.0, MIK-24). Builds on [v1](android-v1.md) (MIK-21) — read that first;
this doc only states what changes and why. Design only — no code. Kotlin +
Jetpack Compose, Material 3, dark theme only (unchanged from v1 §0).

## 0. What changed and why

Owner, on v0.1.0 (verbatim, MIK-88): *"i actually imagined the app to be some
carry with me lookup device, where i can scan a qr code which contains an
url, and then do a lookup with it. also of course with any typed ip or url"*
and *"i cannot lookup domains"*.

v1 shipped lookup as one tab among three (IPs/Lookup/Jobs), reachable only
after Instance List → tap an instance → tap the Lookup tab, and that tab's
one `OutlinedTextField` accepted IPs/CIDRs only (`POST /api/v1/lookup`'s
current `{"ips":[...]}` shape). That is three decisions the owner's own
words contradict: lookup should be the thing the app opens to, it should
read IP **or** host **or** URL, and the QR scanner already in the app
(pairing-only) should answer any QR, not just a pairing one.

This doc covers exactly those three gaps, plus the polish pass the issue
also asks for. It does not revisit anything in v1 §6.4–§6.9 (IP detail,
probe, scan-confirm, job status, settings) beyond the polish findings in
§4 below — those flows are unchanged.

**Server dependency.** `POST /api/v1/lookup` taking host names/URLs is
MIK-89 (in progress at the time of writing; exact JSON field names are
explicitly that ticket's call — "extra field such as `names`, or mixed
entries in `ips`"). §2.3 below specifies the *data* this screen needs from
that response, using the field names already fixed in the server's
existing `NamesView`/`AddrVote` structs (`src/admin/lookup.rs` — unaffected
by MIK-89, which wraps the same `by_name()`/`NamesView` logic in JSON
rather than replacing it, per that ticket's own description: *"a name
result carries the resolver view (votes/agreed addresses, same data the
admin page shows)"*). The exact envelope key is **TBD — MIK-89**; Engineer
should confirm the final response shape against that PR before wiring
this screen, but the fields and their meaning below should not move.

## 1. Navigation: Lookup becomes Home

v1 §2's map gated every screen behind Instance List. v2 inverts that:
**Home is the lookup box**, and it is the nav graph's start destination.

```
Home (Lookup + Scan QR)  ──(switch via instance chip)──▶  bottom sheet: paired instances
     │                                                           │
     ├─(tap an address result)──▶ IP Detail (unchanged, v1 §6.4) │
     ├─(tap "IPs" in bottom nav)──▶ IP List + Search (v1 §6.3)   │
     ├─(tap "Jobs" in bottom nav)──▶ Job status (v1 §6.8)        │
     │                                                           │
     └─(Scan QR)──▶ full-screen scanner ──(classify, §2)──┬──▶ Pair confirm (v1 §6.2, unchanged)
                                                            ├──▶ Home, results shown inline
                                                            └──▶ Home, "nothing looked up" banner
```

Concretely, in `PeepholeApp.kt`'s `NavHost`:

- `NavHost`'s `startDestination` changes from `"instances"` to a new
  `"home"` route. `"instances"` (`InstanceListScreen`) still exists, now
  reached from Home's instance chip (below) or from Settings' "pair
  another" — it stops being the app's front door.
- Add `InstanceStore.lastUsedId: String?` (persisted alongside the existing
  instance rows — a one-column addition, no migration of existing data
  needed since it's nullable and defaults to "most recently paired" the
  first time it's read). Every successful lookup or IP-detail open updates
  it. Home reads it to pick which instance's data a plain lookup queries
  when more than one is paired.
- Home's `Scaffold` keeps the same `NavigationBar` component as v1 §4, now
  with **Lookup first**: tabs become **Lookup · IPs · Jobs** (was
  IPs/Lookup/Jobs) — serial-position and recognition-over-recall both say
  the primary action belongs in the first slot, not the one a returning
  user has to remember to tap.
- Home's `TopAppBar` gains the instance-switcher chip v1 §2 described but
  never implemented: a `AssistChip` in the title row showing the active
  instance's display name, `onClick` opens a `ModalBottomSheet` listing
  every paired instance (reusing `InstanceListScreen`'s `InstanceRow`
  composable as the sheet's row — no new list component). Selecting a row
  sets `lastUsedId` and dismisses the sheet; "Pair another instance" and
  "Manage instances →" (opens the full `InstanceListScreen`) sit below the
  list in the same sheet. This is the one net-new piece of chrome v2 adds;
  everything else reuses v1 or existing code.

### 1.1 Zero instances paired

Home is still the start destination — it does not redirect to Pair. The
lookup `OutlinedTextField` and its submit button are **disabled**, with the
v1 §1 pattern (reason visible, never hidden): *"Pair an instance to look
things up."* **Scan QR** stays enabled and prominent — scanning is also
how a first pairing happens (§2), so it is the one working control on an
otherwise-empty Home, which matches the owner's own mental model of "scan
a QR, get a lookup" better than forcing a separate "Pair" screen first.
`EmptyState` (v1 §5) is not used here since the field itself carries the
message; a second, smaller line under the field reads "No instance paired
yet — scan a pairing QR from an instance's Devices page, or a lookup QR
with a URL on it."

## 2. Scan flow: any QR, classified

v1's scanner (`QrScanner.kt`, reused verbatim — no new camera code) only
ever fed one consumer, `PairViewModel.scanned()`, which treats anything
that isn't a valid pairing payload as a dead end ("This QR code isn't a
peephole pairing code" → back to scan). v2 needs a classifier in front of
that, used from Home's **Scan QR** button, not from inside the Pair flow.

### 2.1 Classifier (new, small, pure function — no I/O)

```
sealed interface ScanOutcome {
    data class Pairing(val payload: PairingPayload) : ScanOutcome
    data class LookupTarget(val query: String, val extractedFrom: String?) : ScanOutcome
    data class Unreadable(val text: String, val reason: String) : ScanOutcome
}

fun classify(text: String): ScanOutcome =
    PairingPayload.parse(text)?.let { ScanOutcome.Pairing(it) }
        ?: extractHost(text)?.let { ScanOutcome.LookupTarget(it, text.takeIf { raw -> raw != it }) }
        ?: ScanOutcome.Unreadable(text, whyNot(text))
```

Order matters and is fixed: **pairing payload first** (it is the one
unambiguous, structured format — `{"v":1,"origin":...,"code":...}`, already
parsed by `PairingPayload.parse` in `pairing/PairingPayload.kt`, reused
verbatim), then a URL or bare host/IP reduced to its lookup query by
`extractHost` (same host-from-URL reduction the server's MIK-89 is adding
to `/api/v1/lookup` — `http(s)://host[:port]/path?q` and bare `host/path`
both reduce to `host`; an IP-literal host, including `[v6]`, is passed
through as the IP itself, not treated as a "name"), then — if neither
matched — the raw text is shown as unreadable. `whyNot` returns one of:
*"This isn't a web address, IP address, host name, or pairing code."* (the
general case) or, when the text is non-empty but clearly not URL-shaped
and not a bare token either (e.g. contains a newline, or decodes to binary
garbage), the same string — one reason string, not per-failure variants,
matching v1 §5's "one disabled-reason string" pattern for consistency.

### 2.2 Routing from Home

**Scan QR** opens a full-screen destination (own back-stack entry, same
weight rule as v1 §2 gave Pair-confirm and Scan-cost-confirm — a modal
`Dialog`/`BasicAlertDialog` is too easy to tap through blind for something
that is about to run a query or start a pairing) containing `QrScanner`
plus the viewfinder chrome from v1 §6.2 Step 1 (reused), titled "Scan a QR
code" instead of "Pair an instance."

On a decode, classify immediately (lookup costs nothing, so — per the
issue's acceptance criterion — **no confirm step**, but the extracted
value is always shown, never swapped in silently):

- **`Pairing`** → navigate to the existing `"pair"` route, pre-loaded with
  the parsed payload (skip Step 1's camera; land straight on v1 §6.2 Step
  2's confirm screen, which already shows the bare origin for the human to
  check — nothing new needed there). Implementation note: thread the raw
  scanned text as a nav argument (`"pair?scanned={text}"`) and have
  `PairScreen` call `vm.scanned(it)` once on arrival if present, instead of
  requiring a second scan inside the Pair flow.
- **`LookupTarget`** → pop back to Home, prefill the lookup field with
  `query`, and submit immediately. A banner above the results reads
  **"From QR: {originalText}"** when `extractedFrom != null` (i.e. the QR
  held a full URL, not a bare address) so the user sees what was reduced
  before trusting the answer — e.g. `https://example.com:8443/path?x=1`
  scans in, the banner says "From QR: https://example.com:8443/path?x=1",
  the field shows `example.com`, and the lookup runs against that host.
  When the QR held exactly a bare IP/host with nothing to strip, skip the
  banner — the field alone already shows exactly what was scanned, a
  second banner repeating it would be noise (cognitive load — only surface
  the "what was extracted" step when something was actually extracted).
- **`Unreadable`** → pop back to Home, do not touch the lookup field or run
  anything, show a banner: the scanned text verbatim (mono, truncated with
  a "show more" if very long — some QRs hold kilobytes) and the reason
  from §2.1. This is the acceptance criterion's "show the text and say why
  nothing was looked up," met literally.

No new permission, no new dependency: this is the same `QrScanner`
Composable, a new pure classifier function, and routing — camera code does
not change.

## 3. Home screen

```
┌───────────────────────────────┐
│ peephole      sentry.example.org ▾│ ← instance chip, opens switcher sheet
├───────────────────────────────┤
│ ┌───────────────────────────┐ │
│ │ IP, CIDR, host name, URL, ││  ← OutlinedTextField, multiline (bulk
│ │ or a pasted list          ││     paste still works, v1 §6.5 parity)
│ └───────────────────────────┘ │
│ [ Look up · stored data,     ] │  ← same cost framing as v1 §6.5: this
│ [ no cost                    ] │     screen never asks a paid provider
│                                 │
│        ┌─────────────┐         │
│        │  ⬚ Scan QR   │         │  ← FilledTonalButton, full width,
│        └─────────────┘         │     equal visual weight to the submit
│                                 │     button — this is the other primary
│                                 │     action, not a secondary icon
├───────────────────────────────┤
│ From QR: https://example.com/  │ ← only when a scan extracted something
├───────────────────────────────┤
│ example.com → 3 addresses       │ ← name result card, §3.1
│ agreed by 4 of 5 resolvers      │
│ 203.0.113.9        ● sev 2  [›] │
│ 203.0.113.10        ● sev 0 [›] │
│ 198.51.100.4  (disputed)    [›] │
├───────────────────────────────┤
│ 203.0.113.9          ● sev 2 [›]│ ← plain-IP results, v1 §6.5 unchanged
│ 198.51.100.9 — not stored       │
└───────────────────────────────┘
          [IPs]  [Lookup●]  [Jobs]   ← bottom nav, Lookup first & selected
```

This is `LookupScreen.kt` (`app/src/main/java/.../ui/LookupScreen.kt`),
extended rather than rewritten:

- The field's label changes from *"Addresses or networks, one per line"*
  to *"IP, CIDR, host name, URL, or a pasted list"* — the one copy change
  that announces the new capability.
- A `FilledTonalButton` ("Scan QR") is added below the submit button, full
  `fillMaxWidth()`, not a small icon-button — Fitts's Law: this is now a
  primary action the owner explicitly asked for, it gets a primary-sized
  target, not an afterthought in the app bar.
  `Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)` + "Scan
  QR" text, reusing Material Icons Extended (already a transitive Compose
  dependency via `material-icons-extended` — no new library, satisfying
  the issue's constraint).
- `lookupEntries()` (currently `input.split(...).filter { it.isNotEmpty() }`)
  stays as the line/comma/space splitter; each entry is classified
  client-side only enough to route the request correctly (an entry that
  parses as `IpAddr` or a CIDR goes in the existing `ips` request path
  unchanged; anything else is a candidate name/URL entry, reduced via the
  same `extractHost` used by the scanner in §2.1, and sent however MIK-89
  ends up wanting names sent — **TBD**, isolate this behind one request-
  building function so that choice is a one-function change when MIK-89
  lands).
- Response rendering gets one new case before the existing
  `results`/`missing`/`unreadable` rows (§3.1), the rest of §6.5's layout,
  states and copy are unchanged from v1.

### 3.1 Name result card

For each resolved name in the response (shape below — **field names TBD,
MIK-89**; the fields themselves are the server's existing `NamesView`):

```
data class NameResult(
    val name: String,                 // NamesView.name
    val note: String?,                // NamesView.note — shown as-is when present
    val addresses: List<AddressVote>, // NamesView.rows
    val resolvers: List<String>,      // NamesView.resolvers — "asked: …"
    val also: List<String>,           // NamesView.also — not looked up, overflow
)
data class AddressVote(val ip: String, val agreed: Boolean, val verdict: String)
```

Rendered as one `Card` (`shapes.medium`, matching every other result card
on this screen):

- **Header**: `"{name} → {addresses.size} address" + (plural)`, e.g.
  *"example.com → 3 addresses"* — the literal phrasing the acceptance
  criterion names. Directly under it, the resolver-agreement line: `note`
  verbatim if the server sent one (it already carries the nuanced cases —
  *"only from bob (DE), disputed"*), else a synthesised *"agreed by {n} of
  {resolvers.size} resolvers"* from counting `agreed == true` rows. This
  line is never omitted — a name result with no agreement statement at all
  would let a reader assume unanimity that was never checked (data
  honesty: an absence of caveats must not read as a stronger claim than
  the evidence supports).
- When **no row is `agreed`** (a wholly disputed resolution) or `note`
  mentions disagreement, the card's header uses the `warning` role (v1 §3
  token) instead of plain text colour — the one place on this screen colour
  carries extra weight, and it's paired with the text that already says
  so, not colour alone.
- **Per-address rows**: `IpRow`-style (reuse `IpRow` from `IpListScreen.kt`
  where the data is available — a resolved address usually *is* an
  `IpSummary` the moment it's looked up) with the `verdict` string as a
  second line where `IpRow`'s normal summary line would go, e.g.
  *"198.51.100.4 — only from bob (DE), disputed"*. Every row stays
  **tappable into IP Detail** regardless of `agreed` (per the acceptance
  criterion: "the per-address results, each tappable") — a disputed
  resolution is still a real address someone should be able to inspect,
  hiding it would be the actual dishonesty, not showing it.
- **`also`** (addresses past the first `MAX_FOLLOWED`, agreed on but not
  looked up — same cap the web admin already applies): one line, muted,
  mono, *"Also resolves to {also.joined}"* + *"(not looked up: only the
  first N agreed addresses are)"*, parity with `admin_lookup.html`'s
  existing `n.also` block. Not tappable — nothing was fetched for these.
- **`resolvers`** and any resolver **errors** (`NamesView.errors`, a node
  that didn't answer): one muted line each, same treatment v1 §6.6 gives a
  probe vantage that errors (shown, not hidden, doesn't block the rest of
  the card).

A bulk paste that mixes names and addresses renders one card per resolved
name (in input order) followed by the existing plain-address table —
headings make the switch from "names" to "addresses" obvious without a
tab or mode toggle, keeping v1's "no separate bulk mode" decision intact.

### 3.2 States (delta from v1 §6.5 / §5 only)

- **loading**: unchanged (per-address busy state); a name entry shows
  "Resolving…" in place of "Looking up…" while the resolve step is
  outstanding, since that's a materially different wait (DNS, not a
  dataset read) worth naming accurately.
- **error, per name**: an unresolvable name is a **per-entry** failure, not
  a whole-request failure (MIK-89's own acceptance criterion requires
  this) — rendered as a card with the header and an inline message
  ("example.invalid — could not be resolved.") instead of the address
  rows, same visual weight as the `unreadable` rows already in v1 §6.5,
  not a screen-level error banner.
- **zero instances paired**: §1.1.
- **offline / 401 / 403**: unchanged from v1 §6.5 (a plain lookup, named or
  not, is always a read; nothing here needs `act` scope).

## 4. Polish pass

No running build exists in this environment to screenshot (same
constraint v1 §9 recorded, still true — there is no headless-browser or
Android-emulator container provisioned for this delegate, and loopback-
only tooling doesn't reach a phone UI either way). Every finding below is
grounded in reading the actual Compose source that shipped
(`feature/screens`, commit `572e401`), cited by file and line, not by
inference — the real visual check is still the Engineer's first build,
screenshotted against this spec, exactly as v1 §9 deferred it.

| # | File : line | Before | After | Why |
|---|---|---|---|---|
| 1 | `InstanceHome.kt:24` | `Tabs = listOf("IPs" to "⌕", "Lookup" to "≡", "Jobs" to "◷")` — Unicode glyphs inside a plain `Text`, rendered by whatever system font supplies that code point | `NavigationBarItem(icon = { Icon(Icons.Outlined.Dns / .Search / .History, contentDescription = null) }, ...)` using Material Icons Extended | Glyph characters aren't icons: no guaranteed glyph coverage across devices, no optical alignment to Material's icon grid, no state-aware tinting. This is the single most "rough" visual cue in the app — placeholder characters standing in for a design decision that was never made. |
| 2 | `Common.kt:174` (`Section`) | `Text(if (open) "▾" else "▸", ...)` | `Icon(Icons.Default.ExpandMore, Modifier.rotate(if (open) 180f else 0f))`, animation skipped (snap) under reduced-motion | Same glyph-as-icon problem, on every collapsible section across IP Detail, Lookup's future per-provider cards, etc. — it's the most-repeated instance of #1. |
| 3 | `InstanceListScreen.kt:38` | `ExtendedFloatingActionButton(onClick = onPair) { Text("+ Pair") }` | `ExtendedFloatingActionButton(onClick = onPair, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Pair") })` | `ExtendedFloatingActionButton` has named `icon`/`text` slots for exactly this; cramming `"+ Pair"` into one `Text` means a screen reader reads "plus sign Pair" instead of an icon it already knows to announce as "Add." |
| 4 | `Common.kt:119` vs. `PairScreen.kt:207` | Two functions named `ErrorBanner` with different signatures and different looks: `Common.kt`'s is a flat `Row.background(errorContainer)` with square corners, edge-to-edge; `PairScreen.kt`'s is a `Card` with the theme's rounded shape | One `ErrorBanner(message, onRetry: (() -> Unit)?)` in `Common.kt`, `Card`-based, used everywhere (`PairScreen.kt`'s copy deleted) | v1 §5 states the error pattern "reads identically everywhere" — it currently doesn't; which look you get depends on which screen you're reading, which is exactly the kind of silent inconsistency the visual-quality bar calls out. |
| 5 | `Common.kt:86` (`SeverityChip`), `:101` (`StatusBadge`) | Hand-rolled `Text(...).background(color.copy(alpha=0.16f), RoundedCornerShape(6.dp))` | `AssistChip(label = { Text(...) }, colors = AssistChipDefaults.assistChipColors(containerColor = ..., labelColor = ...))` | v1 §4 already named `AssistChip`/`FilterChip` as the component for exactly this ("badge/badge-status/chip" analogue); the shipped code reinvented it instead of using it, so it's missing the chip's built-in 32dp min height and label padding — easy to get visually inconsistent chip sizes across screens as more are added (the new name-result card in §3.1 is the first thing that would inherit that drift). |
| 6 | `Theme.kt` (whole file) | `PeepholeTheme` overrides `colorScheme` and `shapes` only; `MaterialTheme.typography` is Compose's stock `Typography()` (system Roboto) | Add an explicit `Typography()` override: Inter (400/500/600, OFL-licensed, bundled as font resources — not a new *library* dependency, satisfying the issue's constraint) mapped to `bodyLarge`/`labelLarge`/`bodyMedium` per v1 §3's own table; a `monoSmall`/`monoMedium` `TextStyle` using JetBrains Mono (also OFL) in place of the generic `Mono = TextStyle(fontFamily = FontFamily.Monospace, ...)`, which falls back to whatever mono font a given OEM ships | This is likely the single biggest cause of the "rough" first impression: v1 §3 specified a type system and it was never wired in, so every screen is running on the Android system default instead of the two families chosen to match the web admin (the doc's own screenshot-parity goal, §3: "so a side-by-side screenshot... reads as one product" — it currently can't, because the fonts don't match). |
| 7 | Scattered: `LookupScreen.kt:62,67`, `PairScreen.kt:76`, `JobScreens.kt:109,120,123,132`, `ActScreens.kt:70-anywhere `Spacer(Modifier.height(...))`` | Literal dp values chosen per call site: 8, 12, 16, 24, 32 all appear with no shared source | A `Spacing` object in `Theme.kt` (`val xs = 4.dp; val sm = 8.dp; val md = 16.dp; val lg = 24.dp; val xl = 32.dp`), call sites updated to reference it | Not visually wrong today, but there's no single place that *is* the spacing scale — every new screen (Home included) is one more chance to pick a sixth number. The visual-quality bar asks for "spacing from a scale," which requires the scale to exist as a named thing, not just a convention in people's heads. |
| 8 | `IpDetailScreen.kt:172-181` (`Tile`), `ActScreens.kt:177-181` (scan quote's cost block) | Both hand-roll `Column.background(surfaceContainer, RoundedCornerShape(...))` instead of a `Card` | `Card(colors = CardDefaults.cardColors(containerColor = surfaceContainer), elevation = CardDefaults.cardElevation(0.dp))` | v1 §3 was explicit: "Compose `Card` with `tonalElevation` only... instead of a default Material 'paper' feel" — the intent was to *use* `Card` with elevation dialed down, not to avoid `Card` entirely. The hand-rolled version loses `Card`'s built-in content padding defaults and semantics (a `Card` is a recognized container to assistive tech; a plain `Column` with a background colour is not). |

None of these are behaviour changes — every one is a visual/semantic
substitution Engineer can do screen-by-screen without touching navigation,
state, or network code, and each is independently verifiable by `grep`
against the file:line cited (no build required to confirm the *before*;
confirming the *after* needs the screenshot pass noted above).

## 5. Accessibility (delta from v1 §7)

- Every icon introduced in §4 items 1–3 carries `contentDescription` per
  v1 §7's existing rule — items 1–2 pass `null` because an adjacent visible
  label already names the control (nav label, section title); item 3's FAB
  icon also passes `null` for the same reason (the `text` slot is the
  label). Icon-only controls that gain no adjacent label stay out of scope
  here — there are none newly introduced.
- The instance-switcher chip (§1) is a tap target ≥48dp (the chip's own
  padding, not its visible glyph size, same rule v1 §7 already states for
  badges) and the bottom sheet it opens is dismissible by back-gesture and
  by an explicit close affordance, not swipe-only.
- Rotation on the `Section` chevron (§4 item 2) is skipped under
  `Settings.Global.ANIMATOR_DURATION_SCALE` / Compose's reduced-motion
  signal, per v1 §7's existing countdown rule — same treatment, same
  reasoning, now applied to a second animated element.

## 6. Still outstanding

- No visual-truth check was possible in this delegate's environment — no
  Android build, emulator, or headless-browser container is provisioned
  here, and this host's loopback-only networking rules out a remote
  preview either. Per v1 §9, this is the Engineer's first implementation
  pass to close, screenshotted against this spec at 1440×900-equivalent
  tablet and phone-portrait widths.
- The exact JSON field names for a name/URL lookup response (§0, §3.1) are
  MIK-89's decision, in progress at the time of writing. The data shape
  this screen needs is fixed above (mirrors the server's existing
  `NamesView`/`AddrVote`); only the envelope key(s) are open. Engineer
  implementing the Android side (MIK-91) should confirm against MIK-89's
  merged `docs/api-v1.md` before wiring §3.1, not against this doc's
  placeholder struct names.
- `extractHost`'s exact URL/bare-host parsing rules (scheme-less input,
  `userinfo@host`, IPv6 literals in brackets) should mirror whatever
  MIK-89 lands for the server's own parser 1:1, so a QR the phone resolves
  locally and a query the server resolves agree on what the "host" was —
  worth Engineer diffing the two parsers once MIK-89 is merged, rather
  than writing the Android one from this doc's prose alone.
