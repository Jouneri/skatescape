# SkateScape

**Skate across Gielinor, throw sick tricks and never walk again.**

SkateScape is a RuneLite plugin that turns your character into a skateboarder with custom skating movement and trick animations. It is a purely cosmetic client-side plugin: nothing is sent to the game server.

## Features

- Custom skateboard movement across Gielinor
- Five built-in tricks:
  - Kickflip
  - 360 Shove-it
  - Varial Kickflip
  - Kickflip 360 Shove-it Body Varial
  - Christ Air (Even Flow not included :D)
- Trick hotkeys through the RuneLite settings panel
- Queueable trick system for chaining combos

## Public plugin behavior

The public SkateScape release is intentionally simple:

- only the **Trick Controls** section is shown in the settings panel
- the internal trick-tuning and inspector tools used during development are hidden from normal users
- no extra gameplay actions are sent to the game server

## Developer tools

SkateScape includes internal trick-tuning and inspector tooling that was used during development. These controls are intentionally hidden from the public settings UI, but they can be re-enabled in a development build if someone wants to study the system or create their own tricks.

To re-enable the hidden development controls:

1. Open `SkateScapeConfig.java`
2. Set:
   ```java
   DEVELOPER_TOOLS_ENABLED = true;
   ```
3. Restore the developer config sections if they are hidden/disabled in the current build
4. Unhide the developer config items

The original development sections are:

- Trick Tuning
- Pose Tuning
- Advanced Pose Timing
- Animation Testing
- Trick Inspector

These tools are not meant for normal public use, but the code remains available for developers who want to experiment.

## Development client

To launch SkateScape in the RuneLite development client, open PowerShell **in the project root** (the folder containing `gradlew.bat`) and run:

```powershell
.\gradlew.bat clean run
```

If you are using Jagex Accounts, follow RuneLite’s development-client login instructions:

https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts

## Notes

- SkateScape is a cosmetic RuneLite plugin
- it does not automate gameplay
- it does not inject inputs
- it does not send new server actions
- it only enables skating and trick animations entirely on the client

## Planned / Future ideas

SkateScape v1 currently focuses on client-side skating and a trick system. If people enjoy it, there are plenty of ideas for later updates:

- **SkateScape Online** — skate with friends using RuneLite’s Party system. SkateScape movement and tricks would remain cosmetic and would still not send any actions to the game server.
- **Trick streaks and XP drops** — track how many tricks you land consecutively, with cosmetic XP-style feedback as the streak grows.
- **Bails** — unsuccessful tricks and wipeouts.
- **Skateboard cosmetic progression** — unlock different skateboard appearances through SkateScape progression.

More may come later depending on feedback.

## License

SkateScape is licensed under the BSD 2-Clause License. See `LICENSE` for details.
