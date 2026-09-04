# Reserve

A video player for Android that works like a karaoke machine. While something is playing, you
search your device for another video and **reserve** it. It plays as soon as the current one
ends. Reserve as many as you like, in any order, without ever interrupting what is on screen.

Everything is local. The app plays videos already on your device and **declares no network
permission at all**, so it cannot phone home even if it wanted to.

## Why it exists

Every player treats a playlist as something you build first and watch second. A karaoke machine
works the other way round: the song is playing, and people queue up the next one while it does.
This is that, for the videos already on your phone or TV box.

## What it does

- Plays videos from your device, full screen.
- Opens a search overlay **over** the playing video — playback keeps running while you browse.
- Reserving a video queues it; the queue plays out automatically, one video after another.
- A "coming up" panel lets you remove a reservation, move it up or down, or bump it straight to
  the front.
- The same video can be reserved twice — each reservation is its own entry, like a real karaoke
  queue.
- Reserve while nothing is playing and it just starts.
- A video that will not decode is skipped rather than dead-ending the session.
- Works from a TV remote (D-pad, OK, Back, Menu, Play/Pause, Next) as well as by touch.
- Keeps the screen awake while playing, ducks for phone calls, and pauses when headphones are
  unplugged.
- The queue survives rotation, and survives the app being killed in the background.

## Requirements

- Android 5.0 (API 21) or newer — phone, tablet, or an Android TV box.
- Permission to read the videos on the device. Nothing else.

## Installing

**Download [reserve-debug.apk](reserve-debug.apk)** and open it on the device. Android will ask
you to allow installs from unknown sources — that prompt is normal for anything not from the Play
Store. `minSdk` is 21, so it installs back to Android 5.0.

Two honest things about that file:

- **It is a debug build**, signed with Android's standard debug key (`CN=Android Debug`). It runs
  exactly like a release build for everyday use, but it is not Play-Store signed, and you cannot
  upgrade it in place from a differently-signed build later.
- **It is a binary from the internet.** If you would rather not trust one, build your own from
  this source with the command below — the result is the same app.

### Building it yourself

```
gradle :app:assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

There is deliberately **no `gradlew` wrapper committed**, so use your own `gradle` — the commands
here are `gradle`, not `./gradlew`. You need JDK 17, Gradle 8.10.2, and an Android SDK with
platform 35 and build-tools 35.0.0.

The workflow at [.github/workflows/ci.yml](.github/workflows/ci.yml) runs both test suites and
builds the same APK, if you would rather let CI do it.

## Using it from a remote

| Key | What it does |
|---|---|
| **OK / Enter** | Shows the transport controls — the same thing a screen tap does on a phone |
| **Left** | Opens the "coming up" queue; press again to close it |
| **Right** | Opens the reserve browser; press again to close it |
| **Menu** | Also toggles the queue |
| **Back** | Closes an open panel; otherwise asks before backgrounding the app |
| **Play / Pause** | Toggles playback |
| **Next** | Skips to the next reservation |

Every button in every list is individually focusable, so the whole app is reachable with a D-pad.

**The controls carry the rest.** Skip, a shortcut into either panel, a **UI** button that hides
the on-screen badge and Up Next line, and a **Clear** button that empties the queue after
confirming. The player itself deliberately stays out of the D-pad focus order so `OK` always
reaches the app; the controls' own buttons are focusable children, so a remote drives them
normally once they are up.

## What stays on screen

- **`Res. N`**, top-left — how many videos are reserved. It hides when the queue is empty.
- **`Up Next: "…"`**, top-middle — the next title, persistently rather than as a banner you
  might miss. Truncated so it does not compete with the video.
- **Orange dots** in the reserve list — one per time that video is already queued, so a press is
  never in doubt.

Both on-screen elements hide together via the **UI** button in the controls.

## How it is built

Two modules, split so the interesting logic can be tested without a device:

| Module | Holds |
|---|---|
| `logic/` | Pure Kotlin, no Android imports — the queue, the search ranking, and the rule for what plays next. |
| `app/` | The Android shell — the player, the overlays, the device scan, the remote keys. |

The queue is the single source of truth. The player is only ever handed **one** video at a time,
so nothing has to stay in sync with a player-side playlist while the queue is being reordered
mid-playback. Auto-advance lives in `logic/` behind a small `VideoSink` interface, which is why
it can be proven correct with no phone attached.

## Tests

```
gradle :logic:test :app:testDebugUnitTest
```

92 tests. The `logic/` suite runs on a plain JVM; the `app/` suite runs under Robolectric and
includes a test that actually starts the activity, so a green build means the app launches rather
than merely compiling. CI additionally counts the executed test cases and fails if either module
contributed zero — a test task that silently runs nothing cannot pass as green.

## Honest limits

- **It has now been run on real hardware — a phone and a Mi Box 3 — and the first pass found
  three bugs.** Playback could not be restarted after backgrounding, touch users had no controls
  at all, and videos outside `Movies/` were invisible. All three are fixed, but the point stands:
  the automated tests here did not catch any of them, so treat "the tests pass" as a floor rather
  than a guarantee.
- Everything is still verified by unit tests, Robolectric and a CI build rather than by a
  device sitting on a desk. That covers this app's own code; it does not cover ExoPlayer painting
  frames on your particular TV, or whether the focus highlights read from a sofa.
- The reserve queue is remembered as a list of video ids. If a video disappears from the device
  while the app is in the background, its reservation is dropped on the way back in.
- Back backgrounds the app while something is playing or reserved, so the queue survives. The
  queue is NOT saved across a deliberate quit — force-closing clears the party.
- Only videos indexed by Android's MediaStore are visible. That now includes downloads, not just
  `Movies/`, but a file the system has never scanned stays invisible — this app does not walk the
  filesystem itself.

## How it was made

This was built end to end in [Omniscio](https://omniscio.com), a desktop app for running and
coordinating Claude Code sessions. Everything here — reading the original request, the design
decisions, the code, the 92 tests, the CI pipeline, and this README — came out of a single
agent-driven run.

Worth stating plainly, since it explains the limits above: no human ever ran this on a phone or a
TV box. The design was pushed through a review pass that caught real problems — a network
permission ExoPlayer injects through manifest merging, a queue action that was implemented and
tested but had no button, and a permission dialog that left users stranded after a second refusal
— but a review pass is not the same as watching it play a video on a sofa. If you install it and
something misbehaves, that is genuinely useful information and worth opening an issue about.

## Licence

MIT. See [LICENSE](LICENSE).
