# dep-guard-maven-plugin

在**构建阶段**拦截多模块/多项目间常见的依赖卫生问题：版本冲突、依赖收敛破坏、
动态版本导致的不可复现构建、以及不同构件中的重复类。发现问题时可输出文本/JSON 报告、
给出可直接粘贴的 `dependencyManagement` 修复建议，并按配置阈值让构建失败（非零退出码）。

## 目标（Goals）

| Goal | 默认阶段 | 说明 |
|------|----------|------|
| `dep-guard:check` | `verify` | 运行全部检查，输出报告并按门禁配置失败构建 |
| `dep-guard:tree` | （不绑定） | 打印规范化的依赖树文本，并写入 `target/dep-guard/dep-guard-tree.txt` |

## 快速开始

```xml
<plugin>
  <groupId>io.github.depguard</groupId>
  <artifactId>dep-guard-maven-plugin</artifactId>
  <version>1.0.0</version>
  <configuration>
    <!-- 门禁：不允许任何版本冲突 -->
    <failOnVersionConflict>true</failOnVersionConflict>
    <failOnDuplicateClass>true</failOnDuplicateClass>
    <failOnDynamicVersion>true</failOnDynamicVersion>

    <!-- 依赖收敛：声明期望的版本集合（g:a:v 精确匹配；g:a 表示任意版本但必须声明） -->
    <expectedVersions>
      <expectedVersion>com.fasterxml.jackson.core:jackson-databind:2.17.2</expectedVersion>
      <expectedVersion>org.slf4j:slf4j-api</expectedVersion>
    </expectedVersions>

    <!-- 白名单：坐标或类名，支持 * 通配 -->
    <ignoredDependencies>
      <ignoredDependency>commons-logging:commons-logging</ignoredDependency>
      <ignoredDependency>com.acme:legacy-*</ignoredDependency>
    </ignoredDependencies>
    <ignoredClasses>
      <ignoredClass>org.apache.commons.logging.*</ignoredClass>
    </ignoredClasses>
  </configuration>
  <executions>
    <execution>
      <goals><goal>check</goal></goals>
    </execution>
  </executions>
</plugin>
```

## 配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `skip` (`depguard.skip`) | `false` | 跳过全部检查 |
| `outputDirectory` | `${project.build.directory}/dep-guard` | 报告输出目录 |
| `expectedVersions` | 空 | 期望版本集合；非空时启用收敛检查 |
| `ignoredDependencies` | 空 | 版本冲突白名单（`g:a` 或 `g:a:v`，支持 `*`） |
| `ignoredClasses` | 空 | 重复类白名单（类全限定名，支持 `*`） |
| `failOnVersionConflict` | `false` | 版本冲突数超过 `maxVersionConflicts` 时构建失败 |
| `maxVersionConflicts` | `0` | 容忍的版本冲突数量阈值 |
| `failOnDuplicateClass` | `false` | 重复类数超过 `maxDuplicateClasses` 时构建失败 |
| `maxDuplicateClasses` | `0` | 容忍的重复类数量阈值 |
| `failOnConvergence` | `false` | 存在收敛违规时构建失败 |
| `failOnDynamicVersion` | `false` | 存在动态版本时构建失败 |

门禁触发时插件抛出 `MojoExecutionException`，Maven 以非零退出码结束构建。

## 各检测项的判定规则

### 1. 依赖树解析（`tree` / 报告中的 `[Dependency tree]`）
- 通过 Aether（`RepositorySystem`）对当前工程做完整的依赖收集与解析，覆盖传递依赖、
  可选依赖、`provided`/`test` 作用域，并遵循 `<exclusions>` 排除规则。
- 插件在**会话副本**上开启 `aether.conflictResolver.verbose`，使被冲突裁决剪掉的节点
  仍然保留在图中（不修改用户自己的构建会话）。
- 输出形如 `g:a:packaging:version:scope`，可选依赖标注 `(optional)`，被冲突覆盖的节点
  标注 `(omitted for conflict with X)`，已展开过的共享节点以 `(*)` 折叠。

