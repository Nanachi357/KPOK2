package com.myprojects.kpok2.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;

/**
 * Utility class for encrypting and decrypting sensitive data
 * Uses AES-GCM encryption with a randomly generated salt and IV for each encryption
 */
@Slf4j
@Component
public class PasswordEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;
    private static final int SALT_LENGTH_BYTE = 16;
    private static final int KEY_LENGTH_BIT = 256;
    
    private final String keySeed;
    
    /**
     * Constructor that initializes or loads the key seed
     */
    public PasswordEncryptor() {
        // Get the user home directory
        String userHome = System.getProperty("user.home");
        Path keyFilePath = Paths.get(userHome, ".kpok2", "encryption.key");
        
        // Create directory if it doesn't exist
        if (!Files.exists(keyFilePath.getParent())) {
            try {
                Files.createDirectories(keyFilePath.getParent());
            } catch (Exception e) {
                log.error("Failed to create directory for encryption key", e);
            }
        }
        
        // Check if key file exists, if not create a new one
        if (!Files.exists(keyFilePath)) {
            try {
                // Generate a random key seed
                SecureRandom random = new SecureRandom();
                byte[] seed = new byte[32];
                random.nextBytes(seed);
                
                // Encode as Base64 for storage
                String encodedSeed = Base64.getEncoder().encodeToString(seed);
                
                // Write to file
                Files.write(keyFilePath, encodedSeed.getBytes(StandardCharsets.UTF_8));
                log.info("Generated new encryption key seed");
                
                keySeed = encodedSeed;
            } catch (Exception e) {
                log.error("Failed to create encryption key", e);
                throw new RuntimeException("Failed to create encryption key", e);
            }
        } else {
            try {
                // Read existing key
                keySeed = new String(Files.readAllBytes(keyFilePath), StandardCharsets.UTF_8);
                log.info("Loaded existing encryption key seed");
            } catch (Exception e) {
                log.error("Failed to load encryption key", e);
                throw new RuntimeException("Failed to load encryption key", e);
            }
        }
    }
    
    /**
     * Encrypts a password
     * 
     * @param password The password to encrypt
     * @return Base64 encoded encrypted password
     */
    public String encryptPassword(String password) {
        try {
            // Generate salt and IV
            SecureRandom random = new SecureRandom();
            byte[] salt = new byte[SALT_LENGTH_BYTE];
            byte[] iv = new byte[IV_LENGTH_BYTE];
            random.nextBytes(salt);
            random.nextBytes(iv);
            
            // Derive key from seed and salt
            SecretKey key = getKeyFromSeed(salt);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
            
            // Encrypt
            byte[] encryptedData = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            
            // Combine salt, IV, and encrypted data
            ByteBuffer byteBuffer = ByteBuffer.allocate(salt.length + iv.length + encryptedData.length);
            byteBuffer.put(salt);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedData);
            
            // Return as Base64 string
            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Failed to encrypt password", e);
            throw new RuntimeException("Failed to encrypt password", e);
        }
    }
    
    /**
     * Decrypts an encrypted password
     * 
     * @param encryptedPassword Base64 encoded encrypted password
     * @return Decrypted password
     */
    public String decryptPassword(String encryptedPassword) {
        try {
            // Decode Base64 string
            byte[] encryptedData = Base64.getDecoder().decode(encryptedPassword);
            
            // Extract salt, IV, and encrypted password
            ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedData);
            byte[] salt = new byte[SALT_LENGTH_BYTE];
            byte[] iv = new byte[IV_LENGTH_BYTE];
            byte[] ciphertext = new byte[encryptedData.length - salt.length - iv.length];
            
            byteBuffer.get(salt);
            byteBuffer.get(iv);
            byteBuffer.get(ciphertext);
            
            // Derive key from seed and salt
            SecretKey key = getKeyFromSeed(salt);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(TAG_LENGTH_BIT, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
            
            // Decrypt
            byte[] decryptedData = cipher.doFinal(ciphertext);
            
            return new String(decryptedData, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt password", e);
            throw new RuntimeException("Failed to decrypt password", e);
        }
    }
    
    /**
     * Derives a secret key from the key seed and salt
     */
    private SecretKey getKeyFromSeed(byte[] salt) throws Exception {
        // Use PBKDF2 to derive a key from the seed
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(
                keySeed.toCharArray(),
                salt,
                10000, // Iteration count
                KEY_LENGTH_BIT
        );
        SecretKey secretKey = factory.generateSecret(spec);
        return new SecretKeySpec(secretKey.getEncoded(), "AES");
    }
} 