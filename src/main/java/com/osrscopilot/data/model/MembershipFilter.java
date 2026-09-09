package com.osrscopilot.data.model;

public enum MembershipFilter
{
    ALL("All"),
    F2P_ONLY("Free to Play"),
    MEMBERS_ONLY("Members Only");

    private final String name;

    MembershipFilter(String name)
    {
        this.name = name;
    }

    @Override
    public String toString()
    {
        return name;
    }
}
