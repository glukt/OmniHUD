package com.osrscopilot.combat;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;

/**
 * Copies the one-line combat summary to the system clipboard so the player can paste it wherever
 * they like (Ctrl+V works in the RuneLite chat box). {@link #copyForChannel} prepends the OSRS
 * channel prefix ({@code /} friends, {@code //} clan) to the copied text.
 *
 * <p>The plugin never types into the chat box or sends chat itself - the RuneLite third-party
 * client rules prohibit programmatically inserting text into the chat input, so this is
 * clipboard-only and the player does the paste + send.
 */
@Singleton
public class CombatChatShare
{
    private final Client client;
    private final ClientThread clientThread;

    @Inject
    public CombatChatShare(Client client, ClientThread clientThread)
    {
        this.client = client;
        this.clientThread = clientThread;
    }

    /** Copy to the system clipboard, retrying a few times past the "clipboard busy" lock Windows throws. */
    public void copy(String text)
    {
        if (text == null || text.isEmpty())
        {
            return;
        }
        boolean ok = false;
        for (int attempt = 0; attempt < 4 && !ok; attempt++)
        {
            try
            {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
                ok = true;
            }
            catch (IllegalStateException busy)
            {
                try
                {
                    Thread.sleep(20L);
                }
                catch (InterruptedException ie)
                {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            catch (Exception other)
            {
                break;
            }
        }
        gameMessage(ok
            ? "summary copied - click the chat box and Ctrl+V, then Enter."
            : "couldn't copy - the clipboard is busy, try again.");
    }

    /** Copy the summary with the channel prefix ({@code ""} / {@code "/"} / {@code "//"}) already on it. */
    public void copyForChannel(ShareChannel channel, String text)
    {
        if (text == null || text.isEmpty())
        {
            return;
        }
        copy((channel == null ? "" : channel.prefix()) + text);
    }

    private void gameMessage(String msg)
    {
        if (client == null || clientThread == null)
        {
            return;
        }
        clientThread.invoke(() ->
            client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[Combat] " + msg, null));
    }
}
