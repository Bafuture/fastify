# dep-guard-maven-plugin

在构建阶段拦截多模块/多项目协作中常见的依赖卫生问题：版本冲突、依赖不收敛、
动态版本导致的不可复现构建、以及不同构件中的重复类。所有检查在 `verify` 阶段完成，
可配置阈值，超阈值即以非零退出码使构建失败。

## 目标（goals）

| Goal | 说明 |
| --- | --- |
| `dep-guard:tree` | 自行遍历依赖图，输出规范化的依赖树文本（含传递依赖、optional 标记、provided/test 作用域推导、排除生效后的结果），冲突被覆盖的节点标注 `(omitted for conflict with X)`。 |
| `dep-guard:check` | 运行全部检查，输出文本报告 `dep-guard-report.txt`、JSON 报告 `dep-guard-report.json` 与依赖树 `dep-guard-tree.txt`（默认写入 `target/dep-guard/`），并按阈值门禁使构建失败。 |

## 配置示例

```xml
<plugin>
  <groupId>io.github.depguard</groupId>
  <artifactId>dep-guard-maven-plugin</artifactId>
  <version>1.0.0</version>
  <configuration>
    <!-- 依赖收敛：声明期望版本，出现其它（传递）版本即违规 -->
    <expectedVersions>
      <expectedVersion>com.google.guava:guava:33.2.1-jre</expectedVersion>
    </expectedVersions>
    <!-- 白名单：坐标（g:a 或 g:a:v，支持 * 通配）与类名 -->
    <whitelistCoordinates>
      <whitelistCoordinate>org.slf4j:*</whitelistCoordinate>
    </whitelistCoordinates>
    <whitelistClasses>
      <whitelistClass>module-info</whitelistClass>
      <whitelistClass>com.example.shaded.*</whitelistClass>
    </whitelistClasses>
    <!-- 门禁阈值：-1 表示不启用该项门禁；0 表示零容忍 -->
    <maxVersionConflicts>0</maxVersionConflicts>
    <maxConvergenceViolations>0</maxConvergenceViolations>
    <maxDynamicVersions>0</maxDynamicVersions>
    <maxDuplicateClassConflicts>0</maxDuplicateClassConflicts>
    <!-- 参与重复类扫描的作用域，默认 compile,runtime,provided -->
    <duplicateCheckScopes>
      <duplicateCheckScope>compile</duplicateCheckScope>
      <duplicateCheckScope>runtime</duplicateCheckScope>
    </duplicateCheckScopes>
  </configuration>
</plugin>
```

所有阈值参数也有对应的命令行属性，如 `-Ddepguard.maxVersionConflicts=0`；
`-Ddepguard.skip=true` 可整体跳过。

## 各检测项的判定规则

### 1. 依赖树解析
从当前工程的直接依赖出发，逐个读取构件描述符（POM）递归遍历。遍历时应用：

- **作用域传递表**（与 Maven 官方一致）：父作用域 compile → 子 compile/runtime 保留为
  compile/runtime，provided/test 被省略；父 provided → 子 compile/runtime 变为 provided；
  父 runtime → 子 compile/runtime 变为 runtime；父 test → 子 compile/runtime 变为 test。
- **optional**：工程自己声明的 optional 依赖正常包含并继续传递；依赖的依赖若为 optional，
  按 Maven 语义不再纳入。
- **排除（exclusions）**：沿引入路径累积，支持 `*` 通配。
- **dependencyManagement**：根工程的 dependencyManagement 会钉住传递依赖的版本/作用域
  （与 Maven 行为一致，这也是修复建议的机制）；中间 POM 自身的 dependencyManagement 用于
  补全未声明版本的子依赖。
- **循环依赖**：检测到环时标记 `(cyclic)` 并停止下钻。

### 2. 版本冲突检测
同一 `groupId:artifactId` 在图中出现多个版本即构成冲突。选中版本按 Maven「最近优先」
规则判定：距根工程路径最短者胜；深度相同时先声明者胜（按深度优先先序的声明顺序）。
报告列出每个版本出现的次数、完整引入路径，以及 `[selected]` / `[overridden]` 标记。

