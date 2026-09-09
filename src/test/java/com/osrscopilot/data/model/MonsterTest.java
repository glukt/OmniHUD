package com.osrscopilot.data.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

public class MonsterTest
{
    private static Monster monster(int id, String name, int combatLevel)
    {
        return Monster.builder().id(id).name(name).combatLevel(combatLevel).build();
    }

    @Test
    public void testEqualityAndHashCodeAreIdOnly()
    {
        Monster a = monster(265, "Blue dragon", 111);
        // Same id, everything else different (name, combat level, and - crucially - a populated
        // drop list that the old @Value equals/hashCode would have deep-walked).
        Monster b = Monster.builder()
            .id(265)
            .name("Blue dragon (variant)")
            .combatLevel(999)
            .drops(List.of(MonsterDrop.builder().name("Dragon bones").rarity(1.0).build()))
            .build();

        Assert.assertEquals("same id -> equal", a, b);
        Assert.assertEquals("same id -> identical hashCode", a.hashCode(), b.hashCode());

        Monster different = monster(266, "Blue dragon", 111);
        Assert.assertNotEquals("different id -> not equal", a, different);
    }

    @Test
    public void testHashSetLookupResolvesByIdWithoutStructuralHash()
    {
        Set<Monster> set = new HashSet<>();
        set.add(monster(265, "Blue dragon", 111));

        // A freshly built instance carrying the same id must be found - this is the
        // MonsterDatabase.searchMonsters(query, category) contains()-per-keystroke path.
        Assert.assertTrue(set.contains(monster(265, "Blue dragon", 111)));
        Assert.assertFalse(set.contains(monster(9999, "Blue dragon", 111)));
    }
}
