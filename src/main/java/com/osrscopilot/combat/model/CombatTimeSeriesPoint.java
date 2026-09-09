package com.osrscopilot.combat.model;

import java.util.ArrayList;
import java.util.List;

public class CombatTimeSeriesPoint
{
    private final int second;
    private int damageDealt = 0;
    private int meleeDamage = 0;
    private int rangedDamage = 0;
    private int magicDamage = 0;
    private int statusDamage = 0;
    private int thrallDamage = 0;
    private int cannonDamage = 0;

    private int damageTaken = 0;
    private int hpHealed = 0;

    private double rollingDps = 0.0;
    private double rollingMeleeDps = 0.0;
    private double rollingRangedDps = 0.0;
    private double rollingMagicDps = 0.0;
    private double rollingStatusDps = 0.0;
    private double rollingDtps = 0.0;
    private double rollingHps = 0.0;

    private long cumulativeDamage = 0;
    private long cumulativeDamageTaken = 0;
    private long cumulativeHealing = 0;
    private final List<String> eventNotes = new ArrayList<>();

    public CombatTimeSeriesPoint(int second)
    {
        this.second = second;
    }

    public int getSecond()
    {
        return second;
    }

    public int getDamageDealt()
    {
        return damageDealt;
    }

    public void setDamageDealt(int damageDealt)
    {
        this.damageDealt = damageDealt;
    }

    public int getMeleeDamage()
    {
        return meleeDamage;
    }

    public void setMeleeDamage(int meleeDamage)
    {
        this.meleeDamage = meleeDamage;
    }

    public int getRangedDamage()
    {
        return rangedDamage;
    }

    public void setRangedDamage(int rangedDamage)
    {
        this.rangedDamage = rangedDamage;
    }

    public int getMagicDamage()
    {
        return magicDamage;
    }

    public void setMagicDamage(int magicDamage)
    {
        this.magicDamage = magicDamage;
    }

    public int getStatusDamage()
    {
        return statusDamage;
    }

    public void setStatusDamage(int statusDamage)
    {
        this.statusDamage = statusDamage;
    }

    public int getThrallDamage()
    {
        return thrallDamage;
    }

    public void setThrallDamage(int thrallDamage)
    {
        this.thrallDamage = thrallDamage;
    }

    public int getCannonDamage()
    {
        return cannonDamage;
    }

    public void setCannonDamage(int cannonDamage)
    {
        this.cannonDamage = cannonDamage;
    }

    public int getDamageTaken()
    {
        return damageTaken;
    }

    public void setDamageTaken(int damageTaken)
    {
        this.damageTaken = damageTaken;
    }

    public int getHpHealed()
    {
        return hpHealed;
    }

    public void setHpHealed(int hpHealed)
    {
        this.hpHealed = hpHealed;
    }

    public double getRollingDps()
    {
        return rollingDps;
    }

    public void setRollingDps(double rollingDps)
    {
        this.rollingDps = rollingDps;
    }

    public double getRollingMeleeDps()
    {
        return rollingMeleeDps;
    }

    public void setRollingMeleeDps(double rollingMeleeDps)
    {
        this.rollingMeleeDps = rollingMeleeDps;
    }

    public double getRollingRangedDps()
    {
        return rollingRangedDps;
    }

    public void setRollingRangedDps(double rollingRangedDps)
    {
        this.rollingRangedDps = rollingRangedDps;
    }

    public double getRollingMagicDps()
    {
        return rollingMagicDps;
    }

    public void setRollingMagicDps(double rollingMagicDps)
    {
        this.rollingMagicDps = rollingMagicDps;
    }

    public double getRollingStatusDps()
    {
        return rollingStatusDps;
    }

    public void setRollingStatusDps(double rollingStatusDps)
    {
        this.rollingStatusDps = rollingStatusDps;
    }

    public double getRollingDtps()
    {
        return rollingDtps;
    }

    public void setRollingDtps(double rollingDtps)
    {
        this.rollingDtps = rollingDtps;
    }

    public double getRollingHps()
    {
        return rollingHps;
    }

    public void setRollingHps(double rollingHps)
    {
        this.rollingHps = rollingHps;
    }

    public long getCumulativeDamage()
    {
        return cumulativeDamage;
    }

    public void setCumulativeDamage(long cumulativeDamage)
    {
        this.cumulativeDamage = cumulativeDamage;
    }

    public long getCumulativeDamageTaken()
    {
        return cumulativeDamageTaken;
    }

    public void setCumulativeDamageTaken(long cumulativeDamageTaken)
    {
        this.cumulativeDamageTaken = cumulativeDamageTaken;
    }

    public long getCumulativeHealing()
    {
        return cumulativeHealing;
    }

    public void setCumulativeHealing(long cumulativeHealing)
    {
        this.cumulativeHealing = cumulativeHealing;
    }

    public List<String> getEventNotes()
    {
        return eventNotes;
    }

    public void addEventNote(String note)
    {
        if (note != null && !note.isEmpty())
        {
            this.eventNotes.add(note);
        }
    }
}
