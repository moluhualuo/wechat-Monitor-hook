# libtenpay_utils.so 逆向分析

## 1. 文件定位

- 目标文件：`wx8068_mcp_decode/lib/arm64-v8a/libtenpay_utils.so`
- 目标版本：微信 Android 8.0.68
- 分析工具：IDA Pro + MCP
- 模块性质：微信支付 / 财付通 native 工具库

结论：`libtenpay_utils.so` 主要负责支付相关的加密、解密、证书、签名、验签、OTP 等能力，不是微信聊天消息或支付通知 XML 的解析模块。

也就是说：

- 监听普通明文消息：不需要分析这个 so。
- 监听支付通知消息：优先走 Java 消息对象 `ok.y7.j()` / `com.tencent.mm.storage.f8.j()`，检查 `type=49` 和 `<sysmsg>` XML。
- 分析支付密码、签名、证书、TOTP、支付请求加密：才需要深入 `libtenpay_utils.so`。

## 2. JNI 类映射

该 so 暴露的 JNI 入口主要对应以下 Java 类：

| Java 类 | native 作用 |
|---|---|
| `com.tenpay.ndk.Encrypt` | 通用加密、解密、RSA、公钥加密、AES/SM4、支付密码处理 |
| `com.tenpay.ndk.CertUtil` | 用户证书导入、读取、签名、验签、token 生成 |
| `com.tenpay.ndk.HkWxCertUtil` | 香港钱包证书相关能力 |
| `com.tenpay.ndk.HkWxCryptoUtil` | 香港钱包密码加密、数据加密 |
| `com.tenpay.ndk.HkWxTokenUtil` | OTP / token 校验相关能力 |
| `com.tenpay.ndk.MessageDigestUtil` | SHA / HMAC / 摘要工具 |

## 3. 关键 JNI 入口

| JNI 入口 | 说明 |
|---|---|
| `Java_com_tenpay_ndk_Encrypt_encrypt` | 财付通通用加密入口 |
| `Java_com_tenpay_ndk_Encrypt_decrypt` | 财付通通用解密入口 |
| `Java_com_tenpay_ndk_Encrypt_encryptPasswdWithRSA` | 使用 RSA 加密支付密码 |
| `Java_com_tenpay_ndk_Encrypt_aesEncryptCBC` | AES-CBC 加密 |
| `Java_com_tenpay_ndk_Encrypt_sm4EncryptCBC` | SM4-CBC 加密 |
| `Java_com_tenpay_ndk_CertUtil_usrSig` | 用户证书签名 |
| `Java_com_tenpay_ndk_CertUtil_getToken` | 基于证书 / secret 生成 token |
| `Java_com_tenpay_ndk_CertUtil_importCert` | 导入用户证书 |
| `Java_com_tenpay_ndk_CertUtil_verifyCert` | 验证证书 / 签名 |
| `Java_com_tenpay_ndk_HkWxCryptoUtil_encryptPassword` | 香港钱包密码加密 |
| `Java_com_tenpay_ndk_HkWxTokenUtil_verifyOtp` | OTP 校验 |

## 4. 核心函数地址表

| 地址 | 函数 / 作用 | 逆向结论 |
|---|---|---|
| `0x1162c` | `verifySign` | 证书验签，按 RSA key 长度选择 SHA1 / SHA256 |
| `0x3c44c` | `fitGenerateTotp` | 基于 HMAC-SHA256 的 TOTP 生成 |
| `0x3d748` | `fit_rsa_public_encrypt` | RSA 公钥加密，PKCS#1 v1.5 padding |
| `0x20af4` | `EncryptWithRsaPubKey` | RSA 公钥加密封装，支持 1024 / 2048 key |
| `0x3dc54` | `fit_rsa_private_encrypt` | RSA 私钥签名式加密，PKCS#1 v1.5 签名 padding |
| `0xf514` | `importUserCertificate` | 导入用户 X.509 DER 证书并写入本地证书结构 |
| `0x2271c` | `R_GeneratePEMKeys` | 生成 RSA key pair |
| `0x1c53c` | `DesDec` | DES ECB 解密，8 字节分组 |

## 5. RSA 加密实现

### 5.1 `fit_rsa_public_encrypt` @ `0x3d748`

该函数实现 RSA 公钥加密，使用 PKCS#1 v1.5 加密填充格式：

```text
00 02 PS 00 DATA
```

其中：

- `00 02`：PKCS#1 v1.5 public-key encryption block 标识。
- `PS`：随机非零填充字节。
- `00`：填充和明文之间的分隔符。
- `DATA`：待加密明文。

逆向观察到随机填充使用 `srand(time(0))` + `rand()` 生成，并过滤掉 `0x00`，保证 padding string 中无零字节。

该实现更偏向传统 RSA PKCS#1 v1.5 加密，不是 OAEP。

