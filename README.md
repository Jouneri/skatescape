# SkateScape

**Skate across Gielinor, throw sick tricks and never walk again.**

SkateScape is a RuneLite plugin that turns your character into a skateboarder with custom skating movement and trick animations. It is a purely cosmetic client-side plugin: nothing is sent to the game server.

## Features

- Custom skateboard movement across Gielinor
- Queueable trick system for chaining combos
- Trick hotkeys through the RuneLite settings panel
- Five built-in tricks:
  - Kickflip
  - 360 Shove-it
  - Varial Kickflip
  - Kickflip 360 Shove-it Body Varial
  - Christ Air (Even Flow not included :D)

## Public plugin behavior

The public SkateScape release is intentionally simple:

- only the **Trick Controls** section is shown in the settings panel
- the internal trick-tuning and inspector tools used during development are hidden from normal users
- no extra gameplay actions are sent to the game server

## Developer tools

SkateScape keeps the original trick-authoring and inspection tools in the source tree, while the normal public build shows only **Trick Controls**.

To enable the developer tools in a local source build:

1. Open `src/main/java/com/skatescape/SkateScapeDeveloperMode.java`

2. Change:
   ```java
   static final boolean ENABLED = false;
   ```
   to:
   ```java
   static final boolean ENABLED = true;
   ```

That single switch enables both the developer settings UI and the runtime authoring/inspection machinery. Developer mode restores:

- Trick Tuning
- Trick Inspector
- Pose Tuning
- Advanced Frame Timing
- Animation Inspector

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
