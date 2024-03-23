import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.block.Blocks;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

@Mod("rainmod")
public class RainMod {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Random random = new Random();
    private int tickCount = 0;

    private final Map<ChunkPos, Set<BlockPos>> waterBlocksByChunk = new HashMap<>();

    // コンフィグ変数を定義
    private int waterGenInterval;


    private int waterGenChance;
    private int evaporationChance;
    private long evaporationTime;
    private int waterGenAmount;
    private boolean evaporateNearLava;

    public RainMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::doClientStuff);
        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, RainModConfig.SPEC, "rainmod-server.toml");
    }

    private void setup(final FMLCommonSetupEvent event) {
        loadConfigValues();
        LOGGER.info("Rain mod setup complete!");
    }

    private void doClientStuff(final FMLClientSetupEvent event) {
        LOGGER.info("Rain mod client setup complete!");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("rainmod")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("interval")
                        .then(Commands.argument("interval", IntegerArgumentType.integer(1, 1000))
                                .executes(context -> setWaterGenInterval(context.getSource(), IntegerArgumentType.getInteger(context, "interval")))))
                .then(Commands.literal("amount")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 10))
                                .executes(context -> setWaterGenAmount(context.getSource(), IntegerArgumentType.getInteger(context, "amount")))))
                .then(Commands.literal("chance")
                        .then(Commands.argument("chance", IntegerArgumentType.integer(0, 100))
                                .executes(context -> setWaterGenChance(context.getSource(), IntegerArgumentType.getInteger(context, "chance")))))
                .then(Commands.literal("evapchance")
                        .then(Commands.argument("chance", IntegerArgumentType.integer(0, 100))
                                .executes(context -> setEvaporationChance(context.getSource(), IntegerArgumentType.getInteger(context, "chance")))))
                .then(Commands.literal("evaptime")
                        .then(Commands.argument("time", IntegerArgumentType.integer(1, 10000))
                                .executes(context -> setEvaporationTime(context.getSource(), IntegerArgumentType.getInteger(context, "time")))))
                .then(Commands.literal("evaporatenearlava")
                        .then(Commands.literal("on")
                                .executes(context -> setEvaporateNearLava(context.getSource(), true)))
                        .then(Commands.literal("off")
                                .executes(context -> setEvaporateNearLava(context.getSource(), false))))
                .then(Commands.literal("amount")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 10))
                                .executes(context -> setWaterGenAmount(context.getSource(), IntegerArgumentType.getInteger(context, "amount")))))
                .then(Commands.literal("reloadconfig")
                        .executes(context -> reloadConfig(context.getSource())))
        );
    }

    private int setWaterGenInterval(CommandSource source, int interval) {
        RainModConfig.WATER_GEN_INTERVAL.set(interval);
        RainModConfig.SPEC.save();
        waterGenInterval = interval;
        source.sendSuccess(new StringTextComponent("Water generation interval set to " + interval), true);
        return 1;
    }

    private int setWaterGenChance(CommandSource source, int chance) {
        RainModConfig.WATER_GEN_CHANCE.set(chance);
        RainModConfig.SPEC.save();
        waterGenChance = chance;
        source.sendSuccess(new StringTextComponent("Water generation chance set to " + chance + "%"), true);
        return 1;
    }

    private int setEvaporationChance(CommandSource source, int chance) {
        RainModConfig.EVAPORATION_CHANCE.set(chance);
        RainModConfig.SPEC.save();
        evaporationChance = chance;
        source.sendSuccess(new StringTextComponent("Evaporation chance set to " + chance + "%"), true);
        return 1;
    }

    private int setEvaporationTime(CommandSource source, int time) {
        RainModConfig.EVAPORATION_TIME.set((long) time);
        RainModConfig.SPEC.save();
        evaporationTime = time;
        source.sendSuccess(new StringTextComponent("Evaporation time set to " + time + " ticks"), true);
        return 1;
    }

    private int setEvaporateNearLava(CommandSource source, boolean enabled) {
        RainModConfig.EVAPORATE_NEAR_LAVA.set(enabled);
        RainModConfig.SPEC.save();
        evaporateNearLava = enabled;

        String status = enabled ? "enabled" : "disabled";
        source.sendSuccess(new StringTextComponent("Water evaporation near lava " + status), true);
        return 1;
    }
    private int setWaterGenAmount(CommandSource source, int amount) {
        RainModConfig.WATER_GEN_AMOUNT.set(amount);
        RainModConfig.SPEC.save();
        waterGenAmount = amount;
        source.sendSuccess(new StringTextComponent("Water generation amount set to " + amount), true);
        return 1;
    }

    private int reloadConfig(CommandSource source) {
        try {
            RainModConfig.SPEC.isLoaded();
            loadConfigValues();
            source.sendSuccess(new StringTextComponent("Configuration reloaded successfully!"), true);
            return 1;
        } catch (Exception e) {
            source.sendFailure(new StringTextComponent("Failed to reload configuration: " + e.getMessage()));
            return 0;
        }
    }

    private void loadConfigValues() {
        waterGenInterval = RainModConfig.WATER_GEN_INTERVAL.get();
        waterGenAmount = RainModConfig.WATER_GEN_AMOUNT.get();
        evaporationChance = RainModConfig.EVAPORATION_CHANCE.get();
        evaporationTime = RainModConfig.EVAPORATION_TIME.get();
        evaporateNearLava = RainModConfig.EVAPORATE_NEAR_LAVA.get();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!event.player.level.isClientSide && event.phase == TickEvent.Phase.END) {
            World world = event.player.level;
            Vector3d playerPos = event.player.position();
            Biome biome = world.getBiome(new BlockPos(playerPos));

            if (biome.getPrecipitation() == Biome.RainType.RAIN && world.isRaining()) {
                if (tickCount++ >= waterGenInterval) {
                    tickCount = 0;
                    generateWater(world, playerPos);
                }
            }

            if (!world.isRaining() && !world.isThundering()) {
                evaporateWater(world, playerPos);
            }

            if (RainModConfig.EVAPORATE_NEAR_LAVA.get()) {
                checkWaterEvaporationNearLava(world, new BlockPos(playerPos));
            }
        }
    }

    private void generateWater(World world, Vector3d playerPos) {
        int range = 16 * 5;
        int attempts = 20;

        for (int i = 0; i < attempts; i++) {
            int dx = random.nextInt(range * 2) - range;
            int dz = random.nextInt(range * 2) - range;

            int startY = world.getSeaLevel();

            for (int y = startY; y < world.getMaxBuildHeight(); y++) {
                BlockPos pos = new BlockPos(playerPos.x + dx, y, playerPos.z + dz);

                if (world.isEmptyBlock(pos) && (world.getBlockState(pos.below()).isCollisionShapeFullBlock(world, pos.below()) || world.canSeeSky(pos))) {
                    if (random.nextInt(100) < waterGenChance) {
                        int waterHeight = 1;
                        for (int j = 1; j < waterGenAmount; j++) {
                            BlockPos waterPos = pos.above(j);
                            if (world.isEmptyBlock(waterPos)) {
                                waterHeight++;
                            } else {
                                break;
                            }
                        }
                        for (int j = 0; j < waterHeight; j++) {
                            BlockPos waterPos = pos.above(j);
                            world.setBlock(waterPos, Fluids.WATER.defaultFluidState().createLegacyBlock(), 11);
                            addWaterBlock(waterPos);
                        }
                    }
                    break;
                }
            }
        }
    }


    private boolean canGenerateWaterAt(World world, BlockPos pos) {
        if (!world.isEmptyBlock(pos) || !world.canSeeSky(pos) || world.getBlockState(pos.below()).is(Blocks.WATER)) {
            return false;
        }
        Biome biome = world.getBiome(pos);
        int waterGenChance = getWaterGenChanceForBiome(biome, pos);
        return random.nextInt(100) < waterGenChance;
    }


    private int getWaterGenChanceForBiome(Biome biome, BlockPos pos) {
        if (biome.getPrecipitation() == Biome.RainType.RAIN) {
            if (biome.getTemperature(pos) < 0.15F) {
                return waterGenChance / 2;
            } else {
                return waterGenChance;
            }
        }
        return 0;
    }

    private void evaporateWater(World world, Vector3d playerPos) {
        BlockPos playerBlockPos = new BlockPos(playerPos);
        Chunk chunk = world.getChunkAt(playerBlockPos);
        ChunkPos chunkPos = chunk.getPos();

        Set<BlockPos> chunkWaterBlocks = waterBlocksByChunk.get(chunkPos);
        if (chunkWaterBlocks != null && !chunkWaterBlocks.isEmpty()) {
            List<BlockPos> toEvaporate = new ArrayList<>();

            for (BlockPos pos : chunkWaterBlocks) {
                if (world.getBlockState(pos).getBlock() == Blocks.WATER && isAirAboveWater(world, pos)) {
                    if (random.nextInt(100) < evaporationChance) {
                        toEvaporate.add(pos);
                    }
                }
            }

            for (BlockPos pos : toEvaporate) {
                evaporateWaterBlock(world, pos);
            }
        }
    }


    private int getEvaporationChanceForEnvironment(World world, Vector3d playerPos) {
        BlockPos pos = new BlockPos(playerPos);
        Biome biome = world.getBiome(pos);
        float temperature = biome.getTemperature(pos);
        float humidity = biome.getDownfall();

        int evaporationChance = RainModConfig.EVAPORATION_CHANCE.get();

        if (temperature > 1.0F) {
            evaporationChance += 10;
        } else if (temperature > 0.5F) {
            evaporationChance += 5;
        }

        if (humidity < 0.2F) {
            evaporationChance += 10;
        } else if (humidity < 0.5F) {
            evaporationChance += 5;
        }

        return MathHelper.clamp(evaporationChance, 0, 100);
    }

    private void checkWaterEvaporationNearLava(World world, BlockPos playerPos) {
        int range = 5;
        double evaporationProbability = 0.1;

        List<BlockPos> lavaPosListCopy = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-range, -range, -range), playerPos.offset(range, range, range))) {
            if (world.getBlockState(pos).getBlock() == Blocks.LAVA) {
                lavaPosListCopy.add(pos);
            }
        }

        for (BlockPos lavaPos : lavaPosListCopy) {
            for (BlockPos waterPos : getWaterBlocksAroundLava(world, lavaPos)) {
                double distance = waterPos.distSqr(lavaPos);
                double probability = evaporationProbability * (1.0 - distance / (range * range));

                if (random.nextDouble() < probability) {
                    evaporateWaterBlock(world, waterPos);
                }
            }
        }
    }

    private List<BlockPos> getWaterBlocksAroundLava(World world, BlockPos lavaPos) {
        List<BlockPos> waterBlocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(lavaPos.offset(-1, -1, -1), lavaPos.offset(1, 1, 1))) {
            if (world.getBlockState(pos).getBlock() == Blocks.WATER) {
                waterBlocks.add(pos);
            }
        }
        return waterBlocks;
    }

    private void evaporateWaterBlock(World world, BlockPos pos) {
        removeWaterBlock(pos);
        world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    private void addWaterBlock(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        waterBlocksByChunk.computeIfAbsent(chunkPos, k -> new HashSet<>()).add(pos);
    }

    private void removeWaterBlock(BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        Set<BlockPos> waterBlocks = waterBlocksByChunk.get(chunkPos);
        if (waterBlocks != null) {
            waterBlocks.remove(pos);
            if (waterBlocks.isEmpty()) {
                waterBlocksByChunk.remove(chunkPos);
            }
        }
    }

    private boolean isAirAboveWater(World world, BlockPos waterPos) {
        BlockPos above = waterPos.above();
        while (above.getY() < world.getMaxBuildHeight()) {
            if (!world.isEmptyBlock(above)) {
                return false;
            }
            above = above.above();
        }
        return true;
    }

    public static class RainModConfig {
        public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
        public static final ForgeConfigSpec SPEC;

        public static final ForgeConfigSpec.ConfigValue<Integer> WATER_GEN_INTERVAL;
        public static final ForgeConfigSpec.ConfigValue<Integer> WATER_GEN_CHANCE;
        public static final ForgeConfigSpec.ConfigValue<Integer> EVAPORATION_CHANCE;
        public static final ForgeConfigSpec.ConfigValue<Long> EVAPORATION_TIME;
        public static final ForgeConfigSpec.ConfigValue<Boolean> EVAPORATE_NEAR_LAVA;

        public static final ForgeConfigSpec.IntValue WATER_GEN_AMOUNT;

        static {
            BUILDER.push("Rain Mod Config");

            WATER_GEN_INTERVAL = BUILDER.comment("Water generation interval (in ticks)")
                    .defineInRange("waterGenInterval", 100, 1, 1000);
            WATER_GEN_CHANCE = BUILDER.comment("Water generation chance (in percentage)")
                    .defineInRange("waterGenChance", 30, 0, 100);
            EVAPORATION_CHANCE = BUILDER.comment("Evaporation chance (in percentage)")
                    .defineInRange("evaporationChance", 20, 0, 100);
            EVAPORATION_TIME = BUILDER.comment("Evaporation time (in ticks)")
                    .defineInRange("evaporationTime", 600L, 1L, 10000L);
            EVAPORATE_NEAR_LAVA = BUILDER.comment("Whether water should evaporate near lava")
                    .define("evaporateNearLava", true);
            WATER_GEN_AMOUNT = BUILDER.comment("Water generation amount")
                    .defineInRange("waterGenAmount", 1, 1, 10);

            BUILDER.pop();
            SPEC = BUILDER.build();
        }
    }
}