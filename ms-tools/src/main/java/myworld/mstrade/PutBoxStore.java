package myworld.mstrade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Read-only bridge to the saved boxes owned by the independent put.sc app. */
public final class PutBoxStore {
    private static final String FILE_NAME = "ms_put_boxes.nbt";

    private PutBoxStore() {}

    public static List<String> ids(MinecraftServer server) {
        CompoundTag root = load(server);
        List<String> ids = new ArrayList<>(root.keySet());
        ids.sort(Comparator.comparingInt(PutBoxStore::numericId));
        return ids;
    }

    public static Entry get(MinecraftServer server, int id) {
        CompoundTag root = load(server);
        CompoundTag box = root.getCompound(Integer.toString(id)).orElse(null);
        if (box == null || !box.contains("x") || !box.contains("y") || !box.contains("z")
            || !box.contains("approach_x") || !box.contains("approach_y") || !box.contains("approach_z")) {
            return null;
        }
        return new Entry(
            id,
            box.getIntOr("x", 0), box.getIntOr("y", 0), box.getIntOr("z", 0),
            box.getDoubleOr("approach_x", 0.0), box.getDoubleOr("approach_y", 0.0),
            box.getDoubleOr("approach_z", 0.0),
            box.getFloatOr("yaw", 0.0F), box.getFloatOr("pitch", 0.0F),
            normalizeDimension(box.getStringOr("dimension", "overworld"))
        );
    }

    private static CompoundTag load(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("scripts").resolve("shared").resolve(FILE_NAME);
        if (!Files.isRegularFile(path)) return new CompoundTag();
        try {
            return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
        } catch (Exception exception) {
            throw new IllegalStateException("cannot read " + FILE_NAME + ": " + exception.getMessage(), exception);
        }
    }

    private static int numericId(String id) {
        try {
            return Integer.parseInt(id);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static String normalizeDimension(String dimension) {
        if (dimension.contains(":")) return dimension;
        return "minecraft:" + dimension;
    }

    public record Entry(int id, int x, int y, int z,
                        double approachX, double approachY, double approachZ,
                        float yaw, float pitch, String dimension) {}
}
