# UniWork

UniWork 是面向 Java 8、传统 Java Web、Spring Boot 和 Spring Cloud 项目的统一企业通信 SDK。

项目当前处于 `0.1.0-SNAPSHOT` 基础阶段。首批代码已经确定并实现：

- Java 8 框架无关核心 API
- `uniwork.yml`、`uniwork.yaml`、`uniwork.properties` 配置加载
- 环境变量占位符，例如 `${WECOM_SECRET}` 和 `${NAME:default}`
- 类型安全的渠道扩展 SPI
- 企业微信、钉钉、飞书、邮箱、短信的固定 API 入口
- 可运行、可测试的医院 OA 自定义渠道示例

真实企业微信、钉钉、飞书、邮箱和短信实现将在后续模块中逐步加入；当前核心不会伪装成已经可以调用这些平台。

## Java 版本

UniWork Core 的编译目标是 Java 8。项目可以使用更高版本 JDK 构建，但构建过程会检查代码没有调用 Java 8 之后才出现的 API。

## Maven 坐标

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.idongxia.uniwork</groupId>
            <artifactId>uniwork-bom</artifactId>
            <version>0.1.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

```xml
<dependency>
    <groupId>com.idongxia.uniwork</groupId>
    <artifactId>uniwork-core</artifactId>
</dependency>
```

## 预期的内置渠道调用

渠道实现加入后，普通用户的调用形式保持为：

```java
uniWork.wecom().sendContent(userId, content);
uniWork.dingtalk().sendContent(userId, content);
uniWork.feishu().sendContent(userId, content);
uniWork.mail().sendContent(email, title, content);
uniWork.sms().sendContent(mobile, content);
```

协作平台还会提供：

```java
String loginUrl = uniWork.wecom().loginUrl();
UniWorkUser user = uniWork.wecom().login(code);
UniWorkUser user = uniWork.wecom().getUser(userId);
```

## Java Web 配置

在 classpath 中建立 `uniwork.yml`：

```yaml
uniwork:
  hospital-oa:
    endpoint: https://oa.example.com/api/messages
    app-id: purchase-system
    secret: ${HOSPITAL_OA_SECRET}
```

加载一次：

```java
UniWork uniWork = UniWork.load();
```

旧项目也可以使用 `uniwork.properties`：

```properties
uniwork.hospital-oa.endpoint=https://oa.example.com/api/messages
uniwork.hospital-oa.app-id=purchase-system
uniwork.hospital-oa.secret=${HOSPITAL_OA_SECRET}
```

## 自定义医院 OA 渠道

完整案例位于 `uniwork-example-hospital-oa`。扩展包提供自己的渠道接口和 Provider，并通过 Java `ServiceLoader` 自动发现。

业务调用不依赖字符串别名：

```java
uniWork.platform(HospitalOaChannel.class)
        .sendContent("EMP10086", "采购项目等待审批");
```

自定义渠道只需要实现两个公共发送方法：

```java
SendResult sendContent(String receiver, String content);

SendResult sendContent(String receiver, String title, String content);
```

## 构建

```bash
./mvnw verify
```

## License

Apache License 2.0
