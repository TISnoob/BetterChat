package com.tis199.betterchat.common.model;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

/** Small, versioned packet used between Paper backends and supported proxies. */
public record ProxyChatMessage(int version, String id, String senderId, String senderName,
                              String language, String country, String message, String origin) {
    private static final Gson GSON = new Gson();
    public static final int CURRENT_VERSION = 1;
    public static final int MAX_MESSAGE_LENGTH = 2_000;

    public ProxyChatMessage {
        if (version != CURRENT_VERSION) throw new IllegalArgumentException("Unsupported packet version");
        UUID.fromString(senderId);
        if (id == null || id.length() > 64 || senderName == null || senderName.length() > 64
                || message == null || message.length() > MAX_MESSAGE_LENGTH)
            throw new IllegalArgumentException("Invalid chat packet");
        language = PlayerPreferences.normalizeLanguage(language);
        country = PlayerPreferences.normalizeCountry(country);
        origin = origin == null ? "unknown" : origin.substring(0, Math.min(origin.length(), 64));
    }

    public static ProxyChatMessage create(UUID sender, String senderName, PlayerPreferences preferences,
                                          String message, String origin) {
        String safeMessage = message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) : message;
        return new ProxyChatMessage(CURRENT_VERSION, UUID.randomUUID().toString(), sender.toString(), senderName,
                preferences.language(), preferences.country(), safeMessage, origin);
    }

    public String toJson() { return GSON.toJson(this); }

    public byte[] encode(char direction, String sharedSecret) {
        if (direction != 'C' && direction != 'S') throw new IllegalArgumentException("Invalid packet direction");
        byte[] json = toJson().getBytes(StandardCharsets.UTF_8);
        byte[] signature = hmac(json, sharedSecret).getBytes(StandardCharsets.US_ASCII);
        byte[] payload = new byte[1 + signature.length + 1 + json.length];
        payload[0] = (byte) direction;
        System.arraycopy(signature, 0, payload, 1, signature.length);
        payload[65] = '\n';
        System.arraycopy(json, 0, payload, 66, json.length);
        return payload;
    }

    public static ProxyChatMessage decode(byte[] payload, char expectedDirection, String sharedSecret) {
        if (payload == null || payload.length < 67 || payload.length > 32_767
                || payload[0] != (byte) expectedDirection || payload[65] != '\n')
            throw new IllegalArgumentException("Invalid chat packet framing");
        byte[] json = java.util.Arrays.copyOfRange(payload, 66, payload.length);
        String expected = hmac(json, sharedSecret);
        byte[] actual = java.util.Arrays.copyOfRange(payload, 1, 65);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), actual))
            throw new IllegalArgumentException("Invalid chat packet signature");
        return parse(new String(json, StandardCharsets.UTF_8));
    }

    private static String hmac(byte[] message, String secret) {
        if (secret == null || secret.length() < 32)
            throw new IllegalArgumentException("Proxy shared secret must contain at least 32 characters");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not sign proxy chat packet", exception);
        }
    }

    public static ProxyChatMessage parse(String json) {
        try {
            ProxyChatMessage packet = GSON.fromJson(json, ProxyChatMessage.class);
            if (packet == null) throw new IllegalArgumentException("Empty packet");
            return packet;
        } catch (JsonSyntaxException exception) {
            throw new IllegalArgumentException("Invalid chat packet", exception);
        }
    }
}
