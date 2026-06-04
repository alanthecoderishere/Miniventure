package com.miniv.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SupabaseMultiplayer — MiniStudy 0.4.0 (Edisi Edukasi Kelulusan)
 *
 * Mengelola koneksi Supabase Realtime Broadcast melalui WebSocket standar Java
 * 11+.
 * Tidak memerlukan library eksternal selain yang sudah ada di proyek.
 *
 * Cara pakai di Main.java:
 * init() → SupabaseMultiplayer.connectAsync("YOUR_ID", "NamaKamu");
 * loop() → jika player bergerak, panggil
 * SupabaseMultiplayer.broadcastPosition(...)
 * tutup → SupabaseMultiplayer.disconnect();
 */
public class SupabaseMultiplayer {

    // ─────────────────────────────────────────────────────────────────────────
    // ★ KONFIGURASI — Ganti dengan kredensial Supabase kamu
    // ─────────────────────────────────────────────────────────────────────────
    public static final String SUPABASE_PROJECT_REF = "ktnbpcksepwzwbowrylq";
    public static final String SUPABASE_ANON_KEY = "sb_publishable_k1bG9JUK3d-i7JjjXidUjw_pGBFBKQj";
    private static final String CHANNEL_NAME = "room:graduation";
    private static final String TOPIC = "realtime:" + CHANNEL_NAME;

    // Heartbeat interval (detik) — Supabase memutus koneksi idle setelah ~60 detik
    private static final int HEARTBEAT_INTERVAL_SEC = 20;
    // Delay reconnect saat putus (milidetik)
    private static final int RECONNECT_DELAY_MS = 5_000;

    // ─────────────────────────────────────────────────────────────────────────
    // ★ STATE GLOBAL — bisa diakses dari mana saja di game
    // ─────────────────────────────────────────────────────────────────────────

    /** Akumulasi poin dari event quiz_correct semua pemain. */
    public static volatile int globalScoreCount = 0;

    /** Peta pemain remote yang sedang online: id → RemotePlayer. */
    public static final ConcurrentHashMap<String, RemotePlayer> remotePlayers = new ConcurrentHashMap<>();

    public static class BlockUpdate {
        public int x, y, z;
        public byte b;

