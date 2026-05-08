package me.adam.specialitems;

import org.bukkit.plugin.java.JavaPlugin;

public final class SpecialItemsPlugin extends JavaPlugin {

    private ItemFactory itemFactory;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        this.itemFactory = new ItemFactory(this);

        var cmd = getCommand("specialitems");
        if (cmd == null) {
            getLogger().severe("Missing command specialitems in plugin.yml");
            return;
        }
        SpecialItemsCommand executor = new SpecialItemsCommand(this, itemFactory);
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);

        getServer().getPluginManager().registerEvents(new VoidCookieListener(this, itemFactory), this);
        getServer().getPluginManager().registerEvents(new FreezeSnowballListener(this, itemFactory), this);
        getServer().getPluginManager().registerEvents(new InfiniteRocketListener(this, itemFactory), this);
        getServer().getPluginManager().registerEvents(new MasterSwordListener(this, itemFactory), this);
    }

    public ItemFactory itemFactory() {
        return itemFactory;
    }
}
