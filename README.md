# MS Tools · 让假人为你的生存档打工

**开机器、搬物资、跑交易链。重复操作交给假人，你继续建造。**

![Fabric](https://img.shields.io/badge/Loader-Fabric-dbba79)
![Minecraft](https://img.shields.io/badge/Minecraft-26.2%20%7C%2026.3-62b47a)
![Java](https://img.shields.io/badge/Java-25-orange)
![License](https://img.shields.io/badge/License-MIT-blue)

[下载与安装](https://github.com/MIHUKUAKU/ms-tools/releases/latest) · [全部命令](docs/全部命令.md) · [安装说明](docs/安装说明.md) · [测试记录](docs/测试报告.md) · [反馈问题](https://github.com/MIHUKUAKU/ms-tools/issues)

我一开始只是想用一个短指令开关和平机，不想每次输入假人名字、坐标和朝向。后来干脆把搬东西、装潜影盒、合成和村民交易也接了进来，现在把自己这套生存工具整理出来分享。

## 一套工具，两个独立模组

**MS 假人工具**用 `/ms` 控制 Carpet 假人，负责实际操作和购买已有交易。

**Trade Target 刷交易筛选**是另一个模组，在村民交易界面里寻找符合条件的交易，命中就停，**不会自动购买**。可以只装一个，也可以一起装。

## 把这些事交给假人

| 你想做的事 | MS 帮你怎么做 |
| --- | --- |
| 一键调用机器旁的假人 | 存好坐标和朝向，用编号 `run` / `walk` / `keep` 调用 |
| 把仓库里的物资带回来 | `fetch` 取面前箱子，`fetchall` 沿箱子逐列扫，`send` 回到指定玩家 |
| 从附近箱子里找指定物品 | `collect` 按主手物品收集，支持从潜影盒里取出，优先同类装盒 |
| 把材料变成打包用的空盒 | `craftbox` 合成潜影盒，材料箱和成品箱可以分开 |
| 让假人跑村民交易所 | `trade` 按主手物品购买，自动拆绿宝石块；交易链 `keep` 等补货再交易 |
| 随时调整假人的位置和视角 | 移动指定格数、抬头低头、转向，完成后看状态反馈 |

还有持续攻击、持续右键、在线假人查询、丢出物品、清空背包、幻翼生成开关，以及基岩/末地传送门框架点破开关。完整 **22 组主指令** 和参数都在 [命令手册](docs/全部命令.md)。

### 刷到想要的交易，而不是一直点刷新

独立刷交易筛选支持物品、附魔、药水效果、价格范围，以及多个交易序号：

- 内置 **331 钻石镐、附魔书、药水箭** 预设，中英文名称都能用。
- 可选基础价或当前价、等级恰好或至少、是否允许额外附魔/效果。
- 多个序号中任意一个命中就停，包含 Visible Traders 的高级交易预览。
- 可以手动停止，关闭交易窗口也会停止。只负责筛选，不替你买东西。

## 三步开始

1. 在 [Releases](https://github.com/MIHUKUAKU/ms-tools/releases/latest) 下载 `MS-Tools-1.7.3-Windows-Installer.zip`，退出游戏并完整解压。
2. 双击 `installer/Install.cmd`，选 PCL 的 Fabric 版本文件夹、安装模式和存档。**前置自动下载，旧文件自动备份，预设和交易链保留。**
3. 重进存档，输入 `/ms`。刷交易筛选的入口在村民交易窗口。

不用安装器也可以，见 [手动安装与排错说明](docs/安装说明.md)。Minecraft、Fabric Loader 和 Java 仍需要先在启动器里装好。

### 先试一个预设

```text
/ms add 1 alex 100.5 70 100.5 0 0 overworld
/ms keep 1
/ms list 1
/ms run 1
```

坐标是示例，请换成自己的。录入时可按 Tab 补全坐标和朝向。`keep` 只上线；`run` 右键后快速退出；想给机器留一点启动时间，用 `walk`。

## 版本与前置

目前适配 **Fabric 26.2 / 26.3**，需要 Java 25、Fabric Loader >= 0.19.3。

| 功能 | 前置 |
| --- | --- |
| MS | 对应游戏版本的 Carpet，以及四个 Scarpet 脚本和 `mstrade` |
| 独立刷交易 | Fabric API、Trade Cycling >= 1.0.21、Visible Traders >= 2.5.0 |

不卡死第三方前置的小版本，但必须选对应 Minecraft 版本的文件。Gugle Carpet Addition 的假人背包界面可选，不是必须装的。

安装器配置好 Carpet 权限和脚本自动加载后，普通玩家使用 `/ms` **不需要 OP**，纯生存档不用先手动输入 `/script load`。联机时请注意权限，这些工具不适合直接开放给不信任的玩家。

## 使用前看一眼

- 自动移动适合提前整理好的平坦通路，不是通用寻路；避免障碍、悬崖和岩浆。
- 刷交易筛选目前只支持**单人存档**。村民需要绑定工作站且还没进行过交易；交易输入格和鼠标持物须为空。先等高级预览加载，再选序号。
- `clear` 是直接清空假人背包，不可撤销。特殊方块点破会直接移除方块，请谨慎开启。
- 两个版本均已编译通过；26.3 做过隔离运行和实际刷交易测试，26.2 目前确认了客户端初始化。不是所有功能和边界条件都测过，详见 [测试记录](docs/测试报告.md)。其它 Minecraft 版本没有验证。

## 源码与反馈

完整源码在 Release 附件的 `MS-Tools-1.7.3-Source.zip`，解压就是 Gradle 项目。想自己编译或改功能，见 [开发与兼容](docs/开发与兼容.md)。

遇到问题可以开 [Issue](https://github.com/MIHUKUAKU/ms-tools/issues)，带上游戏版本、相关模组版本、执行的指令和报错片段就行，不需要上传整个存档。日志里若有账号信息，先删掉。

如果这套工具也帮你省下了重复操作，欢迎点个 Star。
