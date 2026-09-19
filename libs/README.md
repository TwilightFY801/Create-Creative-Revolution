# libs/ — 编译期依赖（第三方，未随本仓库分发）

本目录**不包含**任何第三方 jar。要编译本模组，请自行把下列 jar 放进本目录：

| 文件 | 说明 |
| --- | --- |
| `create.jar` | 机械动力 Create |
| `ponder.jar` | Create 的 Ponder 库 |
| `flywheel.jar` | Flywheel 渲染库 |
| `registrate.jar` | Registrate 注册库 |
| `aeronautics.jar` | Create 航空学（bundled） |
| `offroad.jar` | 航空学的 Offroad 子模块（车轮悬架 / 轮胎） |
| `sable.jar` | Sable 物理库 |
| `sable-companion.jar` | Sable 的 companion 公共库（从 sable.jar 的 `META-INF/jarjar/` 中解出） |
| `simulated.jar` | 航空学的 Simulated 子模块 |
| `veil.jar` | Veil（Sable 内置依赖） |

> 这些 jar 仅用于**编译**（`compileOnly`），不会被打进本模组的 jar。
> 它们各自归其作者所有，请遵守各自授权；本仓库不再分发它们。

`build.gradle` 通过 `flatDir { dirs 'libs' }` 引用这些文件。
