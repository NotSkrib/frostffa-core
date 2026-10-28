package dev.notskrib.frostffacore.shop;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.Material;

/** One named shop (e.g. "weapons", "cosmetics") holding its own catalog of {@link ShopEntry}. */
public class ShopCategory {

    private final String id;
    private String name;
    private Material icon;
    private final Map<String, ShopEntry> entries = new LinkedHashMap<>();

    public ShopCategory(String id, String name, Material icon) {
        this.id = id;
        this.name = name;
        this.icon = icon;
    }

    public String id() { return this.id; }
    public String name() { return this.name; }
    public void name(String name) { this.name = name; }
    public Material icon() { return this.icon; }
    public void icon(Material icon) { this.icon = icon; }
    public Map<String, ShopEntry> entries() { return this.entries; }
}