### 3. 依赖收敛与动态版本
- `expectedVersions` 中声明的坐标，若图中任何一次出现的版本不等于期望值，记一条收敛违规
  （含引入路径）。
- 声明版本为 `LATEST`、`RELEASE` 或版本范围（`[1.0,)`、`(1.0,2.0]` 等）时记一条动态版本
  违规——这类声明使构建结果随仓库内容变化，不可复现。插件会尝试将其解析为具体版本后继续
  遍历，解析失败则记录警告并跳过该子树。

### 4. 重复类检测
对每个「生效」（冲突裁决胜出）且作用域属于 `duplicateCheckScopes` 的构件解析其 jar，
索引全部 `.class` 条目（跳过 `META-INF/`）。同一全限定类名出现在 ≥2 个构件中即为重复；
对每个副本计算 SHA-256：

- hash 全部一致 → `[identical content]`（内容完全相同，通常为误报风险低的重复打包）；
- hash 不一致 → `[DIFFERENT implementations]`（实现不同，类路径顺序决定实际加载哪一个，
  是运行期诡异 bug 的常见根源）。门禁项 `maxDuplicateClassConflicts` 只统计这一类。

### 5. 报告与门禁
- 文本报告：人类可读，含依赖树、各项明细、修复建议、门禁结论。
- JSON 报告：机器可读，含 `summary` 计数、`gatePassed` 与全部明细，供 CI 消费。
- 门禁：任一已启用阈值（≥0）被超过即抛出 `MojoExecutionException`，Maven 以非零码退出。
- 白名单：`whitelistCoordinates` 命中（g:a 或 g:a:v，支持 `*`/`?`）的坐标不计入版本冲突与
  收敛违规；`whitelistClasses` 命中的类名不参与重复类判定。

### 6. 修复建议
对每个版本冲突，报告末尾生成可直接粘贴的 `dependencyManagement` 片段，将该坐标钉到
「最近优先」规则选中的版本上。粘贴后冲突消失（其它版本的出现会被根工程的管理版本覆盖）。

## 误报边界与已知限制

- **「最近优先」是近似**：本插件按「最短路径 + 声明顺序」静态推演 Maven 的冲突裁决，
  与 `mvn dependency:tree` 的裁决结果在常规工程一致；但 Maven 实际的裁决还受 profile
  激活、依赖的依赖中 dependencyManagement 的 import 作用域 BOM 等影响，极端工程下可能
  存在偏差。
- **运行期 classpath 顺序**：重复类检测能发现「实现不同」的风险，但无法预知容器/启动器
  实际的类加载顺序，因此不判断「实际会加载哪一份」。
- **可选依赖的传递闭包**：optional 依赖的传递依赖按 Maven 语义不纳入分析；若运行环境
  （如某些容器）实际会加载它们，本插件无法感知。
- **动态版本解析依赖仓库元数据**：`LATEST`/版本范围需要 `maven-metadata.xml`；离线或
  元数据缺失时记录警告并跳过该子树（违规本身仍会被报告）。
- **system 作用域 / 非 jar 打包**：system 作用域与无法解析为 jar 的构件不参与重复类扫描。
- **shade/relocations**：已 shade 的构件按最终 jar 内容扫描，重定位（relocation）前的
  原始坐标不可见。
- **性能**：完整遍历未做全局去重，超大型依赖图（>2 万节点）会截断并给出警告。

## 测试

`mvn -q verify` 一条命令跑通全部测试。集成测试基于 maven-plugin-testing-harness，
覆盖四个场景：版本冲突、重复类检测、白名单生效、门禁失败。测试所需的样例构件
（`com.acme:lib-x/lib-y/lib-common/lib-dup-a/lib-dup-b`）由 `TestRepositoryBuilder`
在测试启动时生成到 `target/test-repo/`（jar 与 POM 均为程序生成），全程不访问网络。
