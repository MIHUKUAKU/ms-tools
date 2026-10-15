# Installation

[English overview](../README.en.md) · [中文安装说明](安装说明.md) · [Downloads](https://github.com/MIHUKUAKU/ms-tools/releases/latest)

For **Minecraft Java Edition 26.2 / 26.3, Fabric, Java 25 and Fabric Loader ≥ 0.19.3**. Select dependencies built for your Minecraft version. Other Minecraft versions and macOS gameplay have not been verified.

MS and Trade Target are independent. Install only the part you need, or both. Exit Minecraft before changing files, and back up your world.

## Choose your files

| What you want | Files and dependencies |
| --- | --- |
| MS bot commands | `mstrade-1.7.3.jar`, version-matched [Carpet](https://modrinth.com/mod/carpet), and `ms.sc`, `fetch.sc`, `put.sc`, `throw.sc` |
| Trade Target only | `trade-target-331-1.4.2.jar`, version-matched [Fabric API](https://modrinth.com/mod/fabric-api), [Trade Cycling](https://modrinth.com/mod/trade-cycling) ≥ 1.0.21 and [Visible Traders](https://modrinth.com/mod/visible-traders) ≥ 2.5.0 |
| Windows/PCL installer | `MS-Tools-1.7.3-Windows-Installer.zip`; contains both parts and the scripts, with a choice of installation mode |

The scripts are also available in the repository's [scripts directory](../scripts). A source ZIP is a development project, not a ready-to-install mod.

## Windows/PCL installer

1. Install and launch a supported Fabric instance once in PCL, then exit the game. Install Java and Fabric Loader through your launcher first.
2. Download and fully extract `MS-Tools-1.7.3-Windows-Installer.zip`. Run `installer/Install.cmd` from the extracted folder.
3. Select the specific Fabric **version folder** containing that instance, not its `mods`, `saves` or general `.minecraft` folder. Choose `MS`, `Trade` or `Both`. For MS, select the target world containing `level.dat`.
4. Review the plan and confirm installation. Start that instance again. MS commands begin with `/ms`; Trade Target's controls appear in the villager trading screen.

The installer downloads third-party dependencies from Modrinth, checks their published SHA-512 hashes and Fabric mod IDs, and backs up replaced files under the instance's `.ms-tools-backup` directory. Those dependencies are separate projects. Network or verification failures stop installation before files are written to the instance.

For MS it also merges `commandPlayer true` and `scriptsAutoload true` into the selected world's `carpet.conf`. Other rules, personal bot presets and trade-chain data are preserved. Keep the backup until you have confirmed the game works.

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
