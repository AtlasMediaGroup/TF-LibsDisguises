package me.libraryaddict.disguise;

import java.util.Arrays;
import java.util.List;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import static me.libraryaddict.disguise.disguisetypes.DisguiseType.*;

public class BlockedDisguises
{
    public static final List<DisguiseType> forbiddenDisguises = Arrays.asList(ITEM_FRAME, ENDER_DRAGON, PLAYER, GIANT, GHAST, DROPPED_ITEM, ENDER_CRYSTAL, AREA_EFFECT_CLOUD, WITHER);
    public static boolean disabled = false;

    public static boolean isAllowed(Disguise disguise)
    {
        return isAllowed(disguise.getType());
    }

    public static boolean isAllowed(DisguiseType type)
    {
        return !forbiddenDisguises.contains(type);
    }
}