### 5.2 `EncryptWithRsaPubKey` @ `0x20af4`

该函数是 RSA 公钥加密封装层，主要负责：

1. 判断 key 类型或 key 长度。
2. 支持 1024-bit / 2048-bit RSA 公钥。
3. 对输入 / 输出做字节序处理。
4. 调用底层 RSA public operation。

该函数最终进入类似 `rsapublicfunc` 的底层大整数模幂运算。

### 5.3 `fit_rsa_private_encrypt` @ `0x3dc54`

该函数实现 RSA 私钥加密 / 签名式操作，填充结构符合 PKCS#1 v1.5 signature block：

```text
00 01 FF FF ... FF 00 DER_DIGEST_INFO
```

其中：

- `00 01`：签名 block 标识。
- `FF ... FF`：固定 `0xff` padding。
- `00`：分隔符。
- `DER_DIGEST_INFO`：带算法标识的摘要结构。

该函数后续调用 private block operation 完成 RSA 私钥模幂。

### 5.4 `R_GeneratePEMKeys` @ `0x2271c`

该函数负责生成 RSA key pair。

核心流程：

1. 选择 public exponent：常见为 `65537`，也支持 `3`。
2. 生成两个大素数 `p`、`q`。
3. 确认 `gcd(p - 1, e) == 1`。
4. 确认 `gcd(q - 1, e) == 1`。
5. 计算 modulus：`n = p * q`。
6. 计算私钥指数：`d = e^-1 mod phi(n)`。
7. 计算 CRT 参数：
   - `dP = d mod (p - 1)`
   - `dQ = d mod (q - 1)`
   - `qInv = q^-1 mod p`

这说明 so 内部带有完整 RSA key 生成和 RSA 运算实现，不只是调用系统 OpenSSL API。

## 6. 签名与验签

### 6.1 `verifySign` @ `0x1162c`

`verifySign` 是证书验签核心函数。

主要流程：

1. 根据用户或证书标识调用 `findUserCertificate` 查找证书。
2. 从证书结构中提取 RSA public key。
3. 使用 RSA public decrypt / verify operation 还原签名 block。
4. 根据 RSA key 长度选择摘要算法：
   - 1024-bit key：偏向 SHA1。
   - 2048-bit key：偏向 SHA256。
5. 计算待验数据摘要。
6. 构造 PKCS#1 DER DigestInfo。
7. 与签名解出的 DigestInfo 比较。

该函数说明证书签名体系仍是传统 RSA + hash + PKCS#1 v1.5 结构。

### 6.2 相关用途

该类逻辑更可能用于：

- 支付请求签名验签。
- 用户证书签名校验。
- 支付 token 或安全参数验证。
- 支付密码 / 支付请求提交前的安全封装。

它不负责解析微信消息数据库中的 `content` 字段。

## 7. 证书管理

### 7.1 `importUserCertificate` @ `0xf514`

该函数用于导入用户证书。

逆向结论：

1. 输入包含用户证书数据，格式偏向 X.509 DER。
2. 函数解析证书并提取 public key。
3. 生成或拼接 public key label。
4. 将证书和 public key block 写入本地结构。
5. 其中 public key block 观察到约 `514` 字节规模，符合携带 2048-bit key 及其结构信息的可能性。

该逻辑对应 Java 层 `CertUtil` / `HkWxCertUtil` 的证书导入能力。

### 7.2 证书链路作用

证书模块主要服务于支付安全链路：

```text
Java CertUtil
  -> JNI libtenpay_utils.so
    -> 证书导入 / 查找 / 验签 / 用户签名
      -> RSA public/private operation
```

它不是支付通知监听入口，也不是聊天消息明文入口。

## 8. 对称加密

### 8.1 `DesDec` @ `0x1c53c`

该函数实现 DES ECB 解密。

特征：

- 8 字节分组。
- 内部调用 `undes` 一类 DES round 函数。
- padding 风格接近 `0x80` 后跟 `0x00` 的填充方式。

用途可能包括旧版财付通字段解密或兼容旧协议。

### 8.2 AES-CBC

JNI 中存在 `Encrypt_aesEncryptCBC` 入口。

用途推测：

- 支付请求体加密。
- 敏感参数加密。
- 证书或 token 相关字段保护。

### 8.3 SM4-CBC

JNI 中存在 `Encrypt_sm4EncryptCBC` 入口。

SM4 通常用于国密场景，因此该入口可能用于：

- 国内支付合规加密。
- 特定钱包或商户协议。
- 新版支付安全参数封装。

## 9. TOTP / OTP

### `fitGenerateTotp` @ `0x3c44c`

该函数实现基于 HMAC-SHA256 的 TOTP。

逆向观察到 counter 计算类似：

```text
counter = (timestamp - baseTime) / step + offset
```

其中参数对应关系大致为：

```text
counter = (a4 - a3) / a6 + a5
```

