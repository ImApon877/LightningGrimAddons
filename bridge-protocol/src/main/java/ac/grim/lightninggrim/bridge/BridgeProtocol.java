package ac.grim.lightninggrim.bridge;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Wire format for the authenticated proxy-ban bridge (v1):
 *
 * <pre>
 * [ body ][ HMAC-SHA256(body, secret) ]
 * body: magic:i32 version:u8 requestId:u128 issuedAt:i64
 *       backendId:u8str playerId:u128 playerName:u8str checkKey:u8str violations:i32
 * </pre>
 *
 * The MAC covers the whole body, so any modification (including field length
 * bytes) invalidates the signature. Every payload field is length-capped and
 * charset-validated so a signed-but-weird request still cannot smuggle junk
 * into console command substitution or logs.
 */
public final class BridgeProtocol {
    public static final String CHANNEL = "grimbridge:ban";
    public static final int MAX_PAYLOAD_BYTES = 2048;
    /** Offline-decoders don't know the clock; they can pass 0 to skip the wall-clock sanity bound. */
    public static final long MAX_CLOCK_SKEW_MS = 24L * 60 * 60 * 1000;
    private static final int MAGIC = 0x47524231; // "GRB1"
    private static final byte VERSION = 1;
    private static final int SIGNATURE_BYTES = 32;
    private static final int MAX_FIELD_BYTES = 128;
    private static final Pattern SAFE_FIELD = Pattern.compile("[A-Za-z0-9_.\\-/ ]{1,128}");
    /** VANILLA-safe charset, the same check the proxy plugins apply before command substitution. */
    private static final Pattern SAFE_PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    private BridgeProtocol() {}

    public record BanRequest(UUID requestId, long issuedAt, String backendId, UUID playerId, String playerName,
                             String checkKey, int violations) {}

    public static byte[] encode(BanRequest request, byte[] secret) throws GeneralSecurityException, IOException {
        byte[] body = encodeBody(request);
        byte[] signature = sign(body, secret);
        ByteArrayOutputStream output = new ByteArrayOutputStream(body.length + signature.length);
        output.write(body);
        output.write(signature);
        return output.toByteArray();
    }

    /**
     * Decode + verify a payload. {@code nowMs} bounds the timestamp sanity
     * check ({@code issuedAt} must be within {@link #MAX_CLOCK_SKEW_MS} of it
     * even before the caller's TTL check); pass {@code 0} to skip that bound
     * (offline tests without a clock).
     */
    public static BanRequest decode(byte[] payload, byte[] secret, long nowMs) throws GeneralSecurityException, IOException {
        if (payload == null || payload.length <= SIGNATURE_BYTES || payload.length > MAX_PAYLOAD_BYTES) {
            throw new IOException("Invalid bridge payload size");
        }
        int bodyLength = payload.length - SIGNATURE_BYTES;
        byte[] body = Arrays.copyOf(payload, bodyLength);
        byte[] signature = Arrays.copyOfRange(payload, bodyLength, payload.length);
        if (!MessageDigest.isEqual(signature, sign(body, secret))) {
            throw new GeneralSecurityException("Invalid bridge signature");
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(body))) {
            if (input.readInt() != MAGIC || input.readByte() != VERSION) throw new IOException("Unsupported bridge protocol");
            BanRequest request = new BanRequest(
                    new UUID(input.readLong(), input.readLong()), input.readLong(), readField(input),
                    new UUID(input.readLong(), input.readLong()), readField(input), readField(input), input.readInt()
            );
            if (input.available() != 0) throw new IOException("Trailing bridge payload bytes");
            if (request.violations() < 1 || request.violations() > 1_000_000) throw new IOException("Invalid bridge violations count");
            if (request.issuedAt() <= 0) throw new IOException("Invalid bridge timestamp");
            if (nowMs != 0 && Math.abs(nowMs - request.issuedAt()) > MAX_CLOCK_SKEW_MS) throw new IOException("Bridge timestamp out of range");
            return request;
        }
    }

    /** Charset + length validation shared by backends and proxies. */
    public static boolean isValidBackendId(String backendId) {
        return backendId != null && SAFE_FIELD.matcher(backendId).matches();
    }

    public static boolean isValidPlayerName(String playerName) {
        return playerName != null && SAFE_PLAYER_NAME.matcher(playerName).matches();
    }

    private static byte[] encodeBody(BanRequest request) throws IOException {
        if (!isValidBackendId(request.backendId())) throw new IOException("Invalid bridge backend id");
        if (!isValidPlayerName(request.playerName())) throw new IOException("Invalid bridge player name");
        if (request.checkKey() == null || request.checkKey().isEmpty()
                || request.checkKey().getBytes(StandardCharsets.UTF_8).length > MAX_FIELD_BYTES) {
            throw new IOException("Invalid bridge check key");
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(MAGIC);
            output.writeByte(VERSION);
            output.writeLong(request.requestId().getMostSignificantBits());
            output.writeLong(request.requestId().getLeastSignificantBits());
            output.writeLong(request.issuedAt());
            writeField(output, request.backendId());
            output.writeLong(request.playerId().getMostSignificantBits());
            output.writeLong(request.playerId().getLeastSignificantBits());
            writeField(output, request.playerName());
            writeField(output, request.checkKey());
            output.writeInt(request.violations());
            return bytes.toByteArray();
        }
    }

    private static byte[] sign(byte[] body, byte[] secret) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(body);
    }

    private static void writeField(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_FIELD_BYTES) throw new IOException("Invalid bridge field length");
        output.writeByte(bytes.length);
        output.write(bytes);
    }

    private static String readField(DataInputStream input) throws IOException {
        int length = input.readUnsignedByte();
        if (length == 0 || length > MAX_FIELD_BYTES || input.available() < length) throw new IOException("Invalid bridge field length");
        byte[] bytes = input.readNBytes(length);
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (!Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) throw new IOException("Invalid bridge UTF-8");
        return value;
    }
}
