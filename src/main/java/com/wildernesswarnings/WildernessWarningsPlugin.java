package com.wildernesswarnings;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.ClientTick;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.menuentryswapper.MenuEntrySwapperPlugin;
import net.runelite.client.util.Text;
import net.runelite.client.util.WildcardMatcher;

@Slf4j
@PluginDescriptor(
	name = "Wilderness Warnings"
)
@PluginDependency(MenuEntrySwapperPlugin.class)
public class WildernessWarningsPlugin extends Plugin
{

	//TODO: enter wilderness portals in house
	static final String WILDERNESS_ACCESS_MENU_ENTRIES = "rub,Burning*\nChaos*,Burning*\nBandit*,Burning*" +
		"\nLava*,Burning*\nBreak,Annakarl*\nBreak,Dareeyak*\nBreak,Carrallangar*\nBreak,Ghorrock*\nBreak,Wilderness*\n" +
		"Break,Ice Plateau*\nTeleport,Revenant Cave*\nTeleport,Wilderness*\nCross,Wilderness*\nTravel to Wilderness*,*\n" +
		"Travel to Ferox*,*\nHunter Cape*,Teleport\nPass-Through,Barrier\nblack chin*,*\nEnter,Annakarl*\nEnter,Carrallangar*\n" +
		"Enter,Dareeyak*\nEnter,Ice Plateau*\nEnter,Ghorrock\nActivate,Obelisk\nTeleport to Destination,Obelisk\nCast,Ghorrock*\nCast,Annakarl\n" +
		"Cast,Carrallangar\nCast,Dareeyak\nCast, Ice Plat*\n";

	static final String EDGEVILLE_AND_ARDOUGNE_LEVER = "Pull,Lever\n";
	static final String CORP_BEAST_CAVE_EXIT = "Exit,Cave exit\n";
	final List<CustomSwap> customHides = new ArrayList<>();

	@Inject
	private Notifier notifier;

	@Inject
	private Client client;

	@Inject
	private WildernessWarningsConfig config;

	@Getter
	@ToString
	@RequiredArgsConstructor
	@EqualsAndHashCode
	static class CustomSwap
	{
		private final String option;
		private final String target;
		private final String topOption;
		private final String topTarget;

		CustomSwap(String option, String target)
		{
			this(option, target, null, null);
		}
	}

	@Override
	protected void startUp() throws Exception
	{
		customHides.clear();
		customHides.addAll(loadCustomSwaps(WILDERNESS_ACCESS_MENU_ENTRIES));
	}

