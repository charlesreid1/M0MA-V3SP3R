# Campaigns

A **campaign** is a multi-phase, AI-driven security engagement that runs in the background against a target list you define. You give it a name, a scope, and a set of authorized targets; the AI works through five phases, pauses whenever it needs a human decision, and produces an audit trail and a final report.

Campaigns live under **Labs → Campaigns**.

> ⚠️ **Off by default.** Campaigns are experimental and disabled until you flip them on in **Settings → Experimental → Ralph autonomous campaigns**. The New Campaign screen shows a warning banner and refuses to create anything until you do.
>
> **Only run campaigns against systems you own or have explicit written authorization to test.**

---

## When to use a campaign vs. chat

- **Chat** — you're driving; each command is your idea. Good for exploration, one-off actions, and quick tests.
- **Campaign** — the AI drives inside a fixed methodology, unattended, over a longer period. Good when you want to sweep a defined target set methodically without staying at your phone the whole time.

If you find yourself typing "now scan again, then enumerate, then try …" repeatedly, that's a campaign.

---

## What a campaign does

Every campaign moves through the same five phases in order:

1. **Recon** — passive discovery across BLE, SubGHz, NFC, RFID, IR. All low-risk, no approvals needed. Runs a connectivity check first so a disconnected Flipper fails immediately instead of wasting your time.
2. **Research** — off-device work: web + GitHub + community catalogs to look up whatever recon found.
3. **Enumerate** — deeper probing of discovered targets to build an attack picture.
4. **Exploit** — **gated**. The campaign always pauses before this phase and waits for you to explicitly release it. Even then, every individual destructive action inside the phase still pauses for its own approval.
5. **Report** — writes a Markdown report to `/ext/reports/` on the Flipper, summarizing everything found.

Each phase reads the findings from earlier phases (via the database, not chat history), so context is preserved across background restarts, phone reboots, and app kills.

---

## Creating a campaign

**Labs → Campaigns → +**

- **Name** — anything short and memorable.
- **Scope** — one-line description of what you're doing and why. The AI reads this at the start of every phase.
- **In-scope targets** — the authoritative list. BLE MACs, hostnames, frequency ranges — whatever identifies the things you're allowed to touch. **Anything not on this list is refused.**
- **Out-of-scope** — optional explicit exclusions (useful when in-scope is a range but you want to carve out specific addresses).
- **Mode** — safety posture, see below.
- **Max iterations** — soft cap on how long the campaign can run before it pauses for a check-in. Default 10.

---

## Modes and how they interact with risk levels

Every command the AI issues is classified by Vesper into one of four **risk levels**:

| Risk | What it covers | Interactive chat behavior |
|------|----------------|---------------------------|
| **LOW** | Reads, scans, device info | Auto-executes |
| **MEDIUM** | File writes, mutations with limited blast radius | Single-tap **Approve** (with diff preview for writes) |
| **HIGH** | Destructive, exfiltration-adjacent, transmit / write / emulate | **Double-tap** to confirm (second tap within 1.8s) |
| **BLOCKED** | Protected paths (`/int/…`, firmware), sensitive extensions (`.key`, `.priv`, `.secret`), **out-of-scope targets** | Refused; unlock in Settings → Permissions |

Campaigns layer their **mode** on top of that:

| Mode | LOW | MEDIUM | HIGH |
|------|-----|--------|------|
| **Autonomous Safe** (default) | auto | follows your `autoApproveMedium` setting | **always pauses for you** |
| **Autonomous Trusted** | auto | auto (overrides your setting) | **always pauses for you** |

**Two things are guaranteed regardless of mode or settings:**

1. **HIGH-risk actions always pause the campaign.** The `autoApproveHigh` setting in Settings applies only to interactive chat — campaigns ignore it. You will always have the chance to see and approve every destructive action.
2. **Out-of-scope targets are refused.** A target not in your in-scope list (or on your out-of-scope list) is BLOCKED before it's even risk-classified. The AI can't lawyer its way around scope.

Pick **Trusted** when you'd otherwise be tapping "Approve" for every predictable medium-risk step and it's slowing you down; stick with **Safe** if you want to see every write.

---

## Running, pausing, and stopping

**Labs → Campaigns → tap a campaign** to open its detail view.

You'll see:

- **Current status** — Running, Awaiting approval, Paused, Done, or Failed.
- **Current phase** and iteration count.
- **Findings list** — everything the campaign has discovered so far.
- **Jump-to-audit-log** button — filters the audit screen to this campaign's actions.
- **Controls**:
  - **Pause** — halt the current phase; you can resume later.
  - **Resume** — pick up where a paused campaign left off.
  - **Resume exploit phase** — separate button that only appears at the exploit gate, deliberately distinct so you can't release it by muscle memory.
  - **Stop** — terminal; marks the campaign Failed and cancels its scheduled work.

**Kill switch:** **Settings → Experimental → Stop all campaigns** pauses every running campaign at once. Use it if you need everything to stop *now*.

---

## The Approval Inbox

**Labs → Approval Inbox** collects every HIGH-risk action from every running campaign, in one queue. Each pending item shows:

- Which campaign and phase asked for it
- What the action would do (with justification and expected effect)
- Approve / Deny buttons

Pending approvals expire after 24 hours if you don't act on them.

You can also resolve approvals from a campaign's detail screen — the Inbox is just a cross-campaign convenience view.

---

## Notifications

When a campaign hits an approval gate — either mid-phase at a HIGH-risk action, or when it reaches the exploit-phase gate — Vesper fires a **high-importance notification** that will wake your device. Tapping it deep-links straight to the Approval Inbox filtered to that campaign.

You'll need to grant the notifications permission on first launch (Android 13+). If you denied it, campaigns still work, but you won't be woken up when they need you.

---

## Auditing

**Everything a campaign does is logged.** Every command, every result, every approval decision (and who / when) writes to Vesper's audit table.

Two ways to view it:

- **Chat → Audit log** (top-level) — the full log across all sessions. You can filter by risk level.
- **Campaign detail → audit icon** — filtered to the current campaign.

Every finding in a campaign is traceable back to the exact tool call that produced it (audit entry ID); nothing shows up in the findings list that isn't backed by a real logged action.

---

## What makes a campaign stop on its own

- **Report phase completes** → status **Done**.
- **Max iterations reached** → status **Paused**; hit Resume to continue.
- **Per-phase caps hit** — each phase has a tool-call cap (default 30) and a wall-clock cap (default 15 minutes), configurable in Settings. Hitting either pauses the phase.
- **Convergence** — three iterations in a row with no new findings ends the campaign at Report. Prevents runaway loops that aren't producing anything.
- **Unrecoverable error** → status **Failed** with a reason (bad API key, LLM error, corrupted state, etc.).

---

## Practical safety recap

- **Off by default.** Enable in Settings → Experimental.
- **Kill switch** in the same place stops everything at once.
- **Scope is authoritative.** Anything not in your in-scope list is refused.
- **HIGH-risk always pauses**, regardless of mode or auto-approve settings.
- **Exploit phase requires an explicit human release**, in addition to per-action HIGH approvals.
- **Everything is logged and traceable.**
- **You are responsible** for having authorization to test what you're testing.

---

## See also

- [`labs.md`](labs.md) — the other two Labs sub-surfaces (Alchemy Lab, Payload Lab).
- [`architecture.md`](architecture.md) — deeper dive into the risk model and enforcement.
- [`../SECURITY.md`](../SECURITY.md) — reporting vulnerabilities you find with Vesper.
