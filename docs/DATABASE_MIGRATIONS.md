# Room 数据库迁移状态

当前数据库 schema version：**11**。

## 已验证来源的迁移链

- 6 → 7：增加所选 Wi-Fi、蓝牙、基站字段
- 7 → 8：增加位置与时间索引
- 8 → 9：增加 collection_routes 与关联字段
- 9 → 10：增加 trajectories
- 10 → 11：saved_routes 增加 source

版本 **6–11** 有可追溯的迁移实现，可按上述连续迁移链升级。

## 不支持的历史版本

- v1/v2：没有可验证的 Room schema 或完整建库 SQL。
- v3：仓库只保留了 3 → 4 的迁移实现，但缺少 4 → 5 与 5 → 6 的历史 schema/SQL。
- v4/v5：缺少可追溯的历史 schema 与迁移 SQL。

因此当前版本**不注册猜测性的 4 → 5、5 → 6 迁移**。从 v1–v5 升级到 v11 会因缺少迁移路径而明确失败；不会静默删除用户数据，也不会把当前实体结构冒充为旧版数据库结构。请在升级前使用应用内导出功能备份数据。

要重新支持 v1–v5，必须先从对应历史 APK、用户备份或发布构建产物中取得真实 SQLite schema（包括列默认值、索引、外键），再提交对应 schema fixture 和 Room MigrationTestHelper 测试。没有证据时不增加迁移 SQL。

## 后续迁移约束

1. exportSchema 保持启用；每次实体、索引或外键变化都提交对应 schema JSON。
2. 新增 Migration(N, N+1) 时，必须有旧版本 schema 作为依据。
3. 使用真实旧库 fixture 执行迁移测试，校验 Room schema、索引、外键及关键数据完整性。
4. 禁止使用 fallbackToDestructiveMigration() 作为兼容修复。
