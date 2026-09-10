/**
 * # 手写 RSA PKCS#1 v1.5 公钥加密（与 airpower-open 口径对齐）
 *
 * 为什么手写而不是用浏览器原生 `crypto.subtle`：Web Crypto API 的 RSA 只支持
 * **RSA-OAEP**，不支持 Java `Cipher.getInstance("RSA")` 默认的 **PKCS#1 v1.5**
 * 填充。后端 airpower-open 的 `AirRsa` 用的正是 `RSA/ECB/PKCS1Padding`，为了互通，
 * 这里用 `BigInt` 实现最小化的 PKCS#1 v1.5 **公钥方向**运算。
 *
 * ## 与后端 `AirRsa` 对齐的口径（已对照开源 SDK `AirPowerTeam/AirPowerJavaSdk`）
 * - 密钥 2048 位，公钥为 **X509 SPKI DER 的 Base64**（无 PEM 头）；
 * - 分段加密，每段 `2048/8 - 11 = 245` 字节（PKCS#1 v1.5 padding 占 11 字节）；
 * - 密文 Base64 输出；
 * - 请求加密用 `publicKey` 字段；响应「解密」用 `appSecret` 字段（公钥方向验签式
 *   还原 `c^e mod n`，服务端用私钥加密响应）。
 *
 * 只做公钥方向（加密 `m^e mod n` 与公钥解密 `c^e mod n`），不做私钥运算 ——
 * 这恰好覆盖 open/app「测试应用」对话框 RSA 模式的全部需要。
 */

/** 单段明文上限：2048/8 - 11（PKCS#1 v1.5 padding 开销） */
const RSA_BLOCK_SIZE = 245

/** 从 Base64 字符串解码为字节数组（兼容 URL-safe 字符） */
function base64ToBytes(input: string): Uint8Array {
  const normalized = input.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
  const binary = atob(padded)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i += 1) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

/** 字节数组转 Base64（与后端 `Base64.getEncoder()` 一致的标准 Base64） */
function bytesToBase64(bytes: Uint8Array): string {
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary)
}

/** 字节数组 → 无符号大整数（DER INTEGER 的前导零按无符号语义处理） */
function bytesToBigInt(bytes: Uint8Array): bigint {
  let value = 0n
  for (const byte of bytes) {
    value = (value << 8n) | BigInt(byte)
  }
  return value
}

/** 大整数 → 定长字节数组（左补零到 k 字节，供模幂结果规范化） */
function bigIntToBytes(value: bigint, length: number): Uint8Array {
  const bytes = new Uint8Array(length)
  let remaining = value
  for (let i = length - 1; i >= 0; i -= 1) {
    bytes[i] = Number(remaining & 0xFFn)
    remaining >>= 8n
  }
  return bytes
}

/**
 * 极简 DER 读取器：按 TLV 结构顺序读取，只够解析 RSA SPKI 公钥。
 */
class DerReader {
  private readonly view: DataView

  private offset = 0

  constructor(private readonly bytes: Uint8Array) {
    this.view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  }

  private readLength(): number {
    const first = this.view.getUint8(this.offset)
    this.offset += 1
    if ((first & 0x80) === 0) {
      return first
    }
    const count = first & 0x7F
    let length = 0
    for (let i = 0; i < count; i += 1) {
      length = (length << 8) | this.view.getUint8(this.offset)
      this.offset += 1
    }
    return length
  }

  /** 读取一个 TLV，返回其 value 的字节切片 */
  private readTlv(): Uint8Array {
    this.offset += 1 // 跳过 tag（本实现只解析预期结构，不做 tag 校验）
    const length = this.readLength()
    const value = this.bytes.slice(this.offset, this.offset + length)
    this.offset += length
    return value
  }

  /**
   * 从 X509 SPKI DER 中提取 RSA 的 (modulus, publicExponent)。
   *
   * SPKI 结构：SEQUENCE { SEQUENCE { OID, NULL }, BIT STRING { SEQUENCE { n, e } } }
   */
  static parseRsaPublicKey(der: Uint8Array): { modulus: bigint, exponent: bigint } {
    const reader = new DerReader(der)
    const spkiBody = reader.readTlv() // 最外层 SEQUENCE
    const spkiReader = new DerReader(spkiBody)
    spkiReader.readTlv() // AlgorithmIdentifier SEQUENCE
    const bitString = spkiReader.readTlv() // BIT STRING
    // BIT STRING 首字节是 unused-bits 计数（RSA 场景为 0），其后才是 RSAPublicKey DER
    const rsaDer = bitString.slice(1)
    const rsaReader = new DerReader(rsaDer)
    const rsaBody = rsaReader.readTlv() // RSAPublicKey SEQUENCE
    const rsaBodyReader = new DerReader(rsaBody)
    const modulusBytes = rsaBodyReader.readTlv()
    const exponentBytes = rsaBodyReader.readTlv()
    return {
      modulus: bytesToBigInt(modulusBytes),
      exponent: bytesToBigInt(exponentBytes),
    }
  }
}

/** 快速模幂 `base^exp mod modulus`（square-and-multiply，BigInt） */
function modPow(base: bigint, exp: bigint, modulus: bigint): bigint {
  let result = 1n
  let b = base % modulus
  let e = exp
  while (e > 0n) {
    if (e & 1n) {
      result = (result * b) % modulus
    }
    b = (b * b) % modulus
    e >>= 1n
  }
  return result
}

