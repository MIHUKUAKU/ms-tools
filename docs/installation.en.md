# Installation

[English overview](../README.en.md) · [中文安装说明](安装说明.md) · [Downloads](https://github.com/MIHUKUAKU/ms-tools/releases/latest)

For **Minecraft Java Edition 26.2 / 26.3, Fabric, Java 25 and Fabric Loader ≥ 0.19.3**. Select dependencies built for your Minecraft version. Other Minecraft versions and macOS gameplay have not been verified.

MS and Trade Target are independent. Install only the part you need, or both. Exit Minecraft before changing files, and back up your world.

## Choose your files

| What you want | Files and dependencies |
| --- | --- |
| MS bot commands | `mstrade-1.7.3.jar`, version-matched [Carpet](https://modrinth.com/mod/carpet), and `ms.sc`, `fetch.sc`, `put.sc`, `throw.sc` |
| Trade Target only | `trade-target-331-1.4.2.jar`, version-matched [Fabric API](https://modrinth.com/mod/fabric-api), [Trade Cycling](https://modrinth.com/mod/trade-cycling) ≥ 1.0.21 and [Visible Traders](https://modrinth.com/mod/visible-traders) ≥ 2.5.0 |
| Windows/PCL installer | `MS-Tools-1.7.5-Windows-Setup.zip`; contains both parts and the scripts, with a choice of installation mode |

The scripts are also available in the repository's [scripts directory](../scripts). A source ZIP is a development project, not a ready-to-install mod.

## Windows/PCL installer

1. Install and launch a supported Fabric instance once in PCL, then exit the game. Install Java and Fabric Loader through your launcher first.
2. Download and fully extract `MS-Tools-1.7.5-Windows-Setup.zip`. Run root-level `开始安装.cmd` for the bot tools, or `安装含交易筛选.cmd` for both parts.
3. Choose the save folder containing `level.dat`. The installer detects its Fabric instance and displays a plan. Shared, non-isolated instances require selecting the Fabric version JSON as well.
4. Confirm the game is closed and install. Restart that instance and enter the selected save; use `/ms`.

Existing dependency JARs are identified by Fabric mod ID and checked against the minimum version and their Minecraft/Loader requirements. Suitable files are reused. Missing or unsuitable files are supplied from bundled, unmodified Carpet/Fabric API/Visible Traders releases or, for Trade Cycling, its official download. Exact versions and SHA-512 values are recorded in `installer/dependencies.json`; attribution and licenses are included. Duplicate or replaced related files are backed up under `.ms-tools-backup`. Verification failures stop before modifying the instance. This is not a complete dependency solver for arbitrary modpacks, and does not guarantee every dependency combination is tested.

Mod JARs are installed for the whole instance; scripts and rules are enabled for the selected save. Java/Fabric Loader must already be installed. The save's Minecraft version is not upgraded by this installer. The advanced `installer/Install.cmd` entry point and manual installation remain available.

For MS it also merges `commandPlayer true` and `scriptsAutoload true` into the selected world's `carpet.conf`. Other rules, personal bot presets and trade-chain data are preserved. Keep the backup until you have confirmed the game works.

## Enable another existing save

If MS and Carpet are already installed, run root-level `启用已有存档.cmd` and select a save. This copies the four local scripts and enables `commandPlayer true` plus `scriptsAutoload true`, without downloading or replacing any mod JARs. The normal setup already includes this step.

## Manual installation

1. Locate the correct Fabric instance in your launcher and exit Minecraft.
2. Put the chosen mod JAR and its version-matched dependency JARs into that instance's `mods` folder. Move old versions outside the instance first so the same mod is not loaded twice.
3. **For MS**, put all four `.sc` files into the target world's `scripts` folder, beside any existing scripts. Back up matching files first. Preserve `.data.nbt` files and trade-chain JSON files; they contain user data.
4. **For MS**, add these rules to that world's `carpet.conf`, preserving other settings:

```text
commandPlayer true
scriptsAutoload true
```

5. Restart the correct instance and enter the selected world. Try `/ms` for MS. Trade Target does not require the scripts or Carpet configuration.

Manual installation applies to Fabric instances beyond PCL, but this release does not claim tested gameplay on every launcher or operating system. Do not run the Windows installer on macOS.

## Start Trade Target

Use a single-player world and a villager bound to a workstation that has never been traded with. Empty the trade input slots and your cursor. Open trading, wait for Visible Traders' advanced previews to finish loading, and open the filter settings. Choose the target item, enchantment or potion effect, price and trade slots, then save. Reopen that villager's trading screen and start the filter.

The filter stops when any selected slot matches. You can stop it manually or close the trading screen. It never purchases the offer automatically.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| `/ms` is missing | Carpet is loaded; all four scripts are in the selected world's `scripts` folder; `scriptsAutoload true` is set. An administrator can inspect logs and, if necessary, load each script with `/script load ms`, `/script load fetch`, `/script load put`, `/script load throw`. |
| Bot permission error | Confirm `commandPlayer true`. The included scripts grant ordinary-player command access; server permission systems may impose additional restrictions. |
| No Trade Target button | Confirm all three Trade Target dependencies match the game version and are loaded. Trade Target currently supports single-player only. |
| Mod version error | Check Minecraft, Java, Fabric Loader and dependency versions. Use matching releases rather than forcing incompatible JARs to load. |
| Installer rejects the path | Choose PCL's specific supported Fabric version folder, rather than `mods`, `saves` or the general game folder. |

For more detail, see the [Chinese installation and backup guide](安装说明.md). When reporting a problem, include versions, the action or command and the relevant log excerpt, after removing private information.
