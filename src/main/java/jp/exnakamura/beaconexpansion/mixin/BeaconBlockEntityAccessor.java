package jp.exnakamura.beaconexpansion.mixin;

import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.entry.RegistryEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BeaconBlockEntity.class)
public interface BeaconBlockEntityAccessor {
    @Accessor("primary")
    RegistryEntry<StatusEffect> maikura$getPrimaryEffect();

    @Accessor("secondary")
    RegistryEntry<StatusEffect> maikura$getSecondaryEffect();
}