之后流程：

1. 用 secret 和 counter 计算 HMAC-SHA256。
2. 进行动态截断。
3. 对结果取模，生成指定长度 OTP。

该函数和 `HkWxTokenUtil.verifyOtp` 一类 JNI 入口相关，主要服务于 token / OTP 校验，不是消息监听。

## 10. 支付密码相关链路

支付密码相关入口包括：

- `Java_com_tenpay_ndk_Encrypt_encryptPasswdWithRSA`
- `Java_com_tenpay_ndk_HkWxCryptoUtil_encryptPassword`

整体链路可概括为：

```text
Java 支付密码输入 / 支付请求参数
  -> com.tenpay.ndk.Encrypt / HkWxCryptoUtil
    -> JNI libtenpay_utils.so
      -> 密码格式化 / 摘要 / RSA / AES / SM4
        -> 输出加密后的支付参数
```

如果目标是分析“用户发起支付时密码如何加密提交”，需要继续从这些 JNI 入口往下追：

| 建议继续分析函数 | 地址 |
|---|---|
| `encrypt_pass` | `0x1676c` |
| `hk_wx_encrypt_pass` | `0x3eb8c` |
| `fitSignData` | `0x3ab4c` |
| `fitHmacSha256` | `0x3c29c` |
| `Java_com_tenpay_ndk_Encrypt_encryptPasswdWithRSA` | `0x12544` |
| `Java_com_tenpay_ndk_HkWxCryptoUtil_encryptPassword` | `0x3a3c8` |

## 11. 与支付消息监听的关系

当前已经确认普通明文消息走 Java 层 getter：

```text
ok.y7.j()
com.tencent.mm.storage.f8.j()
```

支付通知消息更可靠的监听方式也是复用该消息链路：

```text
xv0.wc.j(p0)
  -> h8.U8(f8)
    -> f8.j() / ok.y7.j()
      -> content = <sysmsg type="paymsg">...</sysmsg>
```

支付消息判断条件：

```text
type == 49
content contains "<sysmsg"
```

解析方式：

```text
com.tencent.mm.sdk.platformtools.aa.d(content, "sysmsg", null)
```

重点字段：

| 字段 | 说明 |
|---|---|
| `.sysmsg.paymsg.PayMsgType` | 支付消息类型 |
| `.sysmsg.paymsg.WalletType` | 钱包类型 |
| `.sysmsg.paymsg.*` | 其他支付通知字段 |

因此，如果目标是“监听支付到账 / 支付通知 / 支付类消息”，当前不应该优先 Hook `libtenpay_utils.so`。应该先验证 Java 层 `type=49 + <sysmsg>` 是否稳定触发。

`libtenpay_utils.so` 更适合以下目标：

| 目标 | 是否适合分析该 so |
|---|---|
| 监听聊天明文 | 不适合 |
| 监听支付通知 XML | 通常不需要 |
| 分析支付密码加密 | 适合 |
| 分析证书导入 / 签名 | 适合 |
| 分析 RSA / AES / SM4 实现 | 适合 |
| 分析 OTP / token | 适合 |

## 12. 后续 IDA 分析建议

如果继续逆向该 so，建议按以下顺序：

1. `Java_com_tenpay_ndk_Encrypt_encryptPasswdWithRSA`
   - 确认支付密码输入如何拼接。
   - 确认 RSA key 来源。
   - 确认是否混入 nonce、时间戳、salt。

2. `encrypt_pass` / `hk_wx_encrypt_pass`
   - 对比大陆钱包和香港钱包密码加密差异。
   - 观察是否使用 AES / SM4 / RSA 多层封装。

3. `fitSignData`
   - 确认签名输入格式。
   - 确认 SHA1 / SHA256 选择逻辑。
   - 确认证书私钥调用位置。

4. `fitHmacSha256`
   - 和 token / TOTP / 请求签名关联。

5. `CertUtil_usrSig`
   - 追踪用户证书签名完整调用链。

## 13. 当前结论

`libtenpay_utils.so` 是微信支付安全能力库，核心能力包括：

- RSA 1024 / 2048 加密、签名、验签。
- 用户证书导入和证书查找。
- AES-CBC / SM4-CBC / DES 等对称加密。
- SHA1 / SHA256 / HMAC-SHA256 摘要。
- TOTP / OTP token 生成和校验。
- 支付密码和香港钱包密码加密。

它对“支付请求安全”很关键，但对“支付消息监听”不是第一 Hook 点。

支付监听当前应优先使用：

```text
ok.y7.j()
com.tencent.mm.storage.f8.j()
type=49
content contains <sysmsg>
com.tencent.mm.sdk.platformtools.aa.d(content, "sysmsg", null)
```

只有当后续目标变成“逆向支付参数加密 / 支付密码加密 / 证书签名 / OTP token”时，才继续深入该 so。
