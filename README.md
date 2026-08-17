# UniWork

UniWork 是面向中国大陆企业应用的统一 Java 通信 SDK。它用一套简洁 API 接入企业微信、钉钉、飞书，并允许医院 OA、内部短信网关等自有平台按相同方式扩展。

当前版本：`0.1.0-SNAPSHOT`

## 主要能力

- Java 8 编译目标，兼容传统 Java Web、Spring Boot 和 Spring Cloud 项目
- 企业微信、钉钉、飞书真实 HTTP API 适配
- 文本消息、带标题文本、基础跳转卡片
- OAuth 授权地址、授权码登录、用户信息查询
- 应用访问令牌本地缓存、提前刷新和失效后单次重试
- `uniwork.yml`、`uniwork.yaml`、`uniwork.properties` 配置
- `${ENV_NAME}` 和 `${ENV_NAME:default}` 环境变量占位符
- 一个公开异常类型 `UniWorkException`
- Java `ServiceLoader` 类型安全扩展，不使用字符串平台别名
- 中文为主、英文补充的双语 JavaDoc

## 最简单的 Maven 依赖

同时使用企业微信、钉钉和飞书时，只需引入：

```xml
<dependency>
    <groupId>com.idongxia.uniwork</groupId>
    <artifactId>uniwork-all</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

只使用一个平台时，也可以单独选择：

```xml
<artifactId>uniwork-wecom</artifactId>
<artifactId>uniwork-dingtalk</artifactId>
<artifactId>uniwork-feishu</artifactId>
```

## 一份配置完成三平台接入

在 classpath 中建立 `uniwork.yml`：

```yaml
uniwork:
  wecom:
    corp-id: ${WECOM_CORP_ID}
    agent-id: ${WECOM_AGENT_ID}
    secret: ${WECOM_SECRET}
    redirect-uri: https://app.example.com/login/wecom/callback

  dingtalk:
    client-id: ${DINGTALK_CLIENT_ID}
    client-secret: ${DINGTALK_CLIENT_SECRET}
    agent-id: ${DINGTALK_AGENT_ID}
    redirect-uri: https://app.example.com/login/dingtalk/callback

  feishu:
    app-id: ${FEISHU_APP_ID}
    app-secret: ${FEISHU_APP_SECRET}
    redirect-uri: https://app.example.com/login/feishu/callback
```

钉钉也兼容旧名称 `app-key` 和 `app-secret`。密钥应放在环境变量或部署平台的密钥管理中，不要提交到 Git。

平台后台还需要完成对应授权：企业微信要保证自建应用的可见范围和通讯录权限；钉钉要开通工作通知以及登录所需的个人权限；飞书要启用机器人，并开通 `im:message:send_as_bot` 和所需通讯录权限。SDK 无法绕过平台后台的权限和数据可见范围。

## 业务调用

传统 Java Web 项目启动时加载一次：

```java
UniWork uniWork = UniWork.load();
```

发送文本：

```java
uniWork.wecom().sendContent(userId, "采购项目等待审批");
uniWork.dingtalk().sendContent(userId, "采购项目等待审批");
uniWork.feishu().sendContent(userId, "采购项目等待审批");
```

发送带标题内容：

```java
uniWork.wecom().sendContent(userId, "采购审批", "项目等待处理");
```

发送基础卡片：

```java
uniWork.wecom().sendCard(userId, "采购审批", "项目等待处理", detailUrl);
uniWork.dingtalk().sendCard(userId, "采购审批", "项目等待处理", detailUrl);
uniWork.feishu().sendCard(userId, "采购审批", "项目等待处理", detailUrl);
```

卡片会分别映射为企业微信 `textcard`、钉钉 `action_card` 和飞书 `interactive`。按钮文字默认是“查看详情”，可通过各平台的 `card-button-text` 修改。

## 登录和用户

```java
String authorizeUrl = uniWork.wecom().loginUrl();
UniWorkUser user = uniWork.wecom().login(code);
UniWorkUser member = uniWork.wecom().getUser(userId);
```

钉钉浏览器 OAuth 回调参数名是 `authCode`，把它的值传给 `login(authCode)` 即可。企业微信和飞书回调参数名为 `code`。

三平台都支持相同调用：

```java
uniWork.wecom().loginUrl();
uniWork.dingtalk().loginUrl();
uniWork.feishu().loginUrl();
```

## 常用可选配置

三个平台都支持：

```yaml
connect-timeout-millis: 3000
read-timeout-millis: 5000
oauth-state: uniwork
card-button-text: 查看详情
```

生产环境应把 `oauth-state` 改成难以猜测的值，并在 OAuth 回调中核对平台原样返回的 `state`，用于降低跨站请求伪造风险。

飞书消息接收人默认按 `user_id` 解释，可修改：

```yaml
receive-id-type: open_id
user-id-type: user_id
```

钉钉默认 OAuth scope 为 `openid`，飞书默认为 `contact:contact.base:readonly`，企业微信默认为 `snsapi_base`。需要更多用户字段时，应先在平台后台申请对应权限，再通过 `oauth-scope` 配置。

## 自定义医院 OA

完整案例位于 `uniwork-example-hospital-oa`。扩展包声明自己的渠道接口和 Provider，UniWork 通过 Java `ServiceLoader` 自动发现。

```java
uniWork.platform(HospitalOaChannel.class)
        .sendContent("EMP10086", "采购项目等待审批");
```

自定义渠道不需要修改 UniWork 核心，也不依赖字符串别名。

## 模块

- `uniwork-core`：公共 API、配置加载和扩展 SPI
- `uniwork-platform-support`：平台适配器共用的 Java 8 HTTP/JSON 支撑
- `uniwork-wecom`：企业微信适配
- `uniwork-dingtalk`：钉钉适配
- `uniwork-feishu`：飞书适配
- `uniwork-all`：三平台聚合依赖
- `uniwork-bom`：版本统一管理
- `uniwork-example-hospital-oa`：自定义平台改造案例

## 构建与兼容性

```bash
./mvnw clean verify
```

构建会运行 Java 8 API 检查。GitHub Actions 使用 Java 8、17、21 三个版本验证。

当前自动化测试使用本地模拟 HTTP 服务核对请求地址、鉴权头、JSON 报文、登录用户映射和令牌缓存；真实企业账号的权限、可见范围和后台配置仍需使用各平台测试应用联调。

## 官方接口依据

- [企业微信获取 access_token](https://developer.work.weixin.qq.com/document/path/91039)
- [企业微信发送应用消息](https://developer.work.weixin.qq.com/document/path/90236)
- [企业微信网页授权](https://developer.work.weixin.qq.com/document/path/91022)
- [钉钉浏览器 OAuth 授权](https://opensource.dingtalk.com/developerpedia/docs/develop/permission/token/browser/get_user_app_token_browser/)
- [飞书获取授权码](https://open.feishu.cn/document/authentication-management/access-token/obtain-oauth-code)
- [飞书获取用户 access_token](https://open.feishu.cn/document/authentication-management/access-token/get-user-access-token)
- [飞书发送消息](https://open.feishu.cn/document/uAjLw4CM/ukTMukTMukTM/reference/im-v1/message/create)

## License

Apache License 2.0
