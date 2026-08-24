package com.theflex5710.oresim.utils;

import autismclient.api.module.BoolSetting;
import com.theflex5710.oresim.mixin.CountPlacementModifierAccessor;
import com.theflex5710.oresim.mixin.HeightRangePlacementModifierAccessor;
import com.theflex5710.oresim.mixin.RarityFilterPlacementModifierAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.data.worldgen.placement.OrePlacements;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.CountPlacement;
import net.minecraft.world.level.levelgen.placement.HeightRangePlacement;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.RarityFilter;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Adapted from Nora Tweaks (CC0) / originally from Meteor Rejects.
// Reads vanilla ore placement configs so the module can re-simulate generation from a seed.
public final class Ore {
    public enum Dimension {
        OVERWORLD, NETHER, END;

        public static Dimension of(Level level) {
            if (level == null) return OVERWORLD;
            ResourceKey<Level> key = level.dimension();
            if (key == Level.NETHER) return NETHER;
            if (key == Level.END) return END;
            return OVERWORLD;
        }
    }

    private static final List<BoolSetting> ORE_SETTINGS = List.of(
        new BoolSetting("coal", "Coal", false),
        new BoolSetting("iron", "Iron", true),
        new BoolSetting("gold", "Gold", true),
        new BoolSetting("redstone", "Redstone", true),
        new BoolSetting("diamond", "Diamond", true),
        new BoolSetting("lapis", "Lapis", true),
        new BoolSetting("copper", "Copper", false),
        new BoolSetting("emerald", "Emerald", true),
        new BoolSetting("quartz", "Quartz", true),
        new BoolSetting("ancient-debris", "Ancient Debris", true)
    );

    private static final Map<String, Boolean> DEFAULT_BY_ID = new HashMap<>();
    private static volatile Map<String, Integer> VANILLA_ORE_BLOCK_COLORS = null;

    public static List<BoolSetting> oreSettings() {
        return ORE_SETTINGS;
    }

    static {
        for (BoolSetting setting : ORE_SETTINGS) {
            DEFAULT_BY_ID.put(setting.id(), setting.defaultValueTyped());
        }
    }

    public int step;
    public int index;
    public BoolSetting active;
    public IntProvider count = ConstantInt.of(1);
    public HeightProvider heightProvider;
    public WorldGenerationContext heightContext;
    public float rarity = 1.0F;
    public float discardOnAirChance;
    public int size;
    public int color;
    public boolean scattered;

    // Blocks that vanilla ore features can actually place. Used to keep simulated positions
    // when the server reveals one of them (mirrors the Xray.ORES check of the original).
    public static boolean isOreBlock(String blockId) {
        return vanillaOreBlocks().containsKey(blockId);
    }

    public static Map<String, Integer> vanillaOreBlocks() {
        Map<String, Integer> map = VANILLA_ORE_BLOCK_COLORS;
        if (map != null) return map;
        synchronized (Ore.class) {
            if (VANILLA_ORE_BLOCK_COLORS == null) {
                Map<String, Integer> built = new HashMap<>();
                put(built, "coal_ore", 0xFF2F2C36);
                put(built, "deepslate_coal_ore", 0xFF2F2C36);
                put(built, "iron_ore", 0xFFECAD77);
                put(built, "deepslate_iron_ore", 0xFFECAD77);
                put(built, "gold_ore", 0xFFF7E51E);
                put(built, "deepslate_gold_ore", 0xFFF7E51E);
                put(built, "redstone_ore", 0xFFF50717);
                put(built, "deepslate_redstone_ore", 0xFFF50717);
                put(built, "lapis_ore", 0xFF081ABD);
                put(built, "deepslate_lapis_ore", 0xFF081ABD);
                put(built, "diamond_ore", 0xFF21F4FF);
                put(built, "deepslate_diamond_ore", 0xFF21F4FF);
                put(built, "emerald_ore", 0xFF1BD12D);
                put(built, "deepslate_emerald_ore", 0xFF1BD12D);
                put(built, "copper_ore", 0xFFEF9700);
                put(built, "deepslate_copper_ore", 0xFFEF9700);
                put(built, "nether_gold_ore", 0xFFF7E51E);
                put(built, "nether_quartz_ore", 0xFFCDCDCD);
                put(built, "ancient_debris", 0xFFD11BF5);
                VANILLA_ORE_BLOCK_COLORS = built;
            }
            map = VANILLA_ORE_BLOCK_COLORS;
        }
        return map;
    }

    private static void put(Map<String, Integer> map, String name, int color) {
        map.put(Identifier.withDefaultNamespace(name).toString(), color);
    }

    public Ore(PlacedFeature feature, int step, int index, BoolSetting active, int color,
               WorldGenerationContext heightContext) {
        this.step = step;
        this.index = index;
        this.active = active;
        this.color = color;
        this.heightContext = heightContext;

        for (PlacementModifier modifier : feature.placement()) {
            if (modifier instanceof CountPlacement countPlacement) {
                this.count = ((CountPlacementModifierAccessor) countPlacement).getCount();
            } else if (modifier instanceof HeightRangePlacement heightRange) {
                this.heightProvider = ((HeightRangePlacementModifierAccessor) heightRange).getHeight();
            } else if (modifier instanceof RarityFilter rarityFilter) {
                this.rarity = ((RarityFilterPlacementModifierAccessor) rarityFilter).getChance();
            }
        }

        FeatureConfiguration featureConfig = feature.feature().value().config();
        if (featureConfig instanceof OreConfiguration oreFeatureConfig) {
            this.discardOnAirChance = oreFeatureConfig.discardChanceOnAirExposure;
            this.size = oreFeatureConfig.size;
        } else {
            throw new IllegalStateException("Config for " + feature + " is not an OreConfiguration");
        }

        if (feature.feature().value().feature()
            instanceof net.minecraft.world.level.levelgen.feature.ScatteredOreFeature) {
            this.scattered = true;
        }
    }

