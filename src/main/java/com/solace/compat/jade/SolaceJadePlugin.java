package com.solace.compat.jade;

import io.redspace.ironsspellbooks.block.alchemist_cauldron.AlchemistCauldronBlock;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class SolaceJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(new AlchemistCauldronJadeProvider(), AlchemistCauldronBlock.class);
        registration.hideTarget(com.solace.world.DreamDimension.FLOOR.get());
    }
}
