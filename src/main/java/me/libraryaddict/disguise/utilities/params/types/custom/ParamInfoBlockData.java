package me.libraryaddict.disguise.utilities.params.types.custom;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.libraryaddict.disguise.utilities.params.ParamInfo;
import me.libraryaddict.disguise.utilities.parser.DisguiseParseException;
import me.libraryaddict.disguise.utilities.reflection.NmsVersion;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Stairs;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Created by libraryaddict on 12/08/2020.
 */
public class ParamInfoBlockData extends ParamInfo {
    private Material[] materials;
    private Cache<Material, Map.Entry<String, String[]>> blockDatas = CacheBuilder.newBuilder()
            .expireAfterAccess(30, TimeUnit.MINUTES).maximumSize(20).build();

    public ParamInfoBlockData(Class paramClass, String name, String description, Material[] possibleValues) {
        super(paramClass, name, "BlockData[State=Something]", description);

        materials = Arrays.stream(possibleValues).filter(m -> {
            switch (m) {
                case CHEST:
                case TRAPPED_CHEST:
                    return false;
                default:
                    break;
            }

            if (!m.isBlock()) {
                return false;
            } else if (NmsVersion.v1_13.isSupported()) {
                return true;
            }

            switch (m) {
                case CAKE:
                case FLOWER_POT:
                case CAULDRON:
                case BREWING_STAND:
                    return false;
                default:
                    return true;
            }
        }).toArray(Material[]::new);
    }

    @Override
    protected Object fromString(String string) throws DisguiseParseException {
        if (string == null || string.equals("null")) {
            return null;
        }

        return Bukkit.createBlockData(string);
    }

    @Override
    public String toString(Object object) {
        if (object == null) {
            return "null";
        }

        return ((BlockData) object).getAsString();
    }

    @Override
    public boolean isParam(Class paramClass) {
        return getParamClass().isAssignableFrom(paramClass);
    }

    private Material getMaterial(String name) {
        return Material.matchMaterial(name, false);
    }

    public Set<String> getEnums(String tabComplete) {
        String s = tabComplete.toLowerCase();
        HashSet<String> returns = new HashSet<>();

        if (s.matches("[a-z_:]+\\[.*")) {
            s = s.substring(0, s.indexOf("["));
        } else if (tabComplete.matches("[a-z:_]+")) {
            for (Material m : materials) {
                if (m.isLegacy() || !m.isBlock()) {
                    continue;
                }

                if (!m.name().toLowerCase().startsWith(s) && !m.getKey().toString().startsWith(s)) {
                    continue;
                }

                if (m.name().toLowerCase().startsWith(s)) {
                    returns.add(m.name());
                } else {
                    returns.add(m.getKey().toString());
                }
            }
        } else {
            return returns;
        }

        Material mat = getMaterial(s);

        if (mat == null) {
            return returns;
        }

        // TODO Maybe auto complete blockstate states
        // Then again, it means I need to get the block states on a new IBlockData
        // Then call toBukkit with the block states then turn them into strings
        // Then handle the edge cases where they are not enums.. Idk, I think I'm going to ignore this.
        // No one cares about auto completion of this either

        return returns;
    }
}