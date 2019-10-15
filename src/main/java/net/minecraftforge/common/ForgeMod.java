/*
 * Minecraft Forge
 * Copyright (c) 2016-2019.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.common;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLModIdMappingEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.progress.StartupMessageManager;
import net.minecraftforge.server.command.ConfigCommand;
import net.minecraftforge.server.command.ForgeCommand;
import net.minecraftforge.versions.forge.ForgeVersion;
import net.minecraftforge.versions.mcp.MCPVersion;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.data.DataGenerator;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.IRecipeSerializer;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.client.model.generators.*;
import net.minecraftforge.client.model.generators.ModelFile.UncheckedModelFile;
import net.minecraftforge.common.crafting.*;
import net.minecraftforge.common.crafting.conditions.*;
import net.minecraftforge.common.data.ForgeBlockTagsProvider;
import net.minecraftforge.common.data.ForgeItemTagsProvider;
import net.minecraftforge.common.data.ForgeRecipeProvider;
import net.minecraftforge.common.model.animation.CapabilityAnimation;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fml.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLModIdMappingEvent;
import net.minecraftforge.fml.event.lifecycle.GatherDataEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.server.command.ConfigCommand;
import net.minecraftforge.server.command.ForgeCommand;
import net.minecraftforge.versions.forge.ForgeVersion;
import net.minecraftforge.versions.mcp.MCPVersion;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

@Mod("forge")
public class ForgeMod implements WorldPersistenceHooks.WorldPersistenceHook
{
    public static final String VERSION_CHECK_CAT = "version_checking";
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Marker FORGEMOD = MarkerManager.getMarker("FORGEMOD");
    public static int[] blendRanges = { 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34 };
    public static boolean disableVersionCheck = false;
    public static boolean forgeLightPipelineEnabled = true;
    public static boolean zoomInMissingModelTextInGui = false;
    public static boolean disableStairSlabCulling = false; // Also known as the "DontCullStairsBecauseIUseACrappyTexturePackThatBreaksBasicBlockShapesSoICantTrustBasicBlockCulling" flag
    public static boolean alwaysSetupTerrainOffThread = false; // In WorldRenderer.setupTerrain, always force the chunk render updates to be queued to the thread
    public static boolean logCascadingWorldGeneration = true; // see Chunk#logCascadingWorldGeneration()
    public static boolean fixVanillaCascading = false; // There are various places in vanilla that cause cascading worldgen. Enabling this WILL change where blocks are placed to prevent this.
                                                       // DO NOT contact Forge about worldgen not 'matching' vanilla if this flag is set.

    private static ForgeMod INSTANCE;
    public static ForgeMod getInstance()
    {
        return INSTANCE;
    }

    public ForgeMod()
    {
        LOGGER.info(FORGEMOD,"Forge mod loading, version {}, for MC {} with MCP {}", ForgeVersion.getVersion(), MCPVersion.getMCVersion(), MCPVersion.getMCPVersion());
        INSTANCE = this;
        MinecraftForge.initialize();
        WorldPersistenceHooks.addHook(this);
        WorldPersistenceHooks.addHook(new FMLWorldPersistenceHook());
        final IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::preInit);
        modEventBus.addListener(this::gatherData);
        modEventBus.register(this);
        MinecraftForge.EVENT_BUS.addListener(this::serverStarting);
        MinecraftForge.EVENT_BUS.addListener(this::serverStopping);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ForgeConfig.clientSpec);
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, ForgeConfig.serverSpec);
        modEventBus.register(ForgeConfig.class);
        // Forge does not display problems when the remote is not matching.
        ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, ()-> Pair.of(()->"ANY", (remote, isServer)-> true));
        StartupMessageManager.addModMessage("Forge version "+ForgeVersion.getVersion());
    }

    public void preInit(FMLCommonSetupEvent evt)
    {
        CapabilityItemHandler.register();
        CapabilityFluidHandler.register();
        CapabilityAnimation.register();
        CapabilityEnergy.register();
        MinecraftForge.EVENT_BUS.addListener(VillagerTradingManager::loadTrades);
        MinecraftForge.EVENT_BUS.register(MinecraftForge.INTERNAL_HANDLER);
        MinecraftForge.EVENT_BUS.register(this);

        if (!ForgeMod.disableVersionCheck)
        {
            VersionChecker.startVersionCheck();
        }

        /*
         * We can't actually add any of these, because vanilla clients will choke on unknown argument types
         * So our custom arguments will not validate client-side, but they do still work
        ArgumentTypes.register("forge:enum", EnumArgument.class, new EnumArgument.Serializer());
        ArgumentTypes.register("forge:modid", ModIdArgument.class, new ArgumentSerializer<>(ModIdArgument::modIdArgument));
        ArgumentTypes.register("forge:structure_type", StructureArgument.class, new ArgumentSerializer<>(StructureArgument::structure));
        */
    }

    public void serverStarting(FMLServerStartingEvent evt)
    {
        new ForgeCommand(evt.getCommandDispatcher());
        ConfigCommand.register(evt.getCommandDispatcher());
    }

    public void serverStopping(FMLServerStoppingEvent evt)
    {
        WorldWorkerManager.clear();
    }

    @Override
    public CompoundNBT getDataForWriting(SaveHandler handler, WorldInfo info)
    {
        CompoundNBT forgeData = new CompoundNBT();
        CompoundNBT dims = new CompoundNBT();
        DimensionManager.writeRegistry(dims);
        if (!dims.isEmpty())
            forgeData.put("dims", dims);
        return forgeData;
    }

    @Override
    public void readData(SaveHandler handler, WorldInfo info, CompoundNBT tag)
    {
        if (tag.contains("dims", 10))
            DimensionManager.readRegistry(tag.getCompound("dims"));
    }

    public void mappingChanged(FMLModIdMappingEvent evt)
    {
    }

    @Override
    public String getModId()
    {
        return ForgeVersion.MOD_ID;
    }

    public void gatherData(GatherDataEvent event)
    {
        DataGenerator gen = event.getGenerator();

        if (event.includeClient())
        {
            gen.addProvider(new ItemModels(gen, event.getExistingFileHelper()));
            gen.addProvider(new BlockStates(gen, event.getExistingFileHelper()));
        }
        if (event.includeServer())
        {
            gen.addProvider(new ForgeBlockTagsProvider(gen));
            gen.addProvider(new ForgeItemTagsProvider(gen));
            gen.addProvider(new ForgeRecipeProvider(gen));
        }
    }
    
    public static class ItemModels extends ModelProvider<ItemModelBuilder>
    {
        public ItemModels(DataGenerator generator, ExistingFileHelper existingFileHelper)
        {
            super(generator, "forge", ITEM_FOLDER, ItemModelBuilder::new, existingFileHelper);
        }
        
        @Override
        protected void registerModels()
        {
            getBuilder("test_generated_model")
                    .parent(new UncheckedModelFile("item/generated"))
                    .texture("layer0", new ResourceLocation("block/stone"));

            getBuilder("test_block_model")
                    .parent(getExistingFile("block/block"))
                    .texture("all", new ResourceLocation("block/dirt"))
                    .texture("top", new ResourceLocation("block/stone"))
                    .element()
                        .cube("#all")
                        .face(Direction.UP)
                            .texture("#top")
                            .end()
                        .end();
        }

        @Override
        public String getName()
        {
            return "Forge Test Item Models";
        }
    }

   public static class BlockStates extends BlockstateProvider {

       public BlockStates(DataGenerator gen, ExistingFileHelper exFileHelper) {
           super(gen, "forge", exFileHelper);
       }

       @Override
       protected void registerStatesAndModels() {
           ModelFile acaciaFenceGate = getBuilder("acacia_fence_gate")
                   .parent(getExistingFile("block/template_fence_gate"))
                   .texture("texture", new ResourceLocation("block/acacia_planks"));
           ModelFile acaciaFenceGateOpen = getBuilder("acacia_fence_gate_open")
                   .parent(getExistingFile("block/template_fence_gate_open"))
                   .texture("texture", new ResourceLocation("block/acacia_planks"));
           ModelFile acaciaFenceGateWall = getBuilder("acacia_fence_gate_wall")
                   .parent(getExistingFile("block/template_fence_gate_wall"))
                   .texture("texture", new ResourceLocation("block/acacia_planks"));
           ModelFile acaciaFenceGateWallOpen = getBuilder("acacia_fence_gate_wall_open")
                   .parent(getExistingFile("block/template_fence_gate_wall_open"))
                   .texture("texture", new ResourceLocation("block/acacia_planks"));
           ModelFile invisbleModel = new UncheckedModelFile(new ResourceLocation("builtin/generated"));
           VariantBlockstate builder = getVariantBuilder(Blocks.ACACIA_FENCE_GATE);
           for (Direction dir : FenceGateBlock.HORIZONTAL_FACING.getAllowedValues()) {
               int angle = (int) dir.getHorizontalAngle();
               builder
                       .partialState()
                            .with(FenceGateBlock.HORIZONTAL_FACING, dir)
                            .with(FenceGateBlock.IN_WALL, false)
                            .with(FenceGateBlock.OPEN, false)
                            .modelForState()
                                .modelFile(invisbleModel)
                                .weight(1)
                            .nextModel()
                                .modelFile(acaciaFenceGate)
                                .rotationY(angle)
                                .uvLock(true)
                                .weight(100)
                            .addModel()
                       .partialState()
                            .with(FenceGateBlock.HORIZONTAL_FACING, dir)
                            .with(FenceGateBlock.IN_WALL, false)
                            .with(FenceGateBlock.OPEN, true)
                            .modelForState()
                                .modelFile(acaciaFenceGateOpen)
                                .rotationY(angle)
                                .uvLock(true)
                            .addModel()
                       .partialState()
                            .with(FenceGateBlock.HORIZONTAL_FACING, dir)
                            .with(FenceGateBlock.IN_WALL, true)
                            .with(FenceGateBlock.OPEN, false)
                            .modelForState()
                                .modelFile(acaciaFenceGateWall)
                                .rotationY(angle)
                                .uvLock(true)
                            .addModel()
                       .partialState()
                            .with(FenceGateBlock.HORIZONTAL_FACING, dir)
                            .with(FenceGateBlock.IN_WALL, true)
                            .with(FenceGateBlock.OPEN, true)
                            .modelForState()
                                .modelFile(acaciaFenceGateWallOpen)
                                .rotationY(angle)
                                .uvLock(true)
                            .addModel();
           }

           ModelFile acaciaFencePost = getBuilder("acacia_fence_post")
                   .parent(getExistingFile("block/fence_post"))
                   .texture("texture", new ResourceLocation("block/acacia_planks"));
           
           ModelFile acaciaFenceSide = getBuilder("acacia_fence_side")
                   .parent(getExistingFile("block/fence_side"))
                   .texture("texture", new ResourceLocation("block/acacia_planks"));
           
           getMultipartBuilder(Blocks.ACACIA_FENCE)
                   .part().modelFile(acaciaFencePost).addModel().build()
                   .part().modelFile(acaciaFenceSide).uvLock(true).addModel()
                           .condition(FenceBlock.NORTH, true).build()
                   .part().modelFile(acaciaFenceSide).rotationY(90).uvLock(true).addModel()
                           .condition(FenceBlock.EAST, true).build()
                   .part().modelFile(acaciaFenceSide).rotationY(180).uvLock(true).addModel()
                           .condition(FenceBlock.SOUTH, true).build()
                   .part().modelFile(acaciaFenceSide).rotationY(270).uvLock(true).addModel()
                           .condition(FenceBlock.WEST, true).build();
       }
   }

    @SubscribeEvent //ModBus, can't use addListener due to nested genetics.
    public void registerRecipeSerialziers(RegistryEvent.Register<IRecipeSerializer<?>> event)
    {
        CraftingHelper.register(AndCondition.Serializer.INSTANCE);
        CraftingHelper.register(FalseCondition.Serializer.INSTANCE);
        CraftingHelper.register(ItemExistsCondition.Serializer.INSTANCE);
        CraftingHelper.register(ModLoadedCondition.Serializer.INSTANCE);
        CraftingHelper.register(NotCondition.Serializer.INSTANCE);
        CraftingHelper.register(OrCondition.Serializer.INSTANCE);
        CraftingHelper.register(TrueCondition.Serializer.INSTANCE);
        CraftingHelper.register(TagEmptyCondition.Serializer.INSTANCE);

        CraftingHelper.register(new ResourceLocation("forge", "compound"), CompoundIngredient.Serializer.INSTANCE);
        CraftingHelper.register(new ResourceLocation("forge", "nbt"), IngredientNBT.Serializer.INSTANCE);
        CraftingHelper.register(new ResourceLocation("minecraft", "item"), VanillaIngredientSerializer.INSTANCE);

        event.getRegistry().register(new ConditionalRecipe.Serializer<IRecipe<?>>().setRegistryName(new ResourceLocation("forge", "conditional")));

    }
}
