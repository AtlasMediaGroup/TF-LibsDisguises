package me.libraryaddict.disguise.disguisetypes.watchers;

import me.libraryaddict.disguise.DisguiseConfig;
import me.libraryaddict.disguise.disguisetypes.Disguise;
import me.libraryaddict.disguise.disguisetypes.MetaIndex;
import me.libraryaddict.disguise.utilities.DisguiseUtilities;
import me.libraryaddict.disguise.utilities.parser.RandomDefaultValue;

public class SlimeWatcher extends InsentientWatcher {

    public SlimeWatcher(Disguise disguise) {
        super(disguise);

        if (DisguiseConfig.isRandomDisguises()) {
            setSize(DisguiseUtilities.random.nextInt(4) + 1);
        } else {
            setSize(2);
        }
    }

    public int getSize() {
        return getData(MetaIndex.SLIME_SIZE);
    }

    // TFM Start
    @RandomDefaultValue
    public void setSize(int size) {
        if (size < 1) {
            size = 1;
        } else if (size > 5) {
            size = 5;
        }
        // TFM End

        if (hasValue(MetaIndex.SLIME_SIZE) && getData(MetaIndex.SLIME_SIZE) == size) {
            return;
        }

        setData(MetaIndex.SLIME_SIZE, size);
        sendData(MetaIndex.SLIME_SIZE);

        updateNameHeight();
    }
}
