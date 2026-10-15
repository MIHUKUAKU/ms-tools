修复 Windows 自带 PowerShell 5.1 下，一键安装在前置文件校验阶段提示 `Get-FileHash` 不存在而中断的问题。现在直接使用系统 .NET SHA-512 实现，继续校验官方哈希和模组 ID。

下载 `MS-Tools-1.7.5-Windows-Setup.zip` 并完整解压：

- `开始安装.cmd`：选择存档，安装 MS 工具、补齐缺失前置并开启假人权限和脚本自动加载。
- `启用已有存档.cmd`：已有 MS 和 Carpet 时，为另一个存档启用脚本与权限，无需重新下载模组。
- `安装含交易筛选.cmd`：同时安装独立刷交易筛选。

需要已能正常启动的 Fabric Minecraft 26.2／26.3 与 Java 25，安装前退出游戏。此包不安装 Minecraft、Java 或 Fabric Loader。已有合适前置会复用，相关旧文件先备份。

本次只修复安装器，两个功能 JAR 仍为 mstrade 1.7.3 和 Trade Target 1.4.2。测试范围见[测试记录](https://github.com/MIHUKUAKU/ms-tools/blob/main/docs/测试报告.md)。

Windows Server 2025 测试机已通过：PowerShell 5.1 的 22 项判定检查、5.1 和 PowerShell 7 各 16 个安装场景，以及三个实际 CMD 入口的文件夹选择、确认、安装和存档权限配置。使用一次性测试存档标记，没有启动 Minecraft 客户端；不是全部游戏行为或所有 Windows 桌面环境的保证。

[正式发布包复验记录](https://github.com/MIHUKUAKU/ms-tools/actions/runs/38746117404)：重新下载 v1.7.5 正式 ZIP，直接使用包内文件运行全部安装检查，通过后未替换安装脚本。

新版本优先在 GitHub 发布，论坛可能延后 1 天至 1 周同步。安装或使用遇到问题，欢迎留言或[提 Issue](https://github.com/MIHUKUAKU/ms-tools/issues)。

## English

Fixes one-click setup stopping during dependency verification on Windows PowerShell 5.1 with a missing `Get-FileHash` command. SHA-512 verification now uses the system .NET implementation; official hashes and mod IDs are still checked.

Extract the Windows setup ZIP, run `开始安装.cmd`, and select your save. A working Fabric Minecraft 26.2/26.3 instance and Java 25 are required; exit the game first. Minecraft, Java and Fabric Loader must already be installed. Existing suitable dependencies are reused. Gameplay JAR versions are unchanged.

On Windows Server 2025, PowerShell 5.1 passed 22 detection checks; both 5.1 and PowerShell 7 passed 16 installation scenarios each. All three actual CMD entry points passed native folder-picker, confirmation and installation checks using disposable save fixtures. Minecraft itself was not launched in this installer test.

[Public release verification](https://github.com/MIHUKUAKU/ms-tools/actions/runs/38746117404) downloaded the published v1.7.5 ZIP and tested its files without replacing the installer.

New versions appear on GitHub first; forum updates may follow 1–7 days later.
