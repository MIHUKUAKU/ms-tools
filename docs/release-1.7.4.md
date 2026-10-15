# 更智能的假人 v1.7.4 · 一键安装更新

新增一键安装包：解压后运行 `开始安装.cmd`，选择存档，即可自动安装工具、检查并补齐前置、启用存档脚本。进入原存档后直接使用 `/ms`。

一键安装同时开启该存档的假人权限和脚本自动加载，普通生存玩家无需OP。已装好MS与Carpet时，也可以单独运行 `启用已有存档.cmd`，选择另一个存档直接启用，保留全部已有模组。

已有且符合要求的前置会保留；需要替换的相关文件先备份。需要一起安装独立刷交易筛选时，运行 `安装含交易筛选.cmd`。

适用Windows／PCL、Fabric Minecraft26.2／26.3。先在启动器装好Java25与Fabric并正常启动一次，安装前退出游戏。前置的原作者入口与许可证随包保留；Trade Cycling缺失时从官方来源下载并校验。

取物、向玩家跨维度送货、自动整理装盒、交易链及自动驻留交易等完整介绍见[主仓库](https://github.com/MIHUKUAKU/ms-tools)。[安装说明](https://github.com/MIHUKUAKU/ms-tools/blob/main/docs/安装说明.md) · [测试记录](https://github.com/MIHUKUAKU/ms-tools/blob/main/docs/测试报告.md)

新版本优先在[GitHub](https://github.com/MIHUKUAKU/ms-tools/releases/latest)发布；不常用论坛可能延后1天至1周同步，请以GitHub最新版本为准。安装或使用有问题欢迎留言／[提Issue](https://github.com/MIHUKUAKU/ms-tools/issues)。免费使用，无需Star即可下载。

本次更新安装包，功能JAR沿用mstrade1.7.3和Trade Target1.4.2。22项前置判定检查、16个临时目录安装场景通过；本轮使用macOS上的PowerShell7，Windows原生文件夹选择界面尚未实机验证。

## English

New one-click Windows setup: extract the ZIP, run `开始安装.cmd`, and select your save. The installer checks and reuses suitable existing dependencies, installs missing files, and enables the Carpet scripts for that save. Run `安装含交易筛选.cmd` to also install the separate singleplayer trade filter. A working Fabric26.2/26.3 instance and Java25 are required; exit Minecraft first.

Updates appear on GitHub first; less frequently used forums may follow1–7days later. Installation logic passed22 dependency checks and16 disposable-fixture scenarios underPowerShell7 onmacOS; the native Windows folder picker still needs Windows validation. Gameplay JAR versions remainmstrade1.7.3 andTrade Target1.4.2.
