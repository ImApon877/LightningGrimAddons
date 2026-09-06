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
import java.util.UUID;

public final class BridgeProtocol {
    public static final String CHANNEL = "grimbridge:ban";
    public static final int MAX_PAYLOAD_BYTES = 2048;
    private static final int MAGIC = 0x47524231;
    private static final byte VERSION = 1;
    private static final int SIGNATURE_BYTES = 32;

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

    public static BanRequest decode(byte[] payload, byte[] secret) throws GeneralSecurityException, IOException {
        if (payload == null || payload.length <= SIGNATURE_BYTES || payload.length > MAX_PAYLOAD_BYTES) {
            throw new IOException("Invalid bridge payload size");
        }
        int bodyLength = payload.length - SIGNATURE_BYTES;
        byte[] body = java.util.Arrays.copyOf(payload, bodyLength);
        byte[] signature = java.util.Arrays.copyOfRange(payload, bodyLength, payload.length);
        if (!MessageDigest.isEqual(signature, sign(body, secret))) {
            throw new GeneralSecurityException("Invalid bridge signature");
        }

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(body))) {
            if (input.readInt() != MAGIC || input.readByte() != VERSION) throw new IOException("Unsupported bridge protocol");
            BanRequest request = new BanRequest(
                    new UUID(input.readLong(), input.readLong()), input.readLong(), readField(input),
                    new UUID(input.readLong(), input.readLong()), readField(input), readField(input), input.readInt()
            );
            if (input.available() != 0 || request.violations() < 1) throw new IOException("Invalid bridge request");
            return request;
        }
    }

    private static byte[] encodeBody(BanRequest request) throws IOException {
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
        if (bytes.length == 0 || bytes.length > 128) throw new IOException("Invalid bridge field length");
        output.writeByte(bytes.length);
        output.write(bytes);
    }

    private static String readField(DataInputStream input) throws IOException {
        int length = input.readUnsignedByte();
        if (length == 0 || length > 128 || input.available() < length) throw new IOException("Invalid bridge field length");
        byte[] bytes = input.readNBytes(length);
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))) throw new IOException("Invalid bridge UTF-8");
        return value;
    }
}
