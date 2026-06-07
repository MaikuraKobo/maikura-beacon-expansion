package jp.exnakamura.beaconexpansion.mixin;

import jp.exnakamura.beaconexpansion.BeaconExpansionMod;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin {
    @Inject(method = "updateLevel", at = @At("RETURN"))
    private static void maikura$rememberVanillaBeacon(World world, int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        BeaconExpansionMod.rememberBeaconAnchor(world, new BlockPos(x, y, z));
    }
}
