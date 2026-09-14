import type { KeyObject } from 'node:crypto'
import { Buffer } from 'node:buffer'
import { constants, generateKeyPairSync, privateDecrypt } from 'node:crypto'
import { describe, expect, it } from 'vitest'
import { rsaDecrypt, rsaEncrypt } from '@/utils/rsa'

/**
 * `rsaEncrypt` / `rsaDecrypt` 与后端 airpower-open `AirRsa` 的互通性测试。
 *
 * Node `crypto` 的公开 API 只有「公钥加密 Type2↔私钥解密」和「私钥签名 Type1↔公钥验签」
 * 两对；airpower 的响应方向是「**私钥加密 Type2** ↔ 公钥解密 Type2」，Node 没有直接对应，
 * 因此这里用 JWK 导出 (n,e,d) 后**手写服务端私钥加密 Type2**，再验证本工具的 `rsaDecrypt`
 * 能还原 —— 这是「可验证」的手写实现，沙箱内无需真实后端即可证明口径正确。
 */

const RSA_KEY_BYTES = 256

function base64UrlToBigInt(b64: string): bigint {
  const normalized = b64.replace(/-/g, '+').replace(/_/g, '/')
  const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
  const bytes = Buffer.from(padded, 'base64')
  let value = 0n
  for (const byte of bytes) {
    value = (value << 8n) | BigInt(byte)
  }
  return value
}

function bigIntToBytes(value: bigint, length: number): Buffer {
  const bytes = Buffer.alloc(length)
  let remaining = value
  for (let i = length - 1; i >= 0; i -= 1) {
    bytes[i] = Number(remaining & 0xFFn)
    remaining >>= 8n
  }
  return bytes
}

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

/** 模拟服务端：私钥加密 Type2（对应后端 AirRsa 响应方向 `m^d mod n` + PKCS#1 v1.5 Type2） */
function serverEncryptType2(plaintext: string, privateKey: KeyObject): string {
  const jwk = privateKey.export({ format: 'jwk' }) as { n: string, d: string }
  const n = base64UrlToBigInt(jwk.n)
  const d = base64UrlToBigInt(jwk.d)
  const k = RSA_KEY_BYTES
  const message = Buffer.from(plaintext, 'utf8')
  if (k - 3 - message.length < 8) {
    throw new Error('明文过长')
  }
  const eb = Buffer.alloc(k)
  eb[0] = 0x00
  eb[1] = 0x02
  // 测试用固定非零填充（生产用随机，这里只验证口径，无需随机）
  eb.fill(0xFF, 2, k - 1 - message.length)
  eb[k - 1 - message.length] = 0x00
  message.copy(eb, k - message.length)
  const m = base64UrlToBigInt(eb.toString('base64'))
  return bigIntToBytes(modPow(m, d, n), k).toString('base64')
}

/** 模拟后端 AirRsa 的分段私钥解密（每 256 字节一段，逐段 PKCS#1 v1.5 解密后拼接） */
function decrypt(privateKey: KeyObject, ciphertext: string): string {
  const full = Buffer.from(ciphertext, 'base64')
  const chunks: Buffer[] = []
  for (let offset = 0; offset < full.length; offset += RSA_KEY_BYTES) {
    chunks.push(privateDecrypt(
      { key: privateKey, padding: constants.RSA_PKCS1_PADDING },
      full.subarray(offset, offset + RSA_KEY_BYTES),
    ))
  }
  return Buffer.concat(chunks).toString('utf8')
}

describe('rsaEncrypt · 请求方向（公钥加密 ↔ 服务端私钥解密）', () => {
  it('短明文加密后可由私钥解密还原', () => {
    const { publicKey, privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 })
    const publicKeyBase64 = Buffer.from(publicKey.export({ type: 'spki', format: 'der' })).toString('base64')

    const plaintext = JSON.stringify({ name: 'example', age: 18 })
    const ciphertext = rsaEncrypt(plaintext, publicKeyBase64)

    expect(decrypt(privateKey, ciphertext)).toBe(plaintext)
  })

  it('超长明文（> 245 字节）分段加密后可还原', () => {
    const { publicKey, privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 })
    const publicKeyBase64 = Buffer.from(publicKey.export({ type: 'spki', format: 'der' })).toString('base64')

    const plaintext = `长文本-${'A'.repeat(600)}`
    const ciphertext = rsaEncrypt(plaintext, publicKeyBase64)

    expect(decrypt(privateKey, ciphertext)).toBe(plaintext)
  })
})

describe('rsaDecrypt · 响应方向（服务端私钥加密 Type2 ↔ 客户端公钥解密）', () => {
  it('还原服务端私钥加密（PKCS#1 v1.5 Type2）的响应明文', () => {
    const { publicKey, privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 })
    const publicKeyBase64 = Buffer.from(publicKey.export({ type: 'spki', format: 'der' })).toString('base64')

    const plaintext = JSON.stringify({ code: 200, data: { name: 'test', age: 1 } })
    const serverCipher = serverEncryptType2(plaintext, privateKey)

    expect(rsaDecrypt(serverCipher, publicKeyBase64)).toBe(plaintext)
  })

  it('还原超长响应（多段私钥加密 Type2）', () => {
    const { publicKey, privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 })
    const publicKeyBase64 = Buffer.from(publicKey.export({ type: 'spki', format: 'der' })).toString('base64')

    const plaintext = `长响应-${'B'.repeat(600)}`
    const message = Buffer.from(plaintext, 'utf8')
    const chunks: string[] = []
    for (let offset = 0; offset < message.length; offset += 245) {
      chunks.push(serverEncryptType2(message.subarray(offset, offset + 245).toString('utf8'), privateKey))
    }
    const serverCipher = Buffer.concat(chunks.map(chunk => Buffer.from(chunk, 'base64'))).toString('base64')

    expect(rsaDecrypt(serverCipher, publicKeyBase64)).toBe(plaintext)
  })
})
