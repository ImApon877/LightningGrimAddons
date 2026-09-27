package ac.grim.lightninggrim.bridge;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BanBridgeTest {
    private static final byte[] SECRET = Base64.getDecoder().decode("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY");
    private static final UUID PLAYER_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final long NOW = 1_700_000_000_000L;
    private static BridgeProtocol.BanRequest request(UUID requestId, long issuedAt, String backendId,
                                                     String playerName, String checkKey, int violations) {
        return new BridgeProtocol.BanRequest(requestId, issuedAt, backendId, PLAYER_ID, playerName, checkKey, violations);
    }    private static byte[] signBody(byte[] body) throws GeneralSecurityException {
        // Re-use encode()'s MAC by signing through a roundabout: encode a
        // request, then swap its body for ours. Simpler: use the package's
        // own HMAC via encode on a dummy and not possible — so replicate the
        // MAC here with the same algorithm.
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(SECRET, "HmacSHA256"));
            return mac.doFinal(body);
        } catch (GeneralSecurityException e) {
            throw e;
        }
    }

    private static byte[] craftBody(java.util.function.Consumer<DataOutputStream> writer) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        writer.accept(out);
        return bytes.toByteArray();
    }

    // ---------- protocol ----------

    @Test
    void roundTripPreservesFields() throws Exception {
        BridgeProtocol.BanRequest original =
                request(UUID.randomUUID(), NOW, "survival", "Notch", "Simulation(grim:simulation)", 42);
        byte[] payload = BridgeProtocol.encode(original, SECRET);

        BridgeProtocol.BanRequest decoded = BridgeProtocol.decode(payload, SECRET, NOW);
        assertEquals(original.requestId(), decoded.requestId());
        assertEquals(original.issuedAt(), decoded.issuedAt());
        assertEquals(original.backendId(), decoded.backendId());
        assertEquals(original.playerId(), decoded.playerId());
        assertEquals(original.playerName(), decoded.playerName());
        assertEquals(original.checkKey(), decoded.checkKey());
        assertEquals(original.violations(), decoded.violations());
    }

    @Test
    void decodeWithZeroNowSkipsClockSanity() throws Exception {
        BridgeProtocol.BanRequest original = request(UUID.randomUUID(), NOW, "survival", "Notch", "Reach", 1);
        BridgeProtocol.BanRequest decoded = BridgeProtocol.decode(BridgeProtocol.encode(original, SECRET), SECRET, 0);
        assertEquals(original.requestId(), decoded.requestId());
    }

    @Test
    void tamperedBodyIsRejected() throws Exception {
        byte[] payload = BridgeProtocol.encode(request(UUID.randomUUID(), NOW, "survival", "Notch", "Reach", 1), SECRET);
        payload[payload.length - 40] ^= 0x01; // flip a bit inside the body
        assertThrows(GeneralSecurityException.class, () -> BridgeProtocol.decode(payload, SECRET, NOW));
    }

    @Test
    void tamperedSignatureIsRejected() throws Exception {
        byte[] payload = BridgeProtocol.encode(request(UUID.randomUUID(), NOW, "survival", "Notch", "Reach", 1), SECRET);
        payload[payload.length - 1] ^= 0x01;
        assertThrows(GeneralSecurityException.class, () -> BridgeProtocol.decode(payload, SECRET, NOW));
    }

    @Test
    void wrongSecretIsRejected() throws Exception {
        byte[] payload = BridgeProtocol.encode(request(UUID.randomUUID(), NOW, "survival", "Notch", "Reach", 1), SECRET);
        byte[] otherSecret = new byte[32];
        otherSecret[0] = 1;
        assertThrows(GeneralSecurityException.class, () -> BridgeProtocol.decode(payload, otherSecret, NOW));
    }

    @Test
    void truncatedAndOversizedPayloadsAreRejected() throws Exception {
        byte[] payload = BridgeProtocol.encode(request(UUID.randomUUID(), NOW, "survival", "Notch", "Reach", 1), SECRET);
        assertThrows(IOException.class, () -> BridgeProtocol.decode(new byte[0], SECRET, NOW));
        assertThrows(IOException.class, () -> BridgeProtocol.decode(new byte[32], SECRET, NOW));
        assertThrows(IOException.class, () -> BridgeProtocol.decode(new byte[BridgeProtocol.MAX_PAYLOAD_BYTES + 1], SECRET, NOW));

        byte[] truncated = new byte[payload.length - 1];
        System.arraycopy(payload, 0, truncated, 0, truncated.length);
        // Truncation cuts the MAC, so the signature check rejects first.
        assertThrows(GeneralSecurityException.class, () -> BridgeProtocol.decode(truncated, SECRET, NOW));
    }

    @Test
    void trailingBytesAreRejected() throws Exception {
        byte[] payload = BridgeProtocol.encode(request(UUID.randomUUID(), NOW, "survival", "Notch", "Reach", 1), SECRET);
        byte[] padded = new byte[payload.length + 1];
        System.arraycopy(payload, 0, padded, 0, payload.length);
        // Padding changes the MACed body, so the signature check rejects first.
        assertThrows(GeneralSecurityException.class, () -> BridgeProtocol.decode(padded, SECRET, NOW));
    }

    @Test
    void issuedAtSanityIsEnforcedAgainstNow() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] twoDaysOld = BridgeProtocol.encode(request(id, NOW - 2L * 24 * 60 * 60 * 1000, "survival", "Notch", "Reach", 1), SECRET);
        assertThrows(IOException.class, () -> BridgeProtocol.decode(twoDaysOld, SECRET, NOW));

        byte[] twoDaysAhead = BridgeProtocol.encode(request(id, NOW + 2L * 24 * 60 * 60 * 1000, "survival", "Notch", "Reach", 1), SECRET);
        assertThrows(IOException.class, () -> BridgeProtocol.decode(twoDaysAhead, SECRET, NOW));
    }

    @Test
    void encodeValidatesFields() {
        UUID id = UUID.randomUUID();
        assertThrows(IOException.class, () -> BridgeProtocol.encode(request(id, NOW, "bad backend!", "Notch", "Reach", 1), SECRET));
        assertThrows(IOException.class, () -> BridgeProtocol.encode(request(id, NOW, "survival", "Notch; drop table", "Reach", 1), SECRET));
        assertThrows(IOException.class, () -> BridgeProtocol.encode(request(id, NOW, "survival", "Notch", "", 1), SECRET));
        assertThrows(IOException.class, () -> BridgeProtocol.encode(request(id, NOW, "survival", "Notch", "x".repeat(129), 1), SECRET));
    }

    @Test
    void zeroAndHugeViolationsAreRejected() throws Exception {
        // violations is not covered by encode-side validation, so craft a
        // validly-signed body with a bogus count.
        UUID id = UUID.randomUUID();
        for (int violations : new int[]{0, -1, 1_000_001}) {
            byte[] body = craftBody(out -> {
                try {
                    out.writeInt(0x47524231);
                    out.writeByte(1);
                    out.writeLong(id.getMostSignificantBits());
                    out.writeLong(id.getLeastSignificantBits());
                    out.writeLong(NOW);
                    writeField(out, "survival");
                    out.writeLong(PLAYER_ID.getMostSignificantBits());
                    out.writeLong(PLAYER_ID.getLeastSignificantBits());
                    writeField(out, "Notch");
                    writeField(out, "Reach");
                    out.writeInt(violations);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            byte[] payload = new byte[body.length + 32];
            System.arraycopy(body, 0, payload, 0, body.length);
            System.arraycopy(signBody(body), 0, payload, body.length, 32);
            assertThrows(IOException.class, () -> BridgeProtocol.decode(payload, SECRET, NOW), "violations=" + violations);
        }
    }

    private static void writeField(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeByte(bytes.length);
        out.write(bytes);
    }

    // ---------- bridge gating ----------

    private static BridgeWithLog bridge(String command) {
        return new BridgeWithLog(command);
    }

    /** Records the dispatched command instead of executing it. */
    static final class BridgeWithLog extends BanBridge {
        final AtomicReference<String> dispatched = new AtomicReference<>();

        BridgeWithLog(String command) {
            apply(BanBridge.parseSettings(Map.of(
                    "enabled", "true",
                    "hmacSecretBase64", Base64.getEncoder().encodeToString(SECRET),
                    "allowedBackends", "survival,skyblock",
                    "requestTtlMs", "10000",
                    "replayTtlMs", "60000",
                    "banCommand", command)).settings());
        }

        @Override
        protected boolean dispatch(String command, BridgeProtocol.BanRequest request, String backend) {
            dispatched.set(command);
            return true;
        }
    }

    @Test
    void validRequestDispatchesCommand() {
        BridgeWithLog b = bridge("ban %player% cheating");
        UUID id = UUID.randomUUID();
        byte[] payload;
        try {
            payload = BridgeProtocol.encode(request(id, NOW, "survival", "Notch", "Reach", 3), SECRET);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertTrue(b.handlePayload("survival", payload, NOW));
        assertEquals("ban Notch cheating", b.dispatched.get());
    }

    @Test
    void replayedRequestIsIgnored() throws Exception {
        BridgeWithLog b = bridge("ban %player%");
        byte[] payload = BridgeProtocol.encode(request(UUID.randomUUID(), NOW, "survival", "Notch", "Reach", 3), SECRET);
        assertTrue(b.handlePayload("survival", payload, NOW));
        assertFalse(b.handlePayload("survival", payload, NOW));
        assertEquals("ban Notch", b.dispatched.get());
    }

    @Test
    void expiredAndFutureRequestsAreRejected() throws Exception {
        BridgeWithLog b = bridge("ban %player%");
        byte[] expired = BridgeProtocol.encode(request(UUID.randomUUID(), NOW - 20_000, "survival", "Notch", "Reach", 3), SECRET);
        byte[] future = BridgeProtocol.encode(request(UUID.randomUUID(), NOW + 20_000, "survival", "Notch", "Reach", 3), SECRET);
        assertFalse(b.handlePayload("survival", expired, NOW));
        assertFalse(b.handlePayload("survival", future, NOW));
        assertNull(b.dispatched.get());
    }

    @Test
    void backendClaimMustMatchConnection() throws Exception {
        BridgeWithLog b = bridge("ban %player%");
        byte[] payload = BridgeProtocol.encode(request(UUID.randomUUID(), NOW, "skyblock", "Notch", "Reach", 3), SECRET);
        assertFalse(b.handlePayload("survival", payload, NOW));
    }

    @Test
    void unknownBackendIsRejectedWithoutDecode() throws Exception {
        BridgeWithLog b = bridge("ban %player%");
        byte[] payload = BridgeProtocol.encode(request(UUID.randomUUID(), NOW, "hacked", "Notch", "Reach", 3), SECRET);
        assertFalse(b.handlePayload("hacked", payload, NOW));
    }

    @Test
    void malformedPayloadIsSwallowed() {
        BridgeWithLog b = bridge("ban %player%");
        assertFalse(b.handlePayload("survival", null, NOW));
        assertFalse(b.handlePayload("survival", new byte[5], NOW));
        assertFalse(b.handlePayload("survival", "garbage".getBytes(StandardCharsets.UTF_8), NOW));
    }

    @Test
    void replayCacheEvictsExpiredEntries() throws Exception {
        BridgeWithLog b = bridge("ban %player%");
        byte[] payload = BridgeProtocol.encode(request(UUID.randomUUID(), NOW, "survival", "Notch", "Reach", 3), SECRET);
        assertTrue(b.handlePayload("survival", payload, NOW));
        assertEquals(1, b.trackedRequests());

        // Replay within the TTL is rejected and does not grow the cache.
        assertFalse(b.handlePayload("survival", payload, NOW + 1_000));
        assertEquals(1, b.trackedRequests());

        // A fresh request ~60s later evicts the expired entry on insert.
        // (The old payload itself can never re-pass the 10s request TTL.)
        byte[] later = BridgeProtocol.encode(request(UUID.randomUUID(), NOW + 60_000, "survival", "Notch", "Reach", 3), SECRET);
        assertTrue(b.handlePayload("survival", later, NOW + 60_001));
        assertEquals(1, b.trackedRequests());
        assertFalse(b.handlePayload("survival", later, NOW + 60_002));
    }

    // ---------- settings parsing ----------

    private static Map<String, String> goodProps() {
        return Map.of(
                "enabled", "true",
                "hmacSecretBase64", Base64.getEncoder().encodeToString(SECRET),
                "allowedBackends", "survival, skyblock",
                "requestTtlMs", "10000",
                "replayTtlMs", "60000",
                "banCommand", "litebans:ban %player% Automated LightningGrim network ban");
    }

    @Test
    void goodSettingsParse() {
        BanBridge.ConfigResult result = BanBridge.parseSettings(goodProps());
        assertNull(result.error());
        assertTrue(result.settings().enabled());
        assertEquals(2, result.settings().allowedBackends().size());
        assertArrayEquals(SECRET, result.settings().secret());
    }

    @Test
    void disabledFlagShortCircuitsValidation() {
        BanBridge.ConfigResult result = BanBridge.parseSettings(Map.of("enabled", "false"));
        assertNull(result.error());
        assertFalse(result.settings().enabled());
    }

    @Test
    void invalidSettingsProduceErrors() {
        assertNull(BanBridge.parseSettings(goodProps()).error());

        Map<String, String> shortSecret = Map.of(
                "enabled", "true",
                "hmacSecretBase64", Base64.getEncoder().encodeToString(new byte[16]),
                "allowedBackends", "survival",
                "banCommand", "ban %player%");
        assertNotEquals(null, BanBridge.parseSettings(shortSecret).error());

        Map<String, String> badBase64 = Map.of(
                "enabled", "true",
                "hmacSecretBase64", "!!!not base64!!!",
                "allowedBackends", "survival",
                "banCommand", "ban %player%");
        assertNotEquals(null, BanBridge.parseSettings(badBase64).error());

        Map<String, String> noBackends = Map.of(
                "enabled", "true",
                "hmacSecretBase64", Base64.getEncoder().encodeToString(SECRET),
                "allowedBackends", "",
                "banCommand", "ban %player%");
        assertNotEquals(null, BanBridge.parseSettings(noBackends).error());

        Map<String, String> noPlaceholder = Map.of(
                "enabled", "true",
                "hmacSecretBase64", Base64.getEncoder().encodeToString(SECRET),
                "allowedBackends", "survival",
                "banCommand", "ban everyone");
        assertNotEquals(null, BanBridge.parseSettings(noPlaceholder).error());

        Map<String, String> invertedTtls = Map.of(
                "enabled", "true",
                "hmacSecretBase64", Base64.getEncoder().encodeToString(SECRET),
                "allowedBackends", "survival",
                "requestTtlMs", "60000",
                "replayTtlMs", "10000",
                "banCommand", "ban %player%");
        assertNotEquals(null, BanBridge.parseSettings(invertedTtls).error());
    }

    @Test
    void defaultConfigTemplateMatchesDocumentedKeys() {
        for (String key : new String[]{"enabled", "hmacSecretBase64", "allowedBackends", "requestTtlMs", "replayTtlMs", "banCommand"}) {
            assertTrue(BanBridge.DEFAULT_CONFIG.contains(key + "="), key);
        }
    }
}
