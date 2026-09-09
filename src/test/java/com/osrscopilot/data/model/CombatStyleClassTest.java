package com.osrscopilot.data.model;

import org.junit.Assert;
import org.junit.Test;

public class CombatStyleClassTest
{
    @Test
    public void testClassify()
    {
        Assert.assertEquals(CombatStyleClass.MELEE, CombatStyleClass.classify("Melee"));
        Assert.assertEquals(CombatStyleClass.MELEE, CombatStyleClass.classify("Stab"));
        Assert.assertEquals(CombatStyleClass.MELEE, CombatStyleClass.classify("Slash/Crush"));
        Assert.assertEquals(CombatStyleClass.RANGED, CombatStyleClass.classify("Ranged"));
        Assert.assertEquals(CombatStyleClass.RANGED, CombatStyleClass.classify("Arrow"));
        Assert.assertEquals(CombatStyleClass.MAGIC, CombatStyleClass.classify("Magic"));
        Assert.assertEquals(CombatStyleClass.MAGIC, CombatStyleClass.classify("Dragonfire"));
        Assert.assertEquals(CombatStyleClass.MIXED, CombatStyleClass.classify("Melee, Magic"));
        Assert.assertEquals(CombatStyleClass.MIXED, CombatStyleClass.classify("Ranged and Magic"));
        Assert.assertEquals(CombatStyleClass.NONE, CombatStyleClass.classify("None (Neutral)"));
        Assert.assertEquals(CombatStyleClass.NONE, CombatStyleClass.classify("N/a"));
        Assert.assertEquals(CombatStyleClass.NONE, CombatStyleClass.classify(""));
        Assert.assertEquals(CombatStyleClass.UNKNOWN, CombatStyleClass.classify(null));
        Assert.assertEquals(CombatStyleClass.UNKNOWN, CombatStyleClass.classify("Various"));

        Monster m = Monster.builder().attackType("Ranged").weakness("None (Neutral)").build();
        Assert.assertEquals(CombatStyleClass.RANGED, m.getAttackStyleClass());
        Assert.assertEquals(CombatStyleClass.NONE, m.getWeaknessStyleClass());
    }
}
