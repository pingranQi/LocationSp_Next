# Room 数据库迁移状态

当前数据库 schema version：**11**。

## 已有迁移链

- 3 → 4：location_records 增加 placeName、remark
- 6 → 7：增加所选 Wi-Fi、蓝牙、基站字段
- 7 → 8：增加位置与时间索引
- 8 → 9：增加 collection_routes 与关联字段
- 9 → 10：增加 trajectories
- 10 → 11：saved_routes 增加 source

## 未知历史版本

仓库可追溯的 schema 变更记录没有提供 4 → 5、5 → 6 的 SQL 与 Room schema 快照，因此不能安全猜测列、索引或外键结构。当前策略是不使用 destructive fallback：Room 遇到缺失迁移路径会明确失败，而不是静默删除用户地点、无线环境和轨迹数据。

版本 3 或 6–10 升级到 11 有已登记迁移链；版本 4/5 的无损升级仍待取得对应旧版 schema 后补齐。升级前应使用 App 内数据导出功能备份。不要把 fallbackToDestructiveMigration() 加回去作为兼容修复。

## 后续迁移约束

1. exportSchema 已启用，KSP 将 schema 输出到 core-data/schemas。
2. 每次修改实体、索引或外键时，提交新 schema JSON。
3. 新增 Migration(N, N+1)，同时覆盖所有仍受支持的起始版本。
4. 使用真实旧版 schema 做迁移测试，校验表结构、索引、外键和关键数据。
