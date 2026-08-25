/*
 * This code partially adapted from Meteor Rejects
 * Original source: https://github.com/AntiCope/meteor-rejects/
 * Credit: Meteor Rejects contributors
 */
package com.theflex5710.oresim.modules;

import autismclient.api.AutismAddons;
import autismclient.api.module.BoolSetting;
import autismclient.api.module.EnumSetting;
import autismclient.api.module.IntSetting;
import autismclient.util.AutismClientMessaging;
import com.theflex5710.oresim.utils.Ore;
import com.theflex5710.oresim.utils.SeedStore;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OreSimModule extends autismclient.modules.Module {
    private static volatile OreSimModule instance;

    private final Map<Long, Map<Ore, Set<Vec3>>> chunkRenderers = new ConcurrentHashMap<>();
    private final Set<Long> pendingChunks = ConcurrentHashMap.newKeySet();
    private long worldSeed;
    private boolean hasSeed;
    private Map<ResourceKey<Biome>, List<Ore>> oreConfig;
    private String lastWorldName;
    private ResourceKey<Level> lastWorldKey;

    public enum AirCheck {
        ON_LOAD,
        RECHECK,
        OFF
    }

    private final IntSetting horizontalRadius = add(new IntSetting("chunk-range", "Chunk Range", 5, 1, 16, 1)
        .description("Range of chunks to render around the player."));

    private final EnumSetting<AirCheck> airCheck = add(
        new EnumSetting<>("air-check-mode", "Air Check", AirCheck.RECHECK, AirCheck.values())
            .description("Checks for air blocks when validating simulated ore positions."));

    public OreSimModule() {
        super(AutismAddons.id("ore-sim"), "OreSim", null, "Simulates vanilla ore generation using the world seed.");
        for (BoolSetting setting : Ore.oreSettings()) {
            add(setting.group("Ores"));
        }
        instance = this;
    }

    public static OreSimModule active() {
        return instance;
    }

    public int chunkRange() {
        Integer value = horizontalRadius.get();
        return value == null ? 5 : Math.max(1, Math.min(16, value));
    }

    @Override
    public void onEnable() {
        if (!SeedStore.hasSeed(MC)) {
            AutismClientMessaging.sendPrefixed("§cOreSim: no seed found. Use §f.seed-world <seed>§c to set one.");
            toggle();
            return;
        }
        updateWorldTracking();
        reload();
    }

    @Override
    public void onDisable() {
        synchronized (chunkRenderers) {
            chunkRenderers.clear();
        }
        pendingChunks.clear();
        oreConfig = null;
        hasSeed = false;
        worldSeed = 0L;
        lastWorldName = null;
        lastWorldKey = null;
    }

    @Override
    public void tick() {
        if (MC.player == null || MC.level == null || !hasSeed || oreConfig == null) return;

        detectWorldChange();

        if (!pendingChunks.isEmpty()) {
            ClientChunkCache chunks = MC.level.getChunkSource();
            for (Long key : List.copyOf(pendingChunks)) {
                int cx = ChunkPos.getX(key);
                int cz = ChunkPos.getZ(key);
                ChunkAccess chunk = chunks.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk != null) {
                    pendingChunks.remove(key);
                    calculateChunk(chunk);
                } else if (chunkRenderers.containsKey(key)) {
                    pendingChunks.remove(key);
                }
            }
        }
    }

    @Override
    public boolean onPacketReceive(Packet<?> packet) {
        if (packet instanceof ClientboundBlockUpdatePacket update) {
            handleBlockUpdate(update.getPos(), update.getBlockState());
        } else if (packet instanceof ClientboundSectionBlocksUpdatePacket sectionUpdate) {
            sectionUpdate.runUpdates((pos, state) -> handleBlockUpdate(pos.immutable(), state));
        } else if (packet instanceof ClientboundLevelChunkWithLightPacket chunkPacket) {
            long key = ChunkPos.pack(chunkPacket.getX(), chunkPacket.getZ());
            if (isEnabled()) pendingChunks.add(key);
            else synchronized (chunkRenderers) { chunkRenderers.remove(key); }
        } else if (packet instanceof ClientboundForgetLevelChunkPacket forget) {
            long key = forget.pos().pack();
            pendingChunks.remove(key);
            synchronized (chunkRenderers) { chunkRenderers.remove(key); }
        }
        return false;
    }

    private void reload() {
        Long stored = SeedStore.current(MC);
        if (stored == null) return;
        worldSeed = stored;
        hasSeed = true;
        oreConfig = Ore.getRegistry(Ore.Dimension.of(MC.level));
        synchronized (chunkRenderers) {
            chunkRenderers.clear();
        }
        pendingChunks.clear();
        if (MC.level != null) {
            loadVisibleChunks();
        }
    }

    private void detectWorldChange() {
        if (MC.level == null) return;
        String currentWorld = SeedStore.scopeKey(MC);
        ResourceKey<Level> currentKey = MC.level.dimension();
        if (!Objects.equals(currentWorld, lastWorldName) || !Objects.equals(currentKey, lastWorldKey)) {
            lastWorldName = currentWorld;
            lastWorldKey = currentKey;
            reload();
        }
    }

    private void updateWorldTracking() {
        if (MC.level == null) {
            lastWorldName = null;
            lastWorldKey = null;
        } else {
            lastWorldName = SeedStore.scopeKey(MC);
            lastWorldKey = MC.level.dimension();
        }
    }

    private void loadVisibleChunks() {
        if (MC.player == null || MC.level == null) return;
        int playerX = MC.player.chunkPosition().x();
        int playerZ = MC.player.chunkPosition().z();
        int radius = Math.max(1, horizontalRadius.get());
        ClientChunkCache chunks = MC.level.getChunkSource();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkAccess chunk = chunks.getChunk(playerX + dx, playerZ + dz, ChunkStatus.FULL, false);
                if (chunk != null) calculateChunk(chunk);
                else pendingChunks.add(ChunkPos.pack(playerX + dx, playerZ + dz));
            }
        }
    }

    private void calculateChunk(ChunkAccess chunk) {
        if (chunk == null || MC.level == null || !hasSeed || oreConfig == null) return;

        ChunkPos chunkPos = chunk.getPos();
        long chunkKey = chunkPos.pack();
        synchronized (chunkRenderers) {
            if (chunkRenderers.containsKey(chunkKey)) return;
        }

        Set<ResourceKey<Biome>> biomeKeys = new HashSet<>();
        ChunkPos.rangeClosed(chunkPos, 1).forEach(pos -> {
            ChunkAccess neighbour = MC.level.getChunk(pos.x(), pos.z(), ChunkStatus.BIOMES, false);
            if (neighbour == null) return;
            for (LevelChunkSection section : neighbour.getSections()) {
                section.getBiomes().getAll(entry -> biomeKeys.add(entry.unwrapKey().get()));
            }
        });

        Set<Ore> ores = biomeKeys.stream()
            .flatMap(biome -> getOresForBiome(biome).stream())
            .collect(Collectors.toSet());

        int chunkX = chunkPos.getMinBlockX();
        int chunkZ = chunkPos.getMinBlockZ();
        WorldgenRandom random = new WorldgenRandom(WorldgenRandom.Algorithm.XOROSHIRO.newInstance(0));
        long populationSeed = random.setDecorationSeed(worldSeed, chunkX, chunkZ);

        Map<Ore, Set<Vec3>> orePositions = new HashMap<>();
        for (Ore ore : ores) {
            HashSet<Vec3> positions = new HashSet<>();
            random.setFeatureSeed(populationSeed, ore.index, ore.step);
            int repeat = ore.count.sample(random);

            for (int i = 0; i < repeat; i++) {
                if (ore.rarity != 1.0F && random.nextFloat() >= 1.0F / ore.rarity) continue;

                int x = random.nextInt(16) + chunkX;
                int z = random.nextInt(16) + chunkZ;
                int y = ore.heightProvider.sample(random, ore.heightContext);
                BlockPos origin = new BlockPos(x, y, z);

                ResourceKey<Biome> biome = chunk.getNoiseBiome(
                    QuartPos.fromBlock(x),
                    QuartPos.fromBlock(y),
                    QuartPos.fromBlock(z)
                ).unwrapKey().get();
                if (!getOresForBiome(biome).contains(ore)) continue;

                if (ore.scattered) {
                    positions.addAll(generateHidden(MC.level, random, origin, ore.size));
                } else {
                    positions.addAll(generateNormal(MC.level, random, origin, ore.size, ore.discardOnAirChance));
                }
            }

            if (!positions.isEmpty()) {
                orePositions.put(ore, positions);
            }
        }

        if (!orePositions.isEmpty()) {
            synchronized (chunkRenderers) {
                chunkRenderers.put(chunkKey, orePositions);
            }
        }
    }

    private void handleBlockUpdate(BlockPos pos, net.minecraft.world.level.block.state.BlockState newState) {
        if (newState != null && Ore.isOreBlock(blockId(newState))) return;

        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        long chunkKey = ChunkPos.pack(x >> 4, z >> 4);
        Map<Ore, Set<Vec3>> chunk;
        synchronized (chunkRenderers) {
            chunk = chunkRenderers.get(chunkKey);
            if (chunk == null) return;
        }

        Vec3 target = new Vec3(x, y, z);
        boolean changed = false;
        for (Set<Vec3> ores : chunk.values()) {
            if (ores.remove(target)) changed = true;
        }
        if (changed) {
            chunk.values().removeIf(Set::isEmpty);
            synchronized (chunkRenderers) {
                if (chunk.isEmpty()) chunkRenderers.remove(chunkKey);
            }
        }
    }

    private static String blockId(net.minecraft.world.level.block.state.BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private List<Ore> getOresForBiome(ResourceKey<Biome> biomeKey) {
        if (oreConfig == null) return Collections.emptyList();
        List<Ore> ores = oreConfig.get(biomeKey);
        if (ores != null) return ores;
        return oreConfig.values().stream().findAny().orElse(Collections.emptyList());
    }

    // ------------------------------------------------------------------
    // Vanilla vein simulation (ported as-is; consumes the shared WorldgenRandom
    // in the exact same call order as vanilla so results match real gen).
    // ------------------------------------------------------------------

    private List<Vec3> generateNormal(Level world, WorldgenRandom random, BlockPos blockPos, int veinSize, float discardOnAir) {
        List<Vec3> positions = new ArrayList<>();
        float angle = random.nextFloat() * (float) Math.PI;
        float spread = (float) veinSize / 8.0F;
        int padding = Mth.ceil(((float) veinSize / 16.0F * 2.0F + 1.0F) / 2.0F);
        double startX = blockPos.getX() + Math.sin(angle) * spread;
        double endX = blockPos.getX() - Math.sin(angle) * spread;
        double startZ = blockPos.getZ() + Math.cos(angle) * spread;
        double endZ = blockPos.getZ() - Math.cos(angle) * spread;
        double startY = blockPos.getY() + random.nextInt(3) - 2;
        double endY = blockPos.getY() + random.nextInt(3) - 2;
        int minX = blockPos.getX() - Mth.ceil(spread) - padding;
        int minY = blockPos.getY() - 2 - padding;
        int minZ = blockPos.getZ() - Mth.ceil(spread) - padding;
        int sizeX = 2 * (Mth.ceil(spread) + padding);
        int sizeY = 2 * (2 + padding);

        for (int x = minX; x <= minX + sizeX; x++) {
            for (int z = minZ; z <= minZ + sizeX; z++) {
                if (minY <= world.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)) {
                    return generateVein(world, random, veinSize, startX, endX, startZ, endZ, startY, endY,
                        minX, minY, minZ, sizeX, sizeY, discardOnAir);
                }
            }
        }

        return positions;
    }

    private List<Vec3> generateVein(Level world, WorldgenRandom random, int veinSize,
                                    double startX, double endX, double startZ, double endZ,
                                    double startY, double endY,
                                    int minX, int minY, int minZ, int sizeX, int sizeY, float discardOnAir) {
        BitSet bitSet = new BitSet(sizeX * sizeY * sizeX);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        double[] buffer = new double[veinSize * 4];
        List<Vec3> positions = new ArrayList<>();

        for (int i = 0; i < veinSize; i++) {
            float progress = (float) i / (float) veinSize;
            double x = Mth.lerp(progress, startX, endX);
            double y = Mth.lerp(progress, startY, endY);
            double z = Mth.lerp(progress, startZ, endZ);
            double scale = random.nextDouble() * veinSize / 16.0D;
            double radius = (Mth.sin((float) Math.PI * progress) + 1.0F) * scale + 1.0D;
            buffer[i * 4] = x;
            buffer[i * 4 + 1] = y;
            buffer[i * 4 + 2] = z;
            buffer[i * 4 + 3] = radius / 2.0D;
        }

        for (int i = 0; i < veinSize - 1; i++) {
            if (buffer[i * 4 + 3] <= 0.0D) continue;
            for (int j = i + 1; j < veinSize; j++) {
                if (buffer[j * 4 + 3] <= 0.0D) continue;
                double dx = buffer[i * 4] - buffer[j * 4];
                double dy = buffer[i * 4 + 1] - buffer[j * 4 + 1];
                double dz = buffer[i * 4 + 2] - buffer[j * 4 + 2];
                double dr = buffer[i * 4 + 3] - buffer[j * 4 + 3];
                if (dr * dr > dx * dx + dy * dy + dz * dz) {
                    if (dr > 0.0D) buffer[j * 4 + 3] = -1.0D;
                    else buffer[i * 4 + 3] = -1.0D;
                }
            }
        }

        AirCheck checkMode = airCheck.get();

        for (int i = 0; i < veinSize; i++) {
            double radius = buffer[i * 4 + 3];
            if (radius < 0.0D) continue;
            double centerX = buffer[i * 4];
            double centerY = buffer[i * 4 + 1];
            double centerZ = buffer[i * 4 + 2];
            int minBlockX = Math.max(Mth.floor(centerX - radius), minX);
            int minBlockY = Math.max(Mth.floor(centerY - radius), minY);
            int minBlockZ = Math.max(Mth.floor(centerZ - radius), minZ);
            int maxBlockX = Math.max(Mth.floor(centerX + radius), minBlockX);
            int maxBlockY = Math.max(Mth.floor(centerY + radius), minBlockY);
            int maxBlockZ = Math.max(Mth.floor(centerZ + radius), minBlockZ);

            for (int x = minBlockX; x <= maxBlockX; x++) {
                double normX = ((double) x + 0.5D - centerX) / radius;
                if (normX * normX >= 1.0D) continue;
                for (int y = minBlockY; y <= maxBlockY; y++) {
                    double normY = ((double) y + 0.5D - centerY) / radius;
                    if (normX * normX + normY * normY >= 1.0D) continue;
                    for (int z = minBlockZ; z <= maxBlockZ; z++) {
                        double normZ = ((double) z + 0.5D - centerZ) / radius;
                        if (normX * normX + normY * normY + normZ * normZ >= 1.0D) continue;
                        int index = x - minX + (y - minY) * sizeX + (z - minZ) * sizeX * sizeY;
                        if (bitSet.get(index)) continue;
                        bitSet.set(index);
                        mutable.set(x, y, z);
                        if (y < world.getMinY() || y >= world.getMaxY()) continue;
                        if (checkMode != AirCheck.OFF && !world.getBlockState(mutable).canOcclude()) continue;
                        if (shouldPlace(world, mutable, discardOnAir, random)) {
                            positions.add(new Vec3(x, y, z));
                        }
                    }
                }
            }
        }

        return positions;
    }

    private boolean shouldPlace(Level world, BlockPos pos, float discardOnAir, WorldgenRandom random) {
        if (discardOnAir == 0 || (discardOnAir != 1.0F && random.nextFloat() >= discardOnAir)) return true;
        for (Direction direction : Direction.values()) {
            if (!world.getBlockState(pos.relative(direction)).canOcclude() && discardOnAir != 1.0F) return false;
        }
        return true;
    }

    private List<Vec3> generateHidden(Level world, WorldgenRandom random, BlockPos origin, int size) {
        List<Vec3> positions = new ArrayList<>();
        AirCheck checkMode = airCheck.get();
        int limit = random.nextInt(size + 1);
        for (int i = 0; i < limit; i++) {
            int range = Math.min(i, 7);
            int x = randomCoord(random, range) + origin.getX();
            int y = randomCoord(random, range) + origin.getY();
            int z = randomCoord(random, range) + origin.getZ();
            BlockPos pos = new BlockPos(x, y, z);
            if (checkMode != AirCheck.OFF && !world.getBlockState(pos).canOcclude()) continue;
            if (shouldPlace(world, pos, 1.0F, random)) {
                positions.add(new Vec3(x, y, z));
            }
        }
        return positions;
    }

    private int randomCoord(WorldgenRandom random, int size) {
        return Math.round((random.nextFloat() - random.nextFloat()) * size);
    }

    // ------------------------------------------------------------------
    // Rendering support - called from OreSimLevelRendererMixin.
    // ------------------------------------------------------------------

    public void forEachVisibleOre(int centerX, int centerZ, int range, OreVisitor visitor) {
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                long key = ChunkPos.pack(centerX + dx, centerZ + dz);
                Map<Ore, Set<Vec3>> chunk;
                synchronized (chunkRenderers) {
                    chunk = chunkRenderers.get(key);
                }
                if (chunk == null) continue;
                for (Map.Entry<Ore, Set<Vec3>> entry : chunk.entrySet()) {
                    Ore ore = entry.getKey();
                    if (ore.active == null || !ore.active.get()) continue;
                    for (Vec3 pos : entry.getValue()) {
                        visitor.accept(pos.x, pos.y, pos.z, pos.x + 1, pos.y + 1, pos.z + 1, ore.color);
                    }
                }
            }
        }
    }

    public interface OreVisitor {
        void accept(double x1, double y1, double z1, double x2, double y2, double z2, int color);
    }
}
