package dev.consolepad.split;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Texto contextual del boton de usar, como las ayudas de consola de Bedrock: mirando un cofre dice "Abrir", una cama "Dormir", un caballo "Montar", un aldeano
 * "Comerciar", un animal con su comida en la mano "Dar de comer"; con un bloque en la mano y nada interactivo delante, "Colocar bloque"; con comida, "Comer".
 * Con el jugador agachado y un bloque en la mano, vanilla coloca el bloque aunque el de delante sea interactivo: se dice "Colocar bloque".
 */
public final class Interact {
    private Interact() {}

    /** Texto del boton de usar para lo que tiene delante el jugador (hit) y lo que lleva en la mano. */
    public static String useLabel(Player player, Level level, HitResult hit) {
        ItemStack hand = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        boolean placing = hand.getItem() instanceof BlockItem && player != null && player.isSecondaryUseActive();
        if (!placing && hit != null && level != null) {
            String target = null;
            if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK) target = forBlock(level.getBlockState(b.getBlockPos()), level, b.getBlockPos());
            else if (hit instanceof EntityHitResult e) target = forEntity(e.getEntity(), hand);
            if (target != null) return target;
        }
        return forItem(hand);
    }

    /** Texto por el bloque, o null si no es interactivo. */
    public static String forBlock(BlockState s, Level level, BlockPos pos) {
        Block b = s.getBlock();
        if (b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof ShulkerBoxBlock || b instanceof EnderChestBlock
                || b instanceof HopperBlock || b instanceof DispenserBlock || b instanceof AbstractFurnaceBlock || b instanceof BrewingStandBlock
                || b instanceof BeaconBlock || b instanceof LecternBlock) return "Abrir";
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock) {
            if (b == Blocks.IRON_DOOR || b == Blocks.IRON_TRAPDOOR) return null; // solo con redstone
            return s.hasProperty(BlockStateProperties.OPEN) && s.getValue(BlockStateProperties.OPEN) ? "Cerrar" : "Abrir";
        }
        if (b instanceof BedBlock) return "Dormir";
        if (b instanceof CraftingTableBlock || b instanceof AnvilBlock || b instanceof EnchantingTableBlock || b instanceof LoomBlock
                || b instanceof StonecutterBlock || b instanceof GrindstoneBlock || b instanceof SmithingTableBlock || b instanceof CartographyTableBlock) return "Usar";
        if (b instanceof ButtonBlock || b instanceof LeverBlock) return "Pulsar";
        if (b instanceof NoteBlock) return "Tocar";
        if (b instanceof JukeboxBlock) return "Poner disco";
        if (b instanceof CakeBlock) return "Comer";
        if (b instanceof ComparatorBlock || b instanceof RepeaterBlock || b instanceof DaylightDetectorBlock) return "Ajustar";
        return null;
    }

    /** Texto por la entidad, o null si no hay nada que hacer con ella. */
    public static String forEntity(Entity e, ItemStack hand) {
        if (e instanceof AbstractVillager) return "Comerciar";
        if (e instanceof Boat || e instanceof AbstractMinecart || e instanceof AbstractHorse) return "Montar";
        if (e instanceof Saddleable sd && sd.isSaddleable() && !sd.isSaddled() && hand.is(Items.SADDLE)) return "Ensillar";
        if (e instanceof Saddleable sd && sd.isSaddled()) return "Montar";
        if (e instanceof Cow && hand.is(Items.BUCKET)) return "Ordeñar";
        if (e instanceof Sheep && hand.is(Items.SHEARS)) return "Esquilar";
        if (e instanceof Animal a && !hand.isEmpty() && a.isFood(hand)) return "Dar de comer";
        if (e instanceof ItemFrame) return hand.isEmpty() ? "Girar" : "Colocar objeto";
        if (e instanceof ArmorStand) return "Equipar";
        return null;
    }

    /** Texto por lo que lleva en la mano cuando no hay nada interactivo delante. */
    public static String forItem(ItemStack hand) {
        if (hand.getItem() instanceof BlockItem) return "Colocar bloque";
        UseAnim anim = hand.isEmpty() ? UseAnim.NONE : hand.getUseAnimation();
        return switch (anim) {
            case EAT -> "Comer";
            case DRINK -> "Beber";
            case BLOCK -> "Bloquear";
            case BOW -> "Tensar";
            case CROSSBOW -> "Cargar";
            case SPEAR -> "Lanzar";
            default -> "Usar objeto";
        };
    }
}