/**
 * 生成一段 PKCS#1 v1.5 Type 2 密文块（公钥加密方向）。
 *
 * EB = 0x00 || 0x02 || PS || 0x00 || M，其中 PS 为至少 8 字节的非零随机字节。
 */
function pkcs1v15EncryptBlock(message: Uint8Array, modulus: bigint, exponent: bigint, k: number): Uint8Array {
  const psLength = k - 3 - message.length
  if (psLength < 8) {
    throw new Error(`RSA 明文过长：本段 ${message.length} 字节，2048 位密钥单段上限 ${RSA_BLOCK_SIZE} 字节`)
  }
  const eb = new Uint8Array(k)
  eb[0] = 0x00
  eb[1] = 0x02
  // 非零随机填充
  const random = crypto.getRandomValues(new Uint8Array(psLength))
  for (let i = 0; i < psLength; i += 1) {
    let value = random[i]
    while (value === 0) {
      value = crypto.getRandomValues(new Uint8Array(1))[0]
    }
    eb[2 + i] = value
  }
  eb[2 + psLength] = 0x00
  eb.set(message, 3 + psLength)
  const m = bytesToBigInt(eb)
  const c = modPow(m, exponent, modulus)
  return bigIntToBytes(c, k)
}

/**
 * 从 X509 SPKI 公钥 Base64 解析出 (modulus, exponent, 模数字节长度)。
 */
function parsePublicKey(publicKeyBase64: string): { modulus: bigint, exponent: bigint, k: number } {
  const der = base64ToBytes(publicKeyBase64)
  const { modulus, exponent } = DerReader.parseRsaPublicKey(der)
  const k = Math.ceil(modulus.toString(2).length / 8) // 模数字节长度（2048 位 → 256）
  return { modulus, exponent, k }
}

/**
 * ### 公钥加密（PKCS#1 v1.5，分段）
 *
 * 对应后端 `AirRsa.encrypt`：请求方向，客户端用 `publicKey` 公钥加密，服务端私钥解密。
 *
 * @param plaintext 明文字符串
 * @param publicKeyBase64 X509 SPKI 公钥的 Base64（无 PEM 头）
 * @returns Base64 密文
 */
export function rsaEncrypt(plaintext: string, publicKeyBase64: string): string {
  const { modulus, exponent, k } = parsePublicKey(publicKeyBase64)
  const message = new TextEncoder().encode(plaintext)
  const chunks: Uint8Array[] = []
  for (let offset = 0; offset < message.length; offset += RSA_BLOCK_SIZE) {
    const chunk = message.slice(offset, offset + RSA_BLOCK_SIZE)
    chunks.push(pkcs1v15EncryptBlock(chunk, modulus, exponent, k))
  }
  const total = new Uint8Array(chunks.reduce((sum, chunk) => sum + chunk.length, 0))
  let cursor = 0
  for (const chunk of chunks) {
    total.set(chunk, cursor)
    cursor += chunk.length
  }
  return bytesToBase64(total)
}

/**
 * 解一段 PKCS#1 v1.5 Type 2 密文块（公钥「解密」方向）。
 *
 * 后端 `AirRsa.decrypt` 用公钥做 `DECRYPT_MODE`，等价于 `m = c^e mod n` 后再剥
 * `00 02 PS 00 M` 填充取回 M（即服务端私钥加密、客户端公钥还原的「验签式」解密）。
 */
function pkcs1v15DecryptBlock(block: Uint8Array, modulus: bigint, exponent: bigint, k: number): Uint8Array {
  const c = bytesToBigInt(block)
  const m = modPow(c, exponent, modulus)
  const eb = bigIntToBytes(m, k)
  // 剥填充：EB = 00 02 PS 00 M，PS 非零，分隔符是 0x02 之后第一个 0x00
  if (eb[0] !== 0x00 || eb[1] !== 0x02) {
    throw new Error('RSA 解密失败：密文块填充头无效（00 02）')
  }
  let separator = -1
  for (let i = 2; i < eb.length; i += 1) {
    if (eb[i] === 0x00) {
      separator = i
      break
    }
  }
  if (separator < 0) {
    throw new Error('RSA 解密失败：未找到填充分隔符')
  }
  return eb.slice(separator + 1)
}

/**
 * ### 公钥「解密」（PKCS#1 v1.5，分段）
 *
 * 对应后端 `AirRsa.decrypt`：响应方向，服务端用私钥加密响应，客户端用 `appSecret`
 * （存的是公钥）还原明文。
 *
 * @param ciphertextBase64 Base64 密文
 * @param publicKeyBase64 X509 SPKI 公钥的 Base64（无 PEM 头）
 * @returns 明文字符串
 */
export function rsaDecrypt(ciphertextBase64: string, publicKeyBase64: string): string {
  const { modulus, exponent, k } = parsePublicKey(publicKeyBase64)
  const full = base64ToBytes(ciphertextBase64)
  const chunks: Uint8Array[] = []
  for (let offset = 0; offset < full.length; offset += k) {
    const block = full.slice(offset, offset + k)
    chunks.push(pkcs1v15DecryptBlock(block, modulus, exponent, k))
  }
  const total = new Uint8Array(chunks.reduce((sum, chunk) => sum + chunk.length, 0))
  let cursor = 0
  for (const chunk of chunks) {
    total.set(chunk, cursor)
    cursor += chunk.length
  }
  return new TextDecoder().decode(total)
}
