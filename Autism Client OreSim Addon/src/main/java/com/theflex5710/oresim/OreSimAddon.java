package com.theflex5710.oresim;

import autismclient.api.AutismAddon;
import autismclient.api.ApiVersion;
import autismclient.api.AutismAddons;
import com.theflex5710.oresim.commands.SeedCommand;
import com.theflex5710.oresim.modules.OreSimModule;
import com.theflex5710.oresim.utils.SeedStore;

public final class OreSimAddon extends AutismAddon {
    public OreSimAddon() {
        name = "OreSim Addon";
        authors = "theflex5710";
        color = 0xFF21F4FF;
    }

    @Override
    public int apiVersion() {
        return ApiVersion.CURRENT;
    }

    @Override
    public void onInitialize() {
        SeedStore.load();
        AutismAddons.modules().register(new OreSimModule());
        AutismAddons.commands().register(new SeedCommand());
    }

    @Override
    public String getPackage() {
        return "com.theflex5710.oresim";
    }
}
