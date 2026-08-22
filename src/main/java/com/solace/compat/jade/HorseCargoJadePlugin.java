package com.solace.compat.jade;

import net.minecraft.world.entity.animal.horse.Horse;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class HorseCargoJadePlugin implements IWailaPlugin {
    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerEntityComponent(new HorseCargoJadeProvider(), Horse.class);
    }
}
