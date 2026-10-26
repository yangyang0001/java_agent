# Viber Callback `GW_999998 / system busy` 调用链路排查

## 现象

调用 `POST /viber/{uri}/callback` 返回：

```json
{
    "success": false,
    "errorCode": "GW_999998",
    "errorMessage": "system busy,please try again later",
    "failure": true
}
```

`GW_999998` / `"system busy,please try again later"` 是 `gateway-common` 依赖包里
`CommonErrorCodeEnum.ERROR_DEFAULT` 的固定文案（反编译确认，
`gateway-common-2026.06.10.02-SOCIAL-RELEASE.jar`）。这个错误码**只在**
`com.digiplus.gateway.common.aspect.GlobalExceptionHandler#handleException(Throwable)`
（兜底捕获一切未被业务代码处理的异常）里被使用：

```java
public SimpleResult<?> handleException(Throwable t) {
    log.warn("GlobalExceptionHandler unknown exception {}", t.getMessage(), t);
    return SimpleResult.failure(ERROR_DEFAULT.getErrorCode(), ERROR_DEFAULT.getErrorMessage());
}
```

也就是说：**这不是业务逻辑主动返回的错误，而是某处抛出了一个没人接住的异常**。

## 调用链路

```
Viber 服务器
   │  POST /viber/{uri}/callback
   ▼
Tomcat / Servlet 容器
   ▼
【第一层】com.digiplus.gateway.common.mvc.filters.SignVerificationFilter   ← gateway-common 全局 Filter
   │   注册方式: FilterConfig#signVerificationFilter() → addUrlPatterns("/*")
   │   即：所有请求（含 /viber/**）都会先经过这个通用签名校验 Filter，
   │   它跑在 Spring MVC DispatcherServlet 之前，ViberWebHookController 完全不知情。
   │
   ├─ 白名单命中 → 直接放行 (filterChain.doFilter)
   │     · 硬编码 CONST_WHITE_LIST = [/actuator, /health, favicon,
   │        /deposit/OnlinePaymentThird.do, /deposit/orderNotify]  —— 不含 /viber/**
   │     · 可配置白名单 whiteListForSignVerification (@Value 默认值 "ALL")
   │        —— 本仓库未在任何 yml/properties 里覆盖这个 key，
   │           说明其值完全由 Nacos 远程配置决定，本地看不到实际生效值。
   │           ⚠️ 这是需要人工确认的关键点。
   │
   └─ 未命中白名单 → 按“游戏/H5 通用签名协议”解析请求：
         1. RequestWrapper 读 body
         2. JsonUtil.fromStr(body, BaseReq.class)          （FAIL_ON_UNKNOWN_PROPERTIES=false，
                                                              多余字段不会报错，一般不会抛异常）
         3. populateBaseReqFromHeaders(): 从请求头取 sign/token/qid/appid/v
            —— Viber 发的是 X-Viber-Content-Signature，不是这里期望的 "sign" header，
               所以这些字段基本都是 null
         4. checkSign(request, baseReq)
            —— 内部自己 try/catch(Exception)，算签名/比较失败都只是 return false，
               不会抛出异常
         5. 若 checkSign()==false → writeErrorResponse() 返回
               { errorCode: GW_ERROR_SIGN_FAILED, errorMessage: "sign verification failed" }
            —— 注意：这个文案跟本次观察到的 "system busy" 不一样！

      doFilterInternal 对整个第 1~4 步都包了一层 catch(Throwable)：
         Exception table: ... target=375 type=any
         375: threadLocalCommunityKey.remove(); throw t;   // 原样重新抛出，不生成任何响应体

      → 如果这里面任何一步意外抛出运行时异常（比如 body 读取失败、
        BaseReq 反序列化失败、签名计算内部异常没被完全兜住等），
        该异常会直接跳出 Filter 链，容器兜底成 GW_999998。

   ▼ (若通过 Filter)
【第二层】Spring MVC DispatcherServlet
   ▼
【第三层】ViberWebHookController#callback()   (ViberWebHookController.java:66-79)
   │   @RequestHeader 签名头当前被注释掉了，固定传 signature=""
   ▼
   handleCallback(uri, "", rawBody)          (ViberWebHookController.java:81)
   │   整个方法体都在 try { ... } catch (ViberCallbackRetryableException) { 503 }
   │                                catch (Exception) { 500，空 body }
   │   —— 注意：控制器内部的异常处理返回的是空 body 的 500，
   │      不是 GW_999998 这种 JSON，所以如果异常发生在这里，现象应该不一样。
   │
   │   验签逻辑本身也已经被注释掉 (line 105-109, TODO 说明现网从未验签，
   │   要求先加灰度开关/按 bot 灰度/失败率监控再打开)：
   │
   │   // if (!signatureVerifier.verify(rawBody, signature, bot.getToken())) {
   │   //     return 403
   │   // }
   ▼
   botConfigLoader.getTokenByPlatformAndUri → callbackValidator.validate → callbackOrchestrator.handle
```

## 结论：卡在哪一层

现象文案 `system busy` 精确对应 `ERROR_DEFAULT`，而不是签名校验失败该有的
`"sign verification failed"`，也不是控制器 catch 块产生的空 body 500。
这说明：

- **异常大概率发生在 `SignVerificationFilter` 这一层，且是在其内部“兜底 catch 后原样再抛出”
  的那段代码路径里** —— 即请求根本没有进入 `ViberWebHookController`。
- Filter 是否会真正拦截 `/viber/**`，取决于 Nacos 上 `whiteListForSignVerification`
  这个 key 对当前应用的实际配置值：
  - 若为空/未配置 → `@Value` 默认值 `"ALL"` → 整个 Filter 直接放行，不会是这里的问题。
  - 若被业务同学配成了具体路径列表（且不含 `/viber`）→ 那么每次 Viber 回调都会
    被塞进“游戏 H5 通用签名协议”的解析/校验流程，而 Viber 的 body/header 结构
    跟 `BaseReq` 完全对不上，一旦某个环节抛出未被内部 catch 兜住的异常，
    就会精确复现现在看到的 `GW_999998`。

## 需要人工确认 / 下一步

1. 去 Nacos 查这个应用（sop-gateway）实际生效的 `whiteListForSignVerification`
   （以及 `blackListForSignVerification`）配置值，确认 `/viber/**` 是否被排除在外。
2. 如果确认是这里，两种修复方向二选一：
   - 在 Nacos 白名单里加入 `/viber` 前缀，让 `SignVerificationFilter` 跳过 Viber 回调
     （推荐，Viber 有自己的 `X-Viber-Content-Signature` 验签方案，由
     `ViberWebhookSignatureVerifier` 负责，跟这个通用 Filter 的语义本来就不是一回事）。
   - 或者在网关层配置里把 `/viber/**` 路由到不经过该全局 Filter 的路径规则下。
3. 建议临时在生产开一次 debug 级日志 / 直接查 `SignVerificationFilter` 所在应用实例的
   ERROR 级日志（`log.warn("GlobalExceptionHandler unknown exception {}", ...)` 会打印
   完整堆栈），一次请求即可实锤具体抛在哪一行。
