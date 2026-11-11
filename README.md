# Fast Scroll — IDEA 滚轮加速插件

按住修饰键（默认 **Alt / Option**）滚动鼠标滚轮时，按可配置倍数加速；不按修饰键时完全保持 IDE 原速。

- 加速倍数可调：1 ~ 20 倍，默认 3 倍
- 修饰键可选：Alt / Option、Ctrl / Control、Shift、Meta / Command
- 设置改完立即生效，不需要重启
- 只作用于 IDE 内部，不影响系统其它程序；不修改任何 IDE 原生设置

## 构建

```bash
./gradlew buildPlugin
```

产物：`build/distributions/fast-scroll-plugin-<版本>.zip`

首次构建会下载 IntelliJ 平台依赖（约 800MB，走 JetBrains 源，实测 3MB/s）。
Gradle 本身默认走腾讯云镜像（见 `gradle/wrapper/gradle-wrapper.properties`）。

## 安装

1. IDE 里 `Settings / Preferences → Plugins → 右上齿轮 → Install Plugin from Disk...`
2. 选 zip，**完全退出 IDE（⌘Q）后重新打开**

> 第 2 步必须是真正的退出重启。本插件对 IDE 来说属于"可动态加载"插件，热加载时不会提示重启，
> 而 `postStartupActivity` 只在启动时执行，热加载会导致监听没注册上（v1.0.0 就栽在这里）。

## 使用

- 在编辑器 / 控制台 / 表格 / 列表里按住 `Option`（macOS）或 `Alt`（Windows/Linux）滚动滚轮
- 打开设置有两种方式：
  - 菜单 `Tools → Fast Scroll Settings…`
  - 设置树顶层的 **Fast Scroll**（不放在 Tools 分组下：不同 IDE 版本分组 id 有差异，放顶层保证一定能看到）
  - 设置页内容：启用开关、修饰键、加速倍数（步长 0.5）

## 调试 / 本地跑起来看效果

```bash
./gradlew runIde
```

会启动一个带本插件的沙箱 IDE，直接在里面试手感，不用装到本机。

## 实现原理

插件的核心在 `WheelAcceleratorService`：

1. 用 `Toolkit.addAWTEventListener(..., MOUSE_WHEEL_EVENT_MASK)` 注册**全局**滚轮监听。AWT 的全局监听器在事件派发给目标组件**之前**回调，这是整个方案成立的前提。
2. 命中「修饰键按下 + 倍数 > 1」时，按**格数**折算：原事件自己已经滚了 `rotation` 格，倍数多出来的 `rotation × (倍数 - 1)` 格压成**一个**事件补发，沿用原事件的 `scrollAmount`、滚动方向，并**清掉修饰键**。
3. 补发事件用 `SyntheticWheelEvent` 子类标记，监听器看到它就放行，避免自我放大形成无限递归。

### 为什么补发的是一个事件，而不是 N 个

IDEA 编辑器的滚轮不是直接调 `JScrollBar.setValue`，而是走
`com.intellij.ui.scroll.MouseWheelSmoothScroll`（2023.3 起的实现，可反编译平台 jar 核对）：

```java
JScrollBar bar = SmoothScrollUtil.getEventScrollBar(e);
int value = bar.getValue();
int delta = getDelta(bar, e, animator.myTargetValue);
if (delta == 0) return;
animator.start(value, value + delta, bar::setValue, ...);
e.consume();
```

也就是每次事件都会起一段**动画**。短时间内连塞多个事件时，后一个事件是拿"动画还没把滚动条挪走时的当前值"重新 `start()` 的，
几段动画互相覆盖，净效果可能只有一格、甚至为零（v1.0.0 实测就是"按住 Option 完全不滚"）。
一个事件带满 delta 只有一段动画，滚动量精确，也不会有事件风暴。

### 另外两个坑

- JDK 的 `MouseWheelEvent` 没有 12 参构造器，带 `preciseWheelRotation` 的是 **14 参**那个（含绝对坐标）；11 参版本会把 precise 退化成 `wheelRotation`。
- 状态文件不能叫 `fast-scroll.xml`：市场上另一个同类插件用的正是这个文件名，组件名也叫 `FastScrollSettings`，撞名会互相覆盖配置。本插件用 `qg-fast-scroll.xml` / `QGFastScrollSettings`。

## 已验证的部分

| 项 | 结果 |
| --- | --- |
| `./gradlew buildPlugin` | 构建成功 |
| `./gradlew verifyPlugin` | 对 IC-233.14475.28 判定 **Compatible**，无 deprecated / internal API 告警 |
| 滚动放大算法 | 真实 `JScrollPane` + 真实 `EventQueue`，向下/向上、1~3 格、`scrollAmount=1/3`、倍数 1/2/3/20 全部符合 `rotation × 倍数` |

**没验证的部分**：真实 IDE 界面里按住 Option 的手感。可看日志确认：

```
grep "滚轮加速" ~/Library/Logs/JetBrains/<IDE>/idea.log
```

正常应能看到启动一条 `[滚轮加速-启动] 监听已注册, ...`，以及每次命中一条 `[滚轮加速-命中] ...`
（只打前 30 次，避免刷屏）。

## 目录

```
src/main/java/com/qg/fastscroll/
  TriggerModifier.java          修饰键枚举 + 按下判定
  FastScrollSettings.java       配置持久化（qg-fast-scroll.xml）
  SyntheticWheelEvent.java      补发事件标记类
  WheelAcceleratorService.java  核心：监听 + 补发
  FastScrollStartupActivity.java 启动时注册监听
  FastScrollConfigurable.java   设置页
  OpenSettingsAction.java       Tools 菜单入口
src/main/resources/META-INF/plugin.xml
```

## 已知限制

- 加速只作用于垂直滚动；Shift+滚轮的横向滚动不加速。
- 倍数取小数（如 2.5）时按格累积，会出现 2 格 / 3 格交替，长期平均正确。
- 如果某个组件自己吃掉了滚轮事件且不走标准滚动路径，加速对它无效。
- 插件基于 2023.3 平台 API 编译（`sinceBuild=233`），2023.3 及以上版本均可加载。
