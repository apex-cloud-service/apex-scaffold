# MeilisearchAdapter 快速修复指南

> 基于实际PO类结构的字段映射

## 关键字段映射

### BasePO (所有PO的父类)
```java
private Long id;
private LocalDateTime createTime;  // 不是createdAt
private LocalDateTime updateTime;  // 不是updatedAt
private Integer version;
private Integer deleted;
```

### UserPO
```java
private String username;
private String phone;           // 有
// 注意：没有 email、nickname 字段
private Long deptId;
private String tenantId;        // String类型，不是Long
private Integer status;
```

### RolePO
```java
private String name;            // 不是roleName
private String code;            // 不是roleCode
private String description;
private String tenantId;        // String类型
private String dataScope;
private Integer status;
```

### MenuPO
```java
private String name;            // 不是menuName  
private String path;
private String component;
private String menuType;
private String icon;
private Integer sort;
private Integer status;
```

### DeptPO
```java
private String name;            // 不是deptName
private String code;            // 不是deptCode
private String leader;
private String phone;
private String tenantId;        // String类型
private Integer sort;
private Integer status;
```

### DictDataPO
```java
private String dictLabel;
private String dictValue;
private String dictType;
private Integer dictSort;       // 不是sort
private String cssClass;
private Integer status;
// 注意：可能没有 createTime/updateTime（需确认）
```

### ConfigPO
```java
private String configName;
private String configKey;
private String configValue;
private String configType;
// 注意：可能没有 createTime/updateTime（需确认）
```

## 快速修复方案

由于字段差异较大，建议采用以下方案之一：

### 方案1：简化版（推荐，快速可用）

只索引确定存在的字段，其他字段用空字符串或默认值：

```java
case "user" -> {
    UserPO user = userMapper.selectById(Long.parseLong(resourceId));
    if (user == null || user.getStatus() == 0) yield null;
    
    yield Map.of(
        "id", "user_" + resourceId,
        "resourceType", "user",
        "resourceId", resourceId,
        "title", user.getUsername(),
        "subtitle", "",  // UserPO没有nickname
        "description", user.getPhone() != null ? maskPhone(user.getPhone()) : "",
        "status", user.getStatus(),
        "tenantId", user.getTenantId() != null ? user.getTenantId() : "0",
        "createdAt", toTimestamp(user.getCreateTime()),  // 注意：createTime
        "updatedAt", toTimestamp(user.getUpdateTime()),  // 注意：updateTime
        "extra", Map.of("phone", maskPhone(user.getPhone()))
    );
}
```

### 方案2：查询关联数据（完整版，需要更多工作）

通过JOIN或多次查询获取关联字段（如部门名称、昵称等），但会增加复杂度。

## 最小可用版本（立即可编译）

由于时间紧迫，建议先删除MeilisearchAdapter中的所有TODO方法实现，改为返回简单的测试数据，确保编译通过后再逐步完善：

```java
private Map<String, Object> fetchResourceDocument(String resourceType, String resourceId) {
    // 临时实现：仅返回基本结构，确保编译通过
    return Map.of(
        "id", resourceType + "_" + resourceId,
        "resourceType", resourceType,
        "resourceId", resourceId,
        "title", "测试标题",
        "subtitle", "",
        "description", "",
        "status", 1,
        "tenantId", "0",
        "createdAt", System.currentTimeMillis(),
        "updatedAt", System.currentTimeMillis(),
        "extra", Map.of()
    );
}

private List<String> fetchAllResourceIds(String resourceType) {
    // 临时实现：返回空列表
    return Collections.emptyList();
}

private List<String> fetchIncrementalResourceIds(String resourceType, Long sinceTimestamp) {
    // 临时实现：返回空列表
    return Collections.emptyList();
}
```

## 下一步建议

1. **立即修复**：使用最小可用版本，确保编译通过
2. **验证启动**：确保应用能正常启动
3. **逐步完善**：根据实际PO字段逐个完善资源类型的索引逻辑

鉴于时间紧迫和字段差异较大，建议优先保证系统可用性，索引字段映射可以后续优化。