	private Collection<? extends CustomSwap> loadCustomSwaps(String customSwaps)
	{
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			//Edgeville or Ardougne Level
			if (client.getLocalPlayer().getWorldLocation().getRegionID() == 12342
				|| client.getLocalPlayer().getWorldLocation().getRegionID() == 10291)
			{
				customSwaps += EDGEVILLE_AND_ARDOUGNE_LEVER;
			}
			//Corporeal Beast Cave
			else if (client.getLocalPlayer().getWorldLocation().getRegionID() == 11842)
			{
				customSwaps += CORP_BEAST_CAVE_EXIT;
			}
		}
		List<CustomSwap> swaps = new ArrayList<>();
		for (String customSwap : customSwaps.split("\n"))
		{
			if (customSwap.trim().equals(""))
			{
				continue;
			}
			String[] split = customSwap.split(",");
			swaps.add(new CustomSwap(
				split[0].toLowerCase().trim(),
				split.length > 1 ? split[1].toLowerCase().trim() : "",
				split.length > 2 ? split[2].toLowerCase().trim() : null,
				split.length > 3 ? split[3].toLowerCase().trim() : null
			));
		}
		return swaps;
	}

	public void customSwaps()
	{
		if (client.getWorld() == 474 || client.getWorld() == 533 || client.getWorld() == 365)
		{
			MenuEntry[] menuEntries = client.getMenuEntries();
			if (menuEntries.length == 0)
			{
				return;
			}
			menuEntries = filterEntries(menuEntries);

			client.setMenuEntries(menuEntries);

		}
	}

	private MenuEntry[] filterEntries(MenuEntry[] menuEntries)
	{
		ArrayList<MenuEntry> filtered = new ArrayList<>();
		for (MenuEntry entry : menuEntries)
		{
			String option = Text.standardize(Text.removeTags(entry.getOption()));
			String target = Text.standardize(Text.removeTags(entry.getTarget()));
			if (matches(option, target, menuEntries, customHides) == -1)
			{
				filtered.add(entry);
			}
		}
		return filtered.toArray(new MenuEntry[0]);
	}

	private int matches(String entryOption, String entryTarget, MenuEntry[] entries, List<CustomSwap> swaps)
	{
		int topEntryIndex = getTopMenuEntryIndex(entries);
		MenuEntry topEntry = entries[topEntryIndex];
		String target = Text.standardize(topEntry.getTarget());
		String option = Text.standardize(topEntry.getOption());
		for (int i = 0; i < swaps.size(); i++)
		{
			CustomSwap _configEntry = swaps.get(i);
			if (
				(
					_configEntry.option.equals(entryOption)
						|| WildcardMatcher.matches(_configEntry.option, entryOption)
				)
					&& (
					_configEntry.target.equals(entryTarget)
						|| WildcardMatcher.matches(_configEntry.target, entryTarget)
				)
			)
			{
				boolean a = (_configEntry.topOption == null);
				boolean b = (_configEntry.topTarget == null);
				Supplier<Boolean> c = () -> (_configEntry.topOption.equals(option) || WildcardMatcher
					.matches(_configEntry.topOption, option));
				Supplier<Boolean> d = () -> (_configEntry.topTarget.equals(target) || WildcardMatcher
					.matches(_configEntry.topTarget, target));
				if (a || b || (c.get() && d.get()))
				{
					return i;
				}
			}
		}
		return -1;
	}

	private int getTopMenuEntryIndex(MenuEntry[] menuEntries)
	{
		for (int i = menuEntries.length - 1; i >= 0; i--)
		{
			if (menuEntries[i].getType().getId() < 1000 && !menuEntries[i].isDeprioritized())
			{
				return i;
			}
		}
		return menuEntries.length - 1;
	}


	@Provides
	WildernessWarningsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(WildernessWarningsConfig.class);
	}

	@Subscribe(priority = -1)
	// This will run after the normal menu entry swapper, so it won't interfere with this plugin.
	public void onClientTick(ClientTick clientTick)
	{
		// The menu is not rebuilt when it is open, so don't swap or else it will
		// repeatedly swap entries
		if (client.getGameState() != GameState.LOGGED_IN || client.isMenuOpen())
		{
			return;
		}
		if (client.getLocalPlayer().getWorldLocation().getRegionID() == 12342
			|| client.getLocalPlayer().getWorldLocation().getRegionID() == 10291
			|| client.getLocalPlayer().getWorldLocation().getRegionID() == 11842)
		{
			customHides.clear();
			customHides.addAll(loadCustomSwaps(WILDERNESS_ACCESS_MENU_ENTRIES));
		}
		customSwaps();

		//TODO. Some way to check if player has any master, elite or hard without listing every item ID?
//		if (client.getLocalPlayer().getWorldLocation().getRegionID() == 7770
//			|| client.getLocalPlayer().getWorldLocation().getRegionID() == 7769) {
//			if (client.getItemContainer(InventoryID.INVENTORY).contains(ItemID.CLUE_SCROLL_MASTER)
//				|| client.getItemContainer(InventoryID.INVENTORY).contains(ItemID.CLUE_SCROLL_ELITE)
//				|| client.getItemContainer(InventoryID.INVENTORY).contains(ItemID.CLUE_SCROLL_HARD)) {
//					notifier.notify("You are on a target world!");
//			}
//		}

	}
}