### 2. 版本冲突检测
- 以 `groupId:artifactId:classifier:extension` 为冲突键，同一键出现 ≥2 个不同版本即判定冲突。
- 「最终选中版本」直接取 Maven 冲突裁决器（最近优先，nearest-wins）保留的节点版本，
  与 Maven 实际生效行为一致，而不是插件自行猜测。
- 每个被覆盖版本都会列出完整引入路径（从工程根到引入它的父依赖链），同一节点最多
  记录 5 条路径。

### 3. 依赖收敛与动态版本
- **收敛**：`expectedVersions` 非空时，解析后的每个构件（冲突胜者）都必须出现在集合中；
  未声明的坐标报「not declared」，版本不在允许集合内报「not one of the expected versions」。
- **动态版本**：扫描本工程 POM 的 `<dependencies>`/`<dependencyManagement>`，以及**每个
  已解析构件自身的 POM 描述符**（因此传递依赖里声明的范围也能发现），命中 `LATEST`、
  `RELEASE` 或版本区间（含 `[`、`(`）即判为不可复现。

### 4. 重复类检测
- 扫描解析后的所有 jar（冲突胜者），以类全限定名分组，按条目内容的 SHA-256 比对。
- 同一类出现在 ≥2 个不同 `groupId:artifactId` 中即判定重复：
  - 所有副本 hash 一致 → 标注 `[identical bytecode]`（通常是 shading/拷贝，风险低）；
  - 存在不同 hash → 标注 `[DIFFERENT implementations]`（运行期行为取决于类路径顺序，高危）。
- 跳过 `META-INF/` 下的类、`module-info.class` 与 `package-info.class`。

### 5. 报告与门禁
- 文本报告打印到日志并写入 `dep-guard-report.txt`；机器可读报告写入
  `dep-guard-report.json`（含 summary 计数与全部明细）。
- 门禁计数均基于**白名单过滤之后**的数量。

### 6. 修复建议
- 对每个版本冲突生成固定到「当前选中版本」的 `<dependencyManagement>` 片段，
  可直接粘贴进 pom.xml（报告的 `[Suggested dependencyManagement]` 一节）。

## 误报边界与已知限制

- **重复类 ≠ 一定出错**：`identical bytecode` 的情况（如 shade 后未重定位但内容一致）
  通常无害，可用 `ignoredClasses` 或 `maxDuplicateClasses` 阈值放行。反之，内容不同的
  重复类只有在类路径顺序变化时才会真正爆雷，但插件按「存在即报」处理。
- **版本冲突 ≠ 不兼容**：报告只说明 Maven 覆盖了某些版本，不判断 API 兼容性；
  确认无害的坐标请用 `ignoredDependencies` 白名单。
- **动态版本检测基于 POM 文本**：`LATEST`/`RELEASE`/区间能被识别；通过属性
  `${revision}` 等间接引入的浮动版本不在检测范围（CI-friendly 版本本身是受控的）。
- **描述符不可读时跳过**：某个传递构件的 POM 缺失或损坏时，其动态版本扫描会被跳过
  （debug 日志可见），不会导致构建失败。
- **收敛检查只作用于冲突胜者**：被覆盖版本不参与 `expectedVersions` 比对
  （它们不进入最终类路径）；如需连被覆盖版本一起约束，请配合版本冲突门禁使用。
- **jar 扫描范围**：仅扫描解析成功且文件存在的 `.jar` 构件；`pom` 类型、
  未解析成功的构件不参与重复类检测。所有作用域（含 `provided`/`test`）都会扫描，
  因为它们同样会进入对应类路径。
- **超大依赖图**：遍历设有访问上限（10 万节点）与每节点路径上限（5 条），
  极端庞大的图中路径列表可能被截断，但冲突判定本身不受影响。

## 测试

```bash
mvn -q verify
```

集成测试基于 `maven-plugin-testing-harness`，覆盖：版本冲突（最近优先 + 引入路径）、
重复类检测（hash 一致 vs 实现不同）、白名单生效、门禁失败、收敛与动态版本、依赖树输出。
测试所需的全部样例构件（jar + pom + 本地仓库元数据）由 `TestRepositoryBuilder` 在测试
启动时生成到 `target/test-repo`，不依赖网络下载任何额外构件。