        public BlockUpdate(int x, int y, int z, byte b) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.b = b;
        }
    }

    public static final ConcurrentLinkedQueue<BlockUpdate> pendingBlocks = new ConcurrentLinkedQueue<>();
    public static final ConcurrentLinkedQueue<String> pendingChats = new ConcurrentLinkedQueue<>();
    /** Join/leave and other MP notices shown in the in-game chat box. */
    public static final ConcurrentLinkedQueue<String> pendingNotifications = new ConcurrentLinkedQueue<>();

    /**
     * Thread-safe queue: Network Thread menulis, Main/Render Thread membaca.
     * Pola ini (Lock-Free Command Pattern) menghindari synchronized block
     * yang dapat memblokir LWJGL render thread.
     */
    public static class PlayerMoveEvent {
        final String id, name, dir, state;
        final double x, y, z;
        PlayerMoveEvent(String id, String name, double x, double y, double z, String dir, String state) {
            this.id = id; this.name = name;
            this.x = x; this.y = y; this.z = z;
            this.dir = dir; this.state = state;
        }
    }
    public static final ConcurrentLinkedQueue<PlayerMoveEvent> pendingMoves = new ConcurrentLinkedQueue<>();

    /** All block changes this session — used for late-join sync and chunk generation. */
    public static final ConcurrentHashMap<String, Byte> sessionBlocks = new ConcurrentHashMap<>();

    private static volatile boolean worldSyncRequested = false;
    private static volatile long lastSyncResponseMs = 0;
    private static final int SYNC_BATCH_SIZE = 80;

    // ─────────────────────────────────────────────────────────────────────────
    // Internal state
    // ─────────────────────────────────────────────────────────────────────────
    private static volatile WebSocket ws;
    private static final AtomicInteger refSeq = new AtomicInteger(1);
    private static volatile boolean connected = false;
    private static volatile boolean shutdown = false;
    private static ScheduledExecutorService heartbeat;

    /** Identitas pemain lokal, di-set saat connectAsync(). */
    private static String localId;
    public static String localName;

    // ─────────────────────────────────────────────────────────────────────────
    // Data class pemain remote
    // ─────────────────────────────────────────────────────────────────────────
    public static class RemotePlayer {
        public final String id;
        public volatile String name;
        public volatile double targetX, targetY, targetZ;
        public volatile double renderX, renderY, renderZ;
        public volatile String dir;
        public volatile String state;
        public volatile long lastSeenMs;
        public volatile boolean initialized;

        RemotePlayer(String id, String name, double x, double y, double z, String dir, String state) {
            this.id = id;
            this.name = name;
            this.targetX = x;
            this.targetY = y;
            this.targetZ = z;
            this.renderX = x;
            this.renderY = y;
            this.renderZ = z;
            this.initialized = true;
            this.dir = dir;
            this.state = state;
            this.lastSeenMs = System.currentTimeMillis();
        }
    }

    private static final long PLAYER_STALE_MS = 5000;

    // ═════════════════════════════════════════════════════════════════════════
    // 1. CONNECT ASYNC — jalankan di thread terpisah, aman dipanggil dari init()
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Memulai koneksi ke Supabase Realtime secara async (non-blocking).
     *
     * @param playerId   ID unik pemain lokal (misal UUID atau nama-hash)
     * @param playerName Nama tampilan pemain lokal
     */
    public static void connectAsync(String playerId, String playerName) {
        localId = playerId;
        localName = playerName;
        shutdown = false;
        worldSyncRequested = false;
        spawnConnectThread();
    }

    private static void spawnConnectThread() {
        Thread t = new Thread(() -> {
            try {
                String url = "wss://" + SUPABASE_PROJECT_REF
                        + ".supabase.co/realtime/v1/websocket"
                        + "?apikey=" + SUPABASE_ANON_KEY
                        + "&vsn=1.0.0";

                HttpClient client = HttpClient.newHttpClient();
                ws = client.newWebSocketBuilder()
                        .buildAsync(URI.create(url), new RealtimeListener())
                        .get(15, TimeUnit.SECONDS);

                // Tunggu sampai koneksi benar-benar dipakai (listener onOpen yang action)

            } catch (Exception e) {
                System.err.println("[Server] Failed To Connect: " + e.getMessage());
                scheduleReconnect();
            }
        }, "MP-Connect");
        t.setDaemon(true);
        t.start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // WebSocket Listener (private inner class)
    // ═════════════════════════════════════════════════════════════════════════
    private static final class RealtimeListener implements WebSocket.Listener {

        private final StringBuilder buf = new StringBuilder();

        @Override
        public void onOpen(WebSocket socket) {
            connected = true;
            System.out.println("[Server] Connected to server");
            socket.request(1);
            sendJoin(socket);
            startHeartbeat();
        }

        @Override
        public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                dispatchMessage(buf.toString());
                buf.setLength(0);
            }
            socket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket socket, Throwable err) {
            System.err.println("[Server] Error: " + err.getMessage());
            onDisconnected();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
            System.out.println("[server] Connection Closed (" + code + "): " + reason);
            onDisconnected();
            return null;
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Channel join & heartbeat
    // ═════════════════════════════════════════════════════════════════════════

    private static void sendJoin(WebSocket socket) {
        // Phoenix phx_join dengan config broadcast self:true
        String msg = "{"
                + "\"topic\":\"" + TOPIC + "\","
                + "\"event\":\"phx_join\","
                + "\"payload\":{\"config\":{\"broadcast\":{\"self\":true}}},"
                + "\"ref\":\"" + refSeq.getAndIncrement() + "\""
                + "}";
        socket.sendText(msg, true);
        System.out.println("[Server] Connecting to: " + CHANNEL_NAME);
    }

    private static void startHeartbeat() {
        if (heartbeat != null)
            heartbeat.shutdownNow();
        heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MP-Heartbeat");
            t.setDaemon(true);
            return t;
        });
        heartbeat.scheduleAtFixedRate(() -> {
            if (connected && ws != null) {
                String hb = "{"
                        + "\"topic\":\"phoenix\","
                        + "\"event\":\"heartbeat\","
                        + "\"payload\":{},"
                        + "\"ref\":\"" + refSeq.getAndIncrement() + "\""
                        + "}";
                ws.sendText(hb, true);
            }
        }, HEARTBEAT_INTERVAL_SEC, HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    private static void onDisconnected() {
        connected = false;
        if (heartbeat != null)
            heartbeat.shutdownNow();
        if (!shutdown)
            scheduleReconnect();
    }

    private static void scheduleReconnect() {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(RECONNECT_DELAY_MS);
            } catch (InterruptedException ignored) {
            }
            if (!shutdown) {
                System.out.println("[Server] Trying To Reconnect...");
                spawnConnectThread();
            }
        }, "MP-Reconnect");
        t.setDaemon(true);
        t.start();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 2. HANDLER — menerima dan memproses pesan masuk
    // ═════════════════════════════════════════════════════════════════════════

    private static void dispatchMessage(String raw) {
        // Hanya proses broadcast dari channel kita
        if (!raw.contains(TOPIC) || !raw.contains("\"broadcast\""))
            return;

        // Ekstrak outer payload (Phoenix envelope)
        String outerPayload = extractBlock(raw, "payload");
        if (outerPayload == null)
            return;

        // Ekstrak inner payload (isi pesan kita)
        String innerPayload = extractBlock(outerPayload, "payload");
        if (innerPayload == null)
            innerPayload = outerPayload;

        String type = extractString(innerPayload, "type");
        if (type == null)
            return;

        switch (type) {
            case "pos":
                handlePlayerPos(innerPayload);
                break;
            case "block":
                handleBlock(innerPayload);
                break;
            case "quiz_correct":
                handleQuizCorrect(innerPayload);
                break;
            case "chat":
                handleChat(innerPayload);
                break;
            case "sync_request":
                handleSyncRequest(innerPayload);
                break;
            case "block_batch":
                handleBlockBatch(innerPayload);
                break;
            case "leave":
                handleLeave(innerPayload);
                break;
            case "teleport":
                handleTeleport(innerPayload);
                break;
            case "teleport_remove":
                handleTeleportRemove(innerPayload);
                break;
            case "teleport_batch":
                handleTeleportBatch(innerPayload);
                break;
            case "info":
                handleInfo(innerPayload);
                break;
            case "info_remove":
                handleInfoRemove(innerPayload);
                break;
            case "info_batch":
                handleInfoBatch(innerPayload);
                break;
            case "npc_spawn":
                handleNpcSpawn(innerPayload);
                break;
            case "npc_remove":
                handleNpcRemove(innerPayload);
                break;
            case "npc_batch":
                handleNpcBatch(innerPayload);
                break;
            case "npc_data":
                handleNpcData(innerPayload);
                break;
            case "action":
                handleAction(innerPayload);
                break;
        }
    }

    private static void handleAction(String json) {
        String actionType = extractString(json, "actionType");
        String actionData = extractString(json, "actionData");
        if (actionType == null || actionData == null) return;
        if ("teleport_pad".equals(actionType)) {
            parseTeleportData(actionData);
        }
    }

    private static void parseTeleportData(String data) {
        String[] p = data.split(",");
        if (p.length != 6) return;
        try {
            TeleportBlockManager.applyRemote(
                    Integer.parseInt(p[0].trim()),
                    Integer.parseInt(p[1].trim()),
                    Integer.parseInt(p[2].trim()),
                    Float.parseFloat(p[3].trim()),
                    Float.parseFloat(p[4].trim()),
                    Float.parseFloat(p[5].trim()));
        } catch (NumberFormatException ignored) {
        }
    }

    private static void handleTeleport(String json) {
        Integer x = extractInt(json, "x");
        Integer y = extractInt(json, "y");
        Integer z = extractInt(json, "z");
        Double tx = extractDouble(json, "tx");
        Double ty = extractDouble(json, "ty");
        Double tz = extractDouble(json, "tz");
        if (x == null || y == null || z == null || tx == null || ty == null || tz == null) return;
        TeleportBlockManager.applyRemote(x, y, z, tx.floatValue(), ty.floatValue(), tz.floatValue());
    }

    private static void handleTeleportRemove(String json) {
        Integer x = extractInt(json, "x");
        Integer y = extractInt(json, "y");
        Integer z = extractInt(json, "z");
        if (x == null || y == null || z == null) return;
        TeleportBlockManager.applyRemoteRemove(x, y, z);
    }

    private static void handleTeleportBatch(String json) {
        String data = extractString(json, "data");
        if (data == null || data.isEmpty()) return;
        for (String entry : data.split(";")) {
            if (!entry.isEmpty()) parseTeleportData(entry);
        }
    }

    public static void broadcastTeleport(int x, int y, int z, float tx, float ty, float tz) {
        if (!connected || ws == null) return;
        String payload = "{\"type\":\"teleport\",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z
                + ",\"tx\":" + tx + ",\"ty\":" + ty + ",\"tz\":" + tz + "}";
        sendBroadcastEnvelope("teleport", payload);
    }

    public static void broadcastTeleportRemove(int x, int y, int z) {
        if (!connected || ws == null) return;
        String payload = "{\"type\":\"teleport_remove\",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + "}";
        sendBroadcastEnvelope("teleport_remove", payload);
    }

    private static void notifyPlayerJoined(String name) {
        String display = name != null && !name.isEmpty() ? name : "???";
        String msg = display + " joined the game";
        pendingNotifications.add(msg);
        System.out.println("[Server] " + msg);
    }

    private static void notifyPlayerLeft(String name) {
        String display = name != null && !name.isEmpty() ? name : "???";
        String msg = display + " left the game";
        pendingNotifications.add(msg);
        System.out.println("[Server] " + msg);
    }

    private static void handleLeave(String json) {
        String id = extractString(json, "id");
        if (id == null || id.equals(localId))
            return;
        RemotePlayer removed = remotePlayers.remove(id);
        if (removed != null) {
            notifyPlayerLeft(removed.name);
        }
    }

    /** Remove remote players who stopped sending updates (crash / force-quit). */
    public static void pruneStalePlayers() {
        long now = System.currentTimeMillis();
        var it = remotePlayers.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (now - e.getValue().lastSeenMs > PLAYER_STALE_MS) {
                notifyPlayerLeft(e.getValue().name);
                it.remove();
            }
        }
    }

    private static String blockKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public static void recordBlock(int x, int y, int z, byte blockType) {
        sessionBlocks.put(blockKey(x, y, z), blockType);
    }

    /** Call once after connecting so existing players send their placed blocks. */
    public static void requestWorldSync() {
        if (!connected || ws == null || localId == null || worldSyncRequested)
            return;
        worldSyncRequested = true;
        String payload = "{\"type\":\"sync_request\",\"id\":\"" + esc(localId) + "\"}";
        sendBroadcastEnvelope("sync_request", payload);
    }

    private static void handleSyncRequest(String json) {
        String reqId = extractString(json, "id");
        if (reqId == null || reqId.equals(localId))
            return;
        
        // Broadcast local position so the new player sees us immediately
        if (lastBroadcastDir != null && lastBroadcastState != null) {
            broadcastPosition(lastBroadcastX, lastBroadcastY, lastBroadcastZ, lastBroadcastDir, lastBroadcastState);
        }
        
        long now = System.currentTimeMillis();
        if (now - lastSyncResponseMs < 2000)
            return;
        lastSyncResponseMs = now;
        sendBlockBatches();
        sendTeleportBatches();
        sendInfoBatches();
        sendNpcBatches();
    }

    private static void handleNpcSpawn(String json) {
        String templateId = extractString(json, "template");
        Integer x = extractInt(json, "x");
        Integer y = extractInt(json, "y");
        Integer z = extractInt(json, "z");
        if (templateId == null || x == null || y == null || z == null) return;
        NPCManager.applyRemoteSpawn(templateId, x, y, z);
    }

    private static void handleNpcRemove(String json) {
        Integer x = extractInt(json, "x");
        Integer y = extractInt(json, "y");
        Integer z = extractInt(json, "z");
        if (x == null || y == null || z == null) return;
        NPCManager.applyRemoteRemove(x, y, z);
    }

    private static void handleNpcBatch(String json) {
        String data = extractString(json, "data");
        if (data == null || data.isEmpty()) return;
        for (String entry : data.split(";;")) {
            if (!entry.isEmpty()) NPCManager.decodeSyncEntry(entry);
        }
    }

    public static void broadcastNpcSpawn(String templateId, int x, int y, int z) {
        if (!connected || ws == null) return;
        String payload = "{\"type\":\"npc_spawn\",\"template\":\"" + esc(templateId)
                + "\",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + "}";
        sendBroadcastEnvelope("npc_spawn", payload);
    }

    public static void broadcastNpcRemove(int x, int y, int z) {
        if (!connected || ws == null) return;
        String payload = "{\"type\":\"npc_remove\",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + "}";
        sendBroadcastEnvelope("npc_remove", payload);
    }

    private static void handleNpcData(String json) {
        Integer x = extractInt(json, "x");
        Integer y = extractInt(json, "y");
        Integer z = extractInt(json, "z");
        String name = extractString(json, "name");
        String dialog = extractString(json, "dialog");
        String buttons = extractString(json, "buttons");
        if (x == null || y == null || z == null) return;
        String[] dialogLines = dialog == null ? new String[0] : dialog.split("\n", -1);
        String[] labels = buttons == null || buttons.isEmpty() ? new String[0] : buttons.split(";", -1);
        boolean locked = extractInt(json, "locked") != null && extractInt(json, "locked") == 1;
        NPCManager.applyRemoteData(x, y, z, name, dialogLines, labels, locked);
    }

    public static void broadcastNpcData(int x, int y, int z, String name, String[] dialogLines,
                                        String[] buttonLabels, boolean locked) {
        if (!connected || ws == null) return;
        String dialog = dialogLines == null ? "" : String.join("\n", dialogLines);
        StringBuilder labels = new StringBuilder();
        if (buttonLabels != null) {
            for (int i = 0; i < buttonLabels.length; i++) {
                if (i > 0) labels.append(';');
                labels.append(buttonLabels[i] == null ? "" : buttonLabels[i]);
            }
        }
        String payload = "{\"type\":\"npc_data\",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z
                + ",\"name\":\"" + esc(name == null ? "" : name) + "\""
                + ",\"dialog\":\"" + esc(dialog) + "\""
                + ",\"buttons\":\"" + esc(labels.toString()) + "\""
                + ",\"locked\":" + (locked ? 1 : 0) + "}";
        sendBroadcastEnvelope("npc_data", payload);
    }

    private static void sendNpcBatches() {
        String all = NPCManager.encodeSyncData();
        if (all.isEmpty()) return;
        String[] entries = all.split(";;");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String entry : entries) {
            if (entry.isEmpty()) continue;
            if (count > 0) sb.append(";;");
            sb.append(entry);
            count++;
            if (count >= 15) {
                flushNpcBatch(sb.toString());
                sb.setLength(0);
                count = 0;
            }
        }
        if (count > 0) flushNpcBatch(sb.toString());
    }

    private static void flushNpcBatch(String data) {
        String payload = "{\"type\":\"npc_batch\",\"data\":\"" + esc(data) + "\"}";
        sendBroadcastEnvelope("npc_batch", payload);
    }

    private static void handleInfo(String json) {
        Integer x = extractInt(json, "x");
        Integer y = extractInt(json, "y");
        Integer z = extractInt(json, "z");
        String msg = extractString(json, "msg");
        if (x == null || y == null || z == null || msg == null) return;
        InfoBlockManager.applyRemote(x, y, z, msg);
    }

    private static void handleInfoRemove(String json) {
        Integer x = extractInt(json, "x");
        Integer y = extractInt(json, "y");
        Integer z = extractInt(json, "z");
        if (x == null || y == null || z == null) return;
        InfoBlockManager.applyRemoteRemove(x, y, z);
    }

    private static void handleInfoBatch(String json) {
        String data = extractString(json, "data");
        if (data == null || data.isEmpty()) return;
        for (String entry : data.split(";;")) {
            if (!entry.isEmpty()) InfoBlockManager.decodeEntry(entry);
        }
    }

    public static void broadcastInfo(int x, int y, int z, String text) {
        if (!connected || ws == null) return;
        String payload = "{\"type\":\"info\",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z
                + ",\"msg\":\"" + esc(text) + "\"}";
        sendBroadcastEnvelope("info", payload);
    }

    public static void broadcastInfoRemove(int x, int y, int z) {
        if (!connected || ws == null) return;
        String payload = "{\"type\":\"info_remove\",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + "}";
        sendBroadcastEnvelope("info_remove", payload);
    }

    private static void sendInfoBatches() {
        String all = InfoBlockManager.encodeSyncData();
        if (all.isEmpty()) return;
        String[] entries = all.split(";;");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String entry : entries) {
            if (entry.isEmpty()) continue;
            if (count > 0) sb.append(";;");
            sb.append(entry);
            count++;
            if (count >= 15) {
                flushInfoBatch(sb.toString());
                sb.setLength(0);
                count = 0;
            }
        }
        if (count > 0) flushInfoBatch(sb.toString());
    }

    private static void flushInfoBatch(String data) {
        String payload = "{\"type\":\"info_batch\",\"data\":\"" + esc(data) + "\"}";
        sendBroadcastEnvelope("info_batch", payload);
    }

    private static void sendTeleportBatches() {
        String all = TeleportBlockManager.encodeSyncData();
        if (all.isEmpty())
            return;
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String entry : all.split(";")) {
            if (entry.isEmpty()) continue;
            if (count > 0) sb.append(';');
            sb.append(entry);
            count++;
            if (count >= SYNC_BATCH_SIZE) {
                flushTeleportBatch(sb.toString());
                sb.setLength(0);
                count = 0;
            }
        }
        if (count > 0)
            flushTeleportBatch(sb.toString());
    }

    private static void flushTeleportBatch(String data) {
        String payload = "{\"type\":\"teleport_batch\",\"data\":\"" + esc(data) + "\"}";
        sendBroadcastEnvelope("teleport_batch", payload);
    }

    private static void handleBlockBatch(String json) {
        String data = extractString(json, "data");
        if (data == null || data.isEmpty())
            return;
        for (String entry : data.split(";")) {
            String[] p = entry.split(",");
            if (p.length != 4)
                continue;
            try {
                int x = Integer.parseInt(p[0].trim());
                int y = Integer.parseInt(p[1].trim());
                int z = Integer.parseInt(p[2].trim());
                byte b = (byte) Integer.parseInt(p[3].trim());
                recordBlock(x, y, z, b);
                pendingBlocks.add(new BlockUpdate(x, y, z, b));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    private static void sendBlockBatches() {
        if (sessionBlocks.isEmpty())
            return;
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (var e : sessionBlocks.entrySet()) {
            if (count > 0)
                sb.append(';');
            sb.append(e.getKey()).append(',').append(e.getValue() & 0xFF);
            count++;
            if (count >= SYNC_BATCH_SIZE) {
                flushBlockBatch(sb.toString());
                sb.setLength(0);
                count = 0;
            }
        }
        if (count > 0)
            flushBlockBatch(sb.toString());
    }

    private static void flushBlockBatch(String data) {
        String payload = "{\"type\":\"block_batch\",\"data\":\"" + esc(data) + "\"}";
        sendBroadcastEnvelope("block_batch", payload);
    }

    private static void sendBroadcastEnvelope(String event, String innerPayload) {
        if (!connected || ws == null)
            return;
        String msg = "{"
                + "\"topic\":\"" + TOPIC + "\","
                + "\"event\":\"broadcast\","
                + "\"payload\":{"
                + "\"type\":\"broadcast\","
                + "\"event\":\"" + event + "\","
                + "\"payload\":" + innerPayload
                + "},"
                + "\"ref\":\"" + refSeq.getAndIncrement() + "\""
                + "}";
        ws.sendText(msg, true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Handler: Pergerakan pemain remote
    // ─────────────────────────────────────────────────────────────────────────
    // Paket:
    // {"type":"pos","id":"string","name":"string","x":double,"y":double,"z":double,"dir":"string","state":"string"}
    private static void handlePlayerPos(String json) {
        // [THREAD-SAFE] Network Thread hanya membaca JSON dan melempar event ke queue.
        // Ia TIDAK menyentuh remotePlayers secara langsung.
        // Main/Render Thread yang akan memprosesnya di updatePlayers().
        String id = extractString(json, "id");
        String name = extractString(json, "name");
        String dir = extractString(json, "dir");
        String state = extractString(json, "state");
        Double x = extractDouble(json, "x");
        Double y = extractDouble(json, "y");
        Double z = extractDouble(json, "z");

        if (id == null || x == null || y == null)
            return;
        if (id.equals(localId))
            return;

        double finalZ = (z != null) ? z : y;
        // Lempar ke antrian — ConcurrentLinkedQueue.add() adalah lock-free dan aman dari thread manapun.
        pendingMoves.add(new PlayerMoveEvent(id, name, x, y, finalZ, dir, state));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Handler: Perubahan blok dunia
    // ─────────────────────────────────────────────────────────────────────────
    // Paket: {"type":"block","x":int,"y":int,"z":int,"b":int}
    private static void handleBlock(String json) {
        Integer x = extractInt(json, "x");
        Integer y = extractInt(json, "y");
        Integer z = extractInt(json, "z");
        Integer b = extractInt(json, "b");
        if (x != null && y != null && z != null && b != null) {
            byte block = b.byteValue();
            recordBlock(x, y, z, block);
            pendingBlocks.add(new BlockUpdate(x, y, z, block));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Handler: Kuis kelompok — tambah poin ke globalScoreCount
    // ─────────────────────────────────────────────────────────────────────────
    // Paket: {"type":"quiz_correct","points":int}
    private static void handleQuizCorrect(String json) {
        Integer points = extractInt(json, "points");
        if (points == null)
            return;

        globalScoreCount += points;
        System.out.println("[Quiz] +" + points + " poin! Total skor: " + globalScoreCount);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Handler: Chat Message
    // ─────────────────────────────────────────────────────────────────────────
    private static void handleChat(String json) {
        String id = extractString(json, "id");
        if (id != null && id.equals(localId))
            return; // abaikan chat sendiri
        String name = extractString(json, "name");
        String message = extractString(json, "msg");
        if (name != null && message != null) {
            pendingChats.add(name + ": " + message);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 3. SENDER — kirim posisi pemain lokal saat bergerak
    // ═════════════════════════════════════════════════════════════════════════

    public static void updatePlayers(float delta) {
        // ── Fase 1: Drain queue dari Network Thread (Lock-Free, hanya di Main Thread) ──
        PlayerMoveEvent move;
        while ((move = pendingMoves.poll()) != null) {
            RemotePlayer rp = remotePlayers.get(move.id);
            if (rp != null) {
                // Update target — hanya field primitif, aman tanpa synchronized
                if (move.name != null) rp.name = move.name;
                rp.targetX = move.x;
                rp.targetY = move.y;
                rp.targetZ = move.z;
                if (move.dir != null) rp.dir = move.dir;
                if (move.state != null) rp.state = move.state;
                rp.lastSeenMs = System.currentTimeMillis();

                // Snap jika jarak terlalu jauh (misal: teleport)
                double dx = move.x - rp.renderX;
                double dy = move.y - rp.renderY;
                double dz = move.z - rp.renderZ;
                if (dx*dx + dy*dy + dz*dz > 25.0) {
                    rp.renderX = move.x;
                    rp.renderY = move.y;
                    rp.renderZ = move.z;
                }
            } else {
                // Player baru — daftarkan ke map
                String displayName = move.name != null ? move.name : "???";
                remotePlayers.put(move.id, new RemotePlayer(
                        move.id, displayName,
                        move.x, move.y, move.z,
                        move.dir != null ? move.dir : "down",
                        move.state != null ? move.state : "idle"));
                notifyPlayerJoined(displayName);
            }
        }

        // ── Fase 2: Lerp semua player render position menuju target (GC-Free, primitif) ──
        for (RemotePlayer rp : remotePlayers.values()) {
            if (!rp.initialized) continue;
            rp.renderX += (rp.targetX - rp.renderX) * 10.0f * delta;
            rp.renderY += (rp.targetY - rp.renderY) * 10.0f * delta;
            rp.renderZ += (rp.targetZ - rp.renderZ) * 10.0f * delta;
        }
    }

    public static double lastBroadcastX = 0, lastBroadcastY = 0, lastBroadcastZ = 0;
    public static String lastBroadcastDir = null, lastBroadcastState = null;

    /**
     * Broadcast koordinat pemain lokal ke semua pemain di channel.
     * Panggil ini di game loop setiap kali pemain bergerak.
     *
     * @param x     Posisi X dunia
     * @param y     Posisi Y dunia (tinggi)
     * @param z     Posisi Z dunia (kedalaman)
     * @param dir   Arah hadap: "up"/"down"/"left"/"right" atau nama animasi
     * @param state Status animasi: "idle"/"walk"/"run" dst.
     */
    public static void broadcastPosition(double x, double y, double z, String dir, String state) {
        if (!connected || ws == null || localId == null)
            return;
            
        lastBroadcastX = x;
        lastBroadcastY = y;
        lastBroadcastZ = z;
        lastBroadcastDir = dir;
        lastBroadcastState = state;

        // Inner payload
        String payload = "{"
                + "\"type\":\"pos\","
                + "\"id\":\"" + esc(localId) + "\","
                + "\"name\":\"" + esc(localName) + "\","
                + "\"x\":" + x + ","
                + "\"y\":" + y + ","
                + "\"z\":" + z + ","
                + "\"dir\":\"" + esc(dir) + "\","
                + "\"state\":\"" + esc(state) + "\""
                + "}";

        // Phoenix broadcast envelope
        String msg = "{"
                + "\"topic\":\"" + TOPIC + "\","
                + "\"event\":\"broadcast\","
                + "\"payload\":{"
                + "\"type\":\"broadcast\","
                + "\"event\":\"pos\","
                + "\"payload\":" + payload
                + "},"
                + "\"ref\":\"" + refSeq.getAndIncrement() + "\""
                + "}";

        ws.sendText(msg, true);
    }

    /**
     * Broadcast block placement / destruction
     */
    public static void broadcastBlock(int x, int y, int z, byte blockType) {
        recordBlock(x, y, z, blockType);
        if (!connected || ws == null)
            return;

        String payload = "{\"type\":\"block\",\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z + ",\"b\":" + blockType + "}";
        sendBroadcastEnvelope("block", payload);
    }

    /**
     * Kirim pesan quiz_correct (misalnya dari server/host kuis).
     * 
     * @param points Poin yang diperoleh
     */
    public static void broadcastQuizCorrect(int points) {
        if (!connected || ws == null)
            return;

        String payload = "{\"type\":\"quiz_correct\",\"points\":" + points + "}";
        String msg = "{"
                + "\"topic\":\"" + TOPIC + "\","
                + "\"event\":\"broadcast\","
                + "\"payload\":{"
                + "\"type\":\"broadcast\","
                + "\"event\":\"quiz_correct\","
                + "\"payload\":" + payload
                + "},"
                + "\"ref\":\"" + refSeq.getAndIncrement() + "\""
                + "}";

        ws.sendText(msg, true);
    }

    /**
     * Kirim pesan aksi kustom (kuis, absensi, dll)
     */
    public static void broadcastAction(String actionType, String actionData) {
        if (!connected || ws == null) return;
        
        String safeData = esc(actionData);
        String payload = "{"
            + "\"type\":\"action\","
            + "\"id\":\"" + esc(localId) + "\","
            + "\"name\":\"" + esc(localName) + "\","
            + "\"actionType\":\"" + esc(actionType) + "\","
            + "\"actionData\":\"" + safeData + "\""
            + "}";

        String msg = "{"
            + "\"topic\":\"" + TOPIC + "\","
            + "\"event\":\"broadcast\","
            + "\"payload\":{"
                + "\"type\":\"broadcast\","
                + "\"event\":\"action\","
                + "\"payload\":" + payload
            + "},"
            + "\"ref\":\"" + refSeq.getAndIncrement() + "\""
            + "}";

        ws.sendText(msg, true);
    }

    /**
     * Kirim pesan chat
     */
    public static void broadcastChat(String message) {

        String payload = "{"
                + "\"type\":\"chat\","
                + "\"id\":\"" + esc(localId) + "\","
                + "\"name\":\"" + esc(localName) + "\","
                + "\"msg\":\"" + esc(message) + "\""
                + "}";

        String msg = "{"
                + "\"topic\":\"" + TOPIC + "\","
                + "\"event\":\"broadcast\","
                + "\"payload\":{"
                + "\"type\":\"broadcast\","
                + "\"event\":\"chat\","
                + "\"payload\":" + payload
                + "},"
                + "\"ref\":\"" + refSeq.getAndIncrement() + "\""
                + "}";

        ws.sendText(msg, true);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Utility: disconnect saat game ditutup
    // ═════════════════════════════════════════════════════════════════════════

    /** Tell other clients this player is leaving (call before closing the socket). */
    public static void broadcastLeave() {
        if (!connected || ws == null || localId == null)
            return;
        String payload = "{\"type\":\"leave\",\"id\":\"" + esc(localId) + "\"}";
        sendBroadcastEnvelope("leave", payload);
        try {
            Thread.sleep(80);
        } catch (InterruptedException ignored) {
        }
    }

    /** Panggil saat game loop selesai (sebelum glfwTerminate). */
    public static void disconnect() {
        shutdown = true;
        broadcastLeave();
        connected = false;
        if (heartbeat != null)
            heartbeat.shutdownNow();
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "game_closed");
            } catch (Exception ignored) {
            }
        }
        remotePlayers.clear();
        System.out.println("[Server] Connection Closed");
    }

    /** True jika WebSocket aktif dan sudah join channel. */
    public static boolean isConnected() {
        return connected;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Mini JSON Parser — tanpa dependency eksternal
    // ═════════════════════════════════════════════════════════════════════════

    /** Ekstrak nilai string: "key":"value" */
    private static String extractString(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /** Ekstrak nilai double/float: "key":1.23 */
    private static Double extractDouble(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]*)?)");
        Matcher m = p.matcher(json);
        if (!m.find())
            return null;
        try {
            return Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Ekstrak nilai integer: "key":42 */
    private static Integer extractInt(String json, String key) {
        Pattern p = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?[0-9]+)");
        Matcher m = p.matcher(json);
        if (!m.find())
            return null;
        try {
            return Integer.parseInt(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Ekstrak blok JSON object { } untuk key tertentu.
     * Menangani nested objects dengan depth counting.
     */
    private static String extractBlock(String json, String key) {
        String search = "\"" + key + "\"";
        int ki = json.indexOf(search);
        if (ki < 0)
            return null;
        int ci = json.indexOf(':', ki + search.length());
        if (ci < 0)
            return null;
        int start = ci + 1;
        while (start < json.length() && json.charAt(start) == ' ')
            start++;
        if (start >= json.length() || json.charAt(start) != '{')
            return null;

        int depth = 0;
        boolean inStr = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\'))
                inStr = !inStr;
            if (!inStr) {
                if (c == '{')
                    depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0)
                        return json.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /** Escape karakter khusus JSON dalam string. */
    private static String esc(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
