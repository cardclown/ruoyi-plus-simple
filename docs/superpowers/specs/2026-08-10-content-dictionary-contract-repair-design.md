# 文章字典契约与模块接入修复设计

## 背景

`ContentArticleDictionaryService` 使用 `DictService#getDictData()` 返回的 `DictDataDTO` 读取字典编码、排序和样式，但当前公共 DTO 只包含标签、键值、默认值和备注，导致 `ruoyi-content` 编译时找不到对应访问器。同时，`ruoyi-content` 尚未加入 Maven 业务模块聚合，也未成为 `ruoyi-admin` 的运行依赖，因此常规聚合构建没有覆盖文章模块。

## 修复目标

- 在公共 `DictDataDTO` 中补齐 `dictCode`、`dictSort`、`cssClass`、`listClass`，字段名称和类型与 `SysDictDataVo` 保持一致。
- 保持 `SysDictTypeServiceImpl#getDictData()` 现有 `BeanUtil.copyToList()` 数据流，由同名属性自动完成复制。
- 将 `ruoyi-content` 加入 `ruoyi-modules` 聚合。
- 在根 POM 的依赖管理中声明 `ruoyi-content:${revision}`，并让 `ruoyi-admin` 依赖该模块，使文章 Controller、Service 和 Mapper 进入后台应用。
- 不修改前端，不改变文章表结构和字典业务规则。

## 方案选择

采用扩展现有公共字典 DTO 的方案。文章数据保存的是系统字典数据主键，不能改用 `dictValue` 代替；另建一套文章专用系统字典查询接口会重复现有能力并扩大改动。

## 数据流

1. `SysDictTypeServiceImpl` 查询 `SysDictDataVo`。
2. `BeanUtil.copyToList()` 按同名字段转换为 `DictDataDTO`。
3. `ContentArticleDictionaryService` 使用字典编码校验分类和标签，并转换展示字段为 `ContentArticleDictOptionVo`。
4. `ruoyi-admin` 通过 Maven 依赖加载 `ruoyi-content`，Spring 扫描其后端组件。

## 测试与验证

- 修复前，直接编译 `ruoyi-content` 必须稳定复现缺少 4 类访问器的 6 个编译错误。
- 修复后，运行 `ContentArticleDictionaryServiceTest`，确认编码校验和展示字段转换通过。
- 运行 `ruoyi-content` 全部测试。
- 从根工程执行 `ruoyi-admin` 聚合构建，确认 Maven 会自动构建并打包 `ruoyi-content`。
- 复核提交只包含公共 DTO、三个 Maven POM、测试或本设计文档，不带入当前工作区其他 OSS 改动。

## 交付范围

本次只修复 `company-website-backend` 当前文章模块。除非另行要求，不同步到 `5.X`，也不修改前端。
