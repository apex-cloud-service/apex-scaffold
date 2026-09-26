package com.apex.scaffold.license;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;

/**
 * RSA加密工具类
 *
 * <p>用于License的签名生成和验证。
 * 使用RSA-SHA256算法,密钥长度2048位。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
public final class RsaUtil {

    private static final String ALGORITHM = "RSA";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    private static final int KEY_SIZE = 2048;

    private RsaUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 生成RSA密钥对
     *
     * @return KeyPair对象
     * @throws NoSuchAlgorithmException 算法不存在
     */
    public static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(ALGORITHM);
        keyPairGen.initialize(KEY_SIZE);
        return keyPairGen.generateKeyPair();
    }

    /**
     * 将公钥转换为Base64字符串
     *
     * @param publicKey 公钥
     * @return Base64编码的公钥字符串
     */
    public static String publicKeyToBase64(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * 将私钥转换为Base64字符串
     *
     * @param privateKey 私钥
     * @return Base64编码的私钥字符串
     */
    public static String privateKeyToBase64(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    /**
     * 从Base64字符串加载公钥
     *
     * @param base64PublicKey Base64编码的公钥
     * @return PublicKey对象
     * @throws Exception 加载失败
     */
    public static PublicKey loadPublicKey(String base64PublicKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * 从Base64字符串加载私钥
     *
     * @param base64PrivateKey Base64编码的私钥
     * @return PrivateKey对象
     * @throws Exception 加载失败
     */
    public static PrivateKey loadPrivateKey(String base64PrivateKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64PrivateKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * 使用私钥对数据进行签名
     *
     * @param data 待签名数据
     * @param privateKey 私钥
     * @return Base64编码的签名字符串
     * @throws Exception 签名失败
     */
    public static String sign(byte[] data, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(privateKey);
        signature.update(data);
        byte[] signed = signature.sign();
        return Base64.getEncoder().encodeToString(signed);
    }

    /**
     * 使用公钥验证签名
     *
     * @param data 原始数据
     * @param signature Base64编码的签名
     * @param publicKey 公钥
     * @return 验证结果
     * @throws Exception 验证失败
     */
    public static boolean verify(byte[] data, String signature, PublicKey publicKey) throws Exception {
        Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
        sig.initVerify(publicKey);
        sig.update(data);
        byte[] sigBytes = Base64.getDecoder().decode(signature);
        return sig.verify(sigBytes);
    }

    /**
     * 计算公钥指纹(SHA-256前16字符)
     *
     * @param publicKey 公钥
     * @return 公钥指纹
     * @throws Exception 计算失败
     */
    public static String calculateFingerprint(PublicKey publicKey) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(publicKey.getEncoded());
        // 取前16个字符作为指纹
        return Base64.getEncoder().encodeToString(hash).substring(0, 16);
    }
}