    public static Map<ResourceKey<Biome>, List<Ore>> getRegistry(Dimension dimension) {
        HolderLookup.Provider lookup = VanillaRegistries.createLookup();
        HolderLookup.RegistryLookup<PlacedFeature> features = lookup.lookupOrThrow(Registries.PLACED_FEATURE);
        var dimensionMap = lookup.lookupOrThrow(Registries.WORLD_PRESET)
            .getOrThrow(WorldPresets.NORMAL).value().createWorldDimensions().dimensions();

        var dimensionOptions = switch (dimension) {
            case OVERWORLD -> dimensionMap.get(LevelStem.OVERWORLD);
            case NETHER -> dimensionMap.get(LevelStem.NETHER);
            case END -> dimensionMap.get(LevelStem.END);
        };

        var biomes = new ArrayList<>(dimensionOptions.generator().getBiomeSource().possibleBiomes());

        WorldGenerationContext heightContext;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.level != null) {
            int bottom = mc.level.getMinY();
            int logical = mc.level.dimensionType().logicalHeight();
            heightContext = new WorldGenerationContext(dimensionOptions.generator(),
                LevelHeightAccessor.create(bottom, logical));
        } else {
            heightContext = new WorldGenerationContext(dimensionOptions.generator(),
                LevelHeightAccessor.create(-64, 384));
        }

        List<FeatureSorter.StepFeatureData> indexer = FeatureSorter.buildFeaturesPerStep(
            biomes,
            biomeEntry -> biomeEntry.value().getGenerationSettings().features(),
            true
        );

        BoolSetting coal = setting("coal");
        BoolSetting iron = setting("iron");
        BoolSetting gold = setting("gold");
        BoolSetting redstone = setting("redstone");
        BoolSetting diamond = setting("diamond");
        BoolSetting lapis = setting("lapis");
        BoolSetting copper = setting("copper");
        BoolSetting emerald = setting("emerald");
        BoolSetting quartz = setting("quartz");
        BoolSetting debris = setting("ancient-debris");

        Map<PlacedFeature, Ore> featureToOre = new HashMap<>();
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_COAL_LOWER, 6, coal, 0xFF2F2C36, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_COAL_UPPER, 6, coal, 0xFF2F2C36, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_IRON_MIDDLE, 6, iron, 0xFFECAD77, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_IRON_SMALL, 6, iron, 0xFFECAD77, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_IRON_UPPER, 6, iron, 0xFFECAD77, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_GOLD, 6, gold, 0xFFF7E51E, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_GOLD_LOWER, 6, gold, 0xFFF7E51E, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_GOLD_EXTRA, 6, gold, 0xFFF7E51E, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_GOLD_NETHER, 7, gold, 0xFFF7E51E, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_GOLD_DELTAS, 7, gold, 0xFFF7E51E, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_REDSTONE, 6, redstone, 0xFFF50717, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_REDSTONE_LOWER, 6, redstone, 0xFFF50717, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_DIAMOND, 6, diamond, 0xFF21F4FF, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_DIAMOND_BURIED, 6, diamond, 0xFF21F4FF, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_DIAMOND_LARGE, 6, diamond, 0xFF21F4FF, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_DIAMOND_MEDIUM, 6, diamond, 0xFF21F4FF, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_LAPIS, 6, lapis, 0xFF081ABD, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_LAPIS_BURIED, 6, lapis, 0xFF081ABD, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_COPPER, 6, copper, 0xFFEF9700, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_COPPER_LARGE, 6, copper, 0xFFEF9700, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_EMERALD, 6, emerald, 0xFF1BD12D, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_QUARTZ_NETHER, 7, quartz, 0xFFCDCDCD, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_QUARTZ_DELTAS, 7, quartz, 0xFFCDCDCD, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_ANCIENT_DEBRIS_SMALL, 7, debris, 0xFFD11BF5, heightContext);
        registerOre(featureToOre, indexer, features, OrePlacements.ORE_ANCIENT_DEBRIS_LARGE, 7, debris, 0xFFD11BF5, heightContext);

        Map<ResourceKey<Biome>, List<Ore>> biomeOreMap = new HashMap<>();
        for (Holder<Biome> biome : biomes) {
            biomeOreMap.put(biome.unwrapKey().get(), new ArrayList<>());
            biome.value().getGenerationSettings().features().stream()
                .flatMap(HolderSet::stream)
                .map(Holder::value)
                .filter(featureToOre::containsKey)
                .forEach(feature -> biomeOreMap.get(biome.unwrapKey().get()).add(featureToOre.get(feature)));
        }

        return biomeOreMap;
    }

    private static BoolSetting setting(String id) {
        for (BoolSetting value : ORE_SETTINGS) {
            if (value.id().equals(id)) return value;
        }
        throw new IllegalStateException("Missing ore setting: " + id);
    }

    private static void registerOre(
        Map<PlacedFeature, Ore> map,
        List<FeatureSorter.StepFeatureData> indexer,
        HolderLookup.RegistryLookup<PlacedFeature> oreRegistry,
        ResourceKey<PlacedFeature> oreKey,
        int genStep,
        BoolSetting active,
        int color,
        WorldGenerationContext heightContext
    ) {
        PlacedFeature placedFeature = oreRegistry.getOrThrow(oreKey).value();
        int idx = indexer.get(genStep).indexMapping().applyAsInt(placedFeature);
        map.put(placedFeature, new Ore(placedFeature, genStep, idx, active, color, heightContext));
    }
}
