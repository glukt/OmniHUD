package com.osrscopilot.data.model;

public enum CurrencyType
{
    COINS("Coins", "gp"),
    TOKKUL("Tokkul", "tokkul"),
    MARK_OF_GRACE("Mark of grace", "marks"),
    WARRIORS_GUILD_TOKEN("Warrior guild token", "tokens"),
    TRADING_STICKS("Trading sticks", "sticks"),
    CASTLE_WARS_TICKET("Castle wars ticket", "tickets"),
    NUMULITE("Numulite", "numulite"),
    STARDUST("Stardust", "stardust"),
    MOLCH_PEARL("Molch pearl", "pearls"),
    GOLDEN_NUGGET("Golden nugget", "nuggets"),
    ABYSSAL_PEARLS("Abyssal pearls", "pearls"),
    FROG_TOKEN("Frog token", "tokens"),
    SLAYER_POINTS("Slayer Points", "pts");

    private final String fullName;
    private final String shortName;

    CurrencyType(String fullName, String shortName)
    {
        this.fullName = fullName;
        this.shortName = shortName;
    }

    public String getFullName()
    {
        return fullName;
    }

    public String getShortName()
    {
        return shortName;
    }

    public static CurrencyType fromCode(String code)
    {
        if (code == null)
        {
            return COINS;
        }
        try
        {
            return CurrencyType.valueOf(code.toUpperCase().replace(" ", "_"));
        }
        catch (IllegalArgumentException e)
        {
            return COINS;
        }
    }
}
