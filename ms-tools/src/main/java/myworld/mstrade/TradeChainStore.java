package myworld.mstrade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TradeChainStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type DATA_TYPE = new TypeToken<LinkedHashMap<String, List<Entry>>>() {}.getType();
    private static final String FILE_NAME = "mstrade-chains.json";

    private TradeChainStore() {}

    public record Entry(String kind, String dimension, UUID uuid, double x, double y, double z, float yaw, float pitch) {
        public static Entry entity(Villager villager) {
            ServerLevel level = (ServerLevel) villager.level();
            return new Entry("entity", level.dimension().identifier().toString(), villager.getUUID(),
                villager.getX(), villager.getY(), villager.getZ(), 0.0F, 0.0F);
        }

        public static Entry location(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
            return new Entry("location", level.dimension().identifier().toString(), null, x, y, z, yaw, pitch);
        }
    }

    public static synchronized List<String> ids(MinecraftServer server) {
        return new ArrayList<>(load(server).keySet());
    }

    public static synchronized List<Entry> get(MinecraftServer server, String id) {
        List<Entry> entries = load(server).get(id);
        return entries == null ? List.of() : List.copyOf(entries);
    }

    public static synchronized boolean addEntity(MinecraftServer server, String id, Villager villager) throws IOException {
        Map<String, List<Entry>> data = load(server);
        List<Entry> entries = data.computeIfAbsent(id, ignored -> new ArrayList<>());
        if (entries.stream().anyMatch(entry -> entry.uuid() != null && entry.uuid().equals(villager.getUUID()))) return false;
        entries.add(Entry.entity(villager));
        save(server, data);
        return true;
    }

    public static synchronized boolean addLocation(MinecraftServer server, String id, Entry location) throws IOException {
        Map<String, List<Entry>> data = load(server);
        List<Entry> entries = data.computeIfAbsent(id, ignored -> new ArrayList<>());
        boolean duplicate = entries.stream().anyMatch(entry -> sameDimension(entry, location) && distanceSquared(entry, location) < 0.25);
        if (duplicate) return false;
        entries.add(location);
        save(server, data);
        return true;
    }

    public static synchronized boolean removeEntity(MinecraftServer server, String id, UUID uuid) throws IOException {
        Map<String, List<Entry>> data = load(server);
        List<Entry> entries = data.get(id);
        if (entries == null || !entries.removeIf(entry -> uuid.equals(entry.uuid()))) return false;
        save(server, data);
        return true;
    }

    public static synchronized boolean removeNearestLocation(MinecraftServer server, String id, Entry location) throws IOException {
        Map<String, List<Entry>> data = load(server);
        List<Entry> entries = data.get(id);
        if (entries == null) return false;
        Entry nearest = null;
        double nearestDistance = 2.25;
        for (Entry entry : entries) {
            if (!sameDimension(entry, location)) continue;
            double distance = distanceSquared(entry, location);
            if (distance < nearestDistance) {
                nearest = entry;
                nearestDistance = distance;
            }
        }
        if (nearest == null) return false;
        entries.remove(nearest);
        save(server, data);
        return true;
    }

    public static synchronized int replace(MinecraftServer server, String id, List<Entry> entries) throws IOException {
        Map<String, List<Entry>> data = load(server);
        data.put(id, new ArrayList<>(entries));
        save(server, data);
        return entries.size();
    }

    public static synchronized boolean delete(MinecraftServer server, String id) throws IOException {
        Map<String, List<Entry>> data = load(server);
        if (data.remove(id) == null) return false;
        save(server, data);
        return true;
    }

    private static boolean sameDimension(Entry first, Entry second) {
        return first.dimension().equals(second.dimension());
    }

    private static double distanceSquared(Entry first, Entry second) {
        double dx = first.x() - second.x();
        double dy = first.y() - second.y();
        double dz = first.z() - second.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static Map<String, List<Entry>> load(MinecraftServer server) {
        Path path = path(server);
        if (!Files.isRegularFile(path)) return new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Map<String, List<Entry>> data = GSON.fromJson(reader, DATA_TYPE);
            return data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
        } catch (Exception exception) {
            throw new IllegalStateException("cannot read " + FILE_NAME + ": " + exception.getMessage(), exception);
        }
    }

    private static void save(MinecraftServer server, Map<String, List<Entry>> data) throws IOException {
        Path path = path(server);
        Path temp = path.resolveSibling(FILE_NAME + ".tmp");
        Files.writeString(temp, GSON.toJson(data, DATA_TYPE), StandardCharsets.UTF_8);
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path path(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }
}
