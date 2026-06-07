package jp.exnakamura.beaconexpansion.client;

import jp.exnakamura.beaconexpansion.BeaconExpansionMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.render.BlockRenderLayer;

public class BeaconExpansionClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // dev6.2: Fabric 1.21.11 では RenderLayer.getTranslucent() ではなく
        // BlockRenderLayer.TRANSLUCENT を BlockRenderLayerMap に登録する。
        // 透明ガラス外殻を持つビーコン系モデルが設置後に金/紫の箱化する問題を抑える。
        BlockRenderLayerMap.putBlocks(
                BlockRenderLayer.TRANSLUCENT,
                BeaconExpansionMod.BEACON_CORE,
                BeaconExpansionMod.ENHANCED_BEACON_CORE,
                BeaconExpansionMod.GOLD_DECORATED_GLASS,
                BeaconExpansionMod.PURPLE_GOLD_DECORATED_GLASS
        );
    }
}
