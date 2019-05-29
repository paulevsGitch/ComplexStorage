package paulevs.complexstorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class ComplexStorage extends JavaPlugin implements Listener
{
	private class CoordinatePair
	{
		Location start;
		Location end;
		
		CoordinatePair(Location start, Location end)
		{
			this.start = start;
			this.end = end;
		}
		
		@Override
		public boolean equals(Object obj)
		{
			return obj instanceof CoordinatePair && ((CoordinatePair) obj).end.equals(end) && ((CoordinatePair) obj).start.equals(start);
		}
	}
	
	private class StorageData
	{
		CoordinatePair bounds;
		Inventory[] inventories;
		int index;
		boolean mustOpen;
	}
	
	private class SortByName implements Comparator<ItemStack>
	{
		@Override
		public int compare(ItemStack item1, ItemStack item2)
		{
			String s1 = item1.getType().name();
			String s2 = item2.getType().name();
			return s1.compareTo(s2);
		}
	}
	
	private static Material surface = Material.GLASS;
	private static Material filler = Material.CHEST;
	private static Material edge = Material.SMOOTH_STONE;
	
	private static int minSize = 4;
	private static int maxSize = 20;
	
	private static HashMap<Player, StorageData> storageData = new HashMap<Player, StorageData>();
	
	private static ItemStack arrowBack;
	private static ItemStack arrowNext;
	private static ItemStack whiteFiller;
	
	private static String arrowBackName = "§r§2Previous";
	private static String arrowNextName = "§r§aNext";
	private static String inUsage = "This storage is in use now";
	private static String titleFormat = "Page %d of %d";
	
	@Override
	public void onEnable()
	{
		getServer().getPluginManager().registerEvents(this, this);
		loadConfig();
		initFillers();
	}
	
	private void initFillers()
	{
		arrowBack = new ItemStack(Material.ARROW);
		ItemMeta meta = arrowBack.getItemMeta();
		meta.setDisplayName(arrowBackName);
		arrowBack.setItemMeta(meta);
		
		arrowNext = new ItemStack(Material.ARROW);
		meta = arrowNext.getItemMeta();
		meta.setDisplayName(arrowNextName);
		arrowNext.setItemMeta(meta);
		
		whiteFiller = new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
		meta = whiteFiller.getItemMeta();
		meta.setDisplayName(" ");
		whiteFiller.setItemMeta(meta);
	}
	
	private void loadConfig()
	{
		getConfig().options().copyDefaults(true);
		
		if (!getConfig().contains("materials.surface"))
			getConfig().set("materials.surface", surface.name());
		surface = Material.getMaterial(getConfig().getString("materials.surface", surface.name()));
		
		if (!getConfig().contains("materials.filler"))
			getConfig().set("materials.filler", filler.name());
		filler = Material.getMaterial(getConfig().getString("materials.filler", filler.name()));
		
		if (!getConfig().contains("materials.edge"))
			getConfig().set("materials.edge", edge.name());
		edge = Material.getMaterial(getConfig().getString("materials.edge", edge.name()));
		
		if (!getConfig().contains("size.minimum"))
			getConfig().set("size.minimum", minSize);
		minSize = getConfig().getInt("size.minimum", minSize);
		
		if (!getConfig().contains("size.maximum"))
			getConfig().set("size.maximum", maxSize);
		maxSize = getConfig().getInt("size.maximum", maxSize);
		
		if (!getConfig().contains("title.back"))
			getConfig().set("title.back", arrowBackName);
		arrowBackName = getConfig().getString("title.back", arrowBackName);
		
		if (!getConfig().contains("title.next"))
			getConfig().set("title.next", arrowNextName);
		arrowNextName = getConfig().getString("title.next", arrowNextName);
		
		if (!getConfig().contains("title.page-format"))
			getConfig().set("title.page-format", titleFormat);
		titleFormat = getConfig().getString("title.page-format", titleFormat);
		
		if (!getConfig().contains("message.in-usage"))
			getConfig().set("message.in-usage", inUsage);
		inUsage = getConfig().getString("message.in-usage", inUsage);
		
		saveConfig();
	}
	
	@EventHandler
	public void onBlockClick(PlayerInteractEvent e)
	{
		if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getHand() == EquipmentSlot.HAND)
		{
			CoordinatePair bounds = findBounds(e.getClickedBlock().getLocation());
			if (bounds != null && isValidBounds(bounds) && checkStructure(bounds.start, bounds.end))
			{
				e.setCancelled(true);
				if (isStorageInUse(bounds))
				{
					e.getPlayer().sendMessage(inUsage);
					return;
				}
				else
				{
					List<ItemStack> items = getContainedItems(bounds);
					Collections.sort(items, new SortByName());
					Inventory[] inv = new Inventory[countStorageSpace(bounds)];
					int index = 0;
					boolean add = items.size() > 0;
					for (int i = 0; i < inv.length; i++)
					{
						inv[i] = Bukkit.createInventory(null, 36, String.format(titleFormat, i + 1, inv.length));
						fillControls(inv[i]);
						if (add)
							for (int j = 0; j < 27; j++)
							{
								inv[i].setItem(j, items.get(index++));
								if (index >= items.size())
								{
									add = false;
									break;
								}
							}
					}
					
					StorageData st = new StorageData();
					st.bounds = bounds;
					st.inventories = inv;
					st.index = 0;
					st.mustOpen = false;
					storageData.put(e.getPlayer(), st);
					
					e.getPlayer().openInventory(inv[0]);
				}
			}
		}
	}
	
	@EventHandler
	public void onCloseInventory(InventoryCloseEvent e)
	{
		if (e.getPlayer() instanceof Player)
		{
			Player p = (Player) e.getPlayer();
			if (storageData.containsKey(p))
			{
				StorageData data = storageData.get(p);
				if (!data.mustOpen)
				{
					List<ItemStack> items = getAllContents(data.inventories);
					items = putItemsInStorage(data.bounds, items);
					storageData.remove(p);
					if (!items.isEmpty())
						for (ItemStack i: items)
							p.getWorld().dropItem(p.getLocation(), i);
				}
				else
					data.mustOpen = false;
			}
		}
	}
	
	@EventHandler
	public void onInventoryClick(InventoryClickEvent e)
	{
		if (e.getWhoClicked() instanceof Player)
		{
			Player p = (Player) e.getWhoClicked();
			if (storageData.containsKey(p))
			{
				StorageData data = storageData.get(p);
				if (e.getRawSlot() > 26 && e.getRawSlot() < 36)
					e.setCancelled(true);
				if (e.getRawSlot() == 27)
				{
					int index = data.index - 1;
					if (index < 0)
						index += data.inventories.length;
					data.mustOpen = true;
					data.index = index;
					p.openInventory(data.inventories[index]);
				}
				else if (e.getRawSlot() == 35)
				{
					int index = data.index + 1;
					if (index >= data.inventories.length)
						index -= data.inventories.length;
					data.mustOpen = true;
					data.index = index;
					p.openInventory(data.inventories[index]);
				}
				else
				{
					data.mustOpen = false;
				}
			}
		}
	}
	
	@EventHandler
	public void onBlockBreak(final BlockBreakEvent e)
	{
		for (StorageData st: storageData.values())
			if (isInside(st.bounds, e.getBlock().getLocation()))
			{
				e.getPlayer().sendMessage(inUsage);
				e.setCancelled(true);
			}
	}
	
	@EventHandler
	public void onPistonMove(BlockPistonExtendEvent e)
	{
		for (StorageData st: storageData.values())
			if (isInside(st.bounds, e.getBlock().getLocation().add(e.getDirection().getDirection())))
			{
				e.setCancelled(true);
			}
	}
	
	@EventHandler
	public void onPistonMove(BlockPistonRetractEvent e)
	{
		for (StorageData st: storageData.values())
			if (isInside(st.bounds, e.getBlock().getLocation().add(e.getDirection().getDirection())))
			{
				e.setCancelled(true);
			}
	}
	
	@EventHandler
	public void onBlockExplode(final BlockExplodeEvent e)
	{
		for (StorageData st: storageData.values())
			if (isInside(st.bounds, e.getBlock().getLocation()))
			{
				e.setCancelled(true);
			}
	}
	
	private List<ItemStack> getAllContents(Inventory[] inv)
	{
		List<ItemStack> items = new ArrayList<ItemStack>();
		for (Inventory i: inv)
			for (int j = 0; j < 27; j++)
			{
				ItemStack item = i.getItem(j);
				if (item != null)
					items.add(item);
			}
		return items;
	}
	
	private boolean isInside(CoordinatePair p, Location l)
	{
		return l.getBlockX() >= p.start.getBlockX() &&
				l.getBlockX() <= p.end.getBlockX() &&
				l.getBlockY() >= p.start.getBlockY() &&
				l.getBlockY() <= p.end.getBlockY() &&
				l.getBlockZ() >= p.start.getBlockZ() &&
				l.getBlockZ() <= p.end.getBlockZ();
	}
	
	private void fillControls(Inventory inv)
	{
		inv.setItem(27, arrowBack.clone());
		inv.setItem(35, arrowNext.clone());
		for (int i = 28; i < 35; i++)
			inv.setItem(i, whiteFiller.clone());
	}
	
	private int countStorageSpace(CoordinatePair pair)
	{
		int res = (pair.end.getBlockX() - pair.start.getBlockX() - 1);
		res *= (pair.end.getBlockY() - pair.start.getBlockY() - 1);
		res *= (pair.end.getBlockZ() - pair.start.getBlockZ() - 1);
		return res;
	}
	
	private boolean isBoxMaterial(Material mat)
	{
		return mat == surface || mat == filler || mat == edge;
	}
	
	private CoordinatePair findBounds(Location loc)
	{
		Material sel = loc.getBlock().getType();
		World w = loc.getWorld();
		if (isBoxMaterial(sel))
		{
			int x = loc.getBlockX();
			int y = loc.getBlockY();
			int z = loc.getBlockZ();
			
			int x1 = x;
			int x2 = x;
			int y1 = y;
			int y2 = y;
			int z1 = z;
			int z2 = z;
			
			while(isBoxMaterial(sel))
				sel = w.getBlockAt(x1--, y, z).getType();
			sel = surface;
			while(isBoxMaterial(sel))
				sel = w.getBlockAt(x2++, y, z).getType();
			sel = surface;
			while(isBoxMaterial(sel))
				sel = w.getBlockAt(x, y1--, z).getType();
			sel = surface;
			while(isBoxMaterial(sel))
				sel = w.getBlockAt(x, y2++, z).getType();
			sel = surface;
			while(isBoxMaterial(sel))
				sel = w.getBlockAt(x, y, z1--).getType();
			sel = surface;
			while(isBoxMaterial(sel))
				sel = w.getBlockAt(x, y, z2++).getType();
			return new CoordinatePair(new Location(w, x1 + 2, y1 + 2, z1 + 2), new Location(w, x2 - 2, y2 - 2, z2 - 2));
		}
		return null;
	}
	
	private boolean checkStructure(Location start, Location end)
	{
		World w = start.getWorld();
		Material sel;
		for (int x = start.getBlockX(); x <= end.getBlockX(); x++)
			for (int y = start.getBlockY(); y <= end.getBlockY(); y++)
				for (int z = start.getBlockZ(); z <= end.getBlockZ(); z++)
				{
					sel = w.getBlockAt(x, y, z).getType();
					if (x == start.getBlockX() || x == end.getBlockX())
					{
						if (y == start.getBlockY() || z == start.getBlockZ() || y == end.getBlockY() || z == end.getBlockZ())
						{
							if (sel != edge)
								return false;
						}
						else if (sel != surface)
							return false;
					}
					else if (y == start.getBlockY() || y == end.getBlockY())
					{
						if (x == start.getBlockX() || z == start.getBlockZ() || x == end.getBlockX() || z == end.getBlockZ())
						{
							if (sel != edge)
								return false;
						}
						else if (sel != surface)
							return false;
					}
					else if (z == start.getBlockZ() || z == end.getBlockZ())
					{
						if (x == start.getBlockX() || y == start.getBlockY() || x == end.getBlockX() || y == end.getBlockY())
						{
							if (sel != edge)
								return false;
						}
						else if (sel != surface)
							return false;
					}
					else if (sel != filler)
						return false;
				}
		return true;
	}
	
	private boolean isValidBounds(CoordinatePair pair)
	{
		int dx = pair.end.getBlockX() - pair.start.getBlockX() + 1;
		int dy = pair.end.getBlockY() - pair.start.getBlockY() + 1;
		int dz = pair.end.getBlockZ() - pair.start.getBlockZ() + 1;
		return dx >= minSize && dx <= maxSize && dy >= minSize && dy <= maxSize && dz >= minSize && dz <= maxSize;
	}
	
	private List<ItemStack> getContainedItems(CoordinatePair pair)
	{
		List<ItemStack> items = new ArrayList<ItemStack>();
		World w = pair.start.getWorld();
		Chest chest;
		Inventory inventory;
		for (int x = pair.start.getBlockX() + 1; x < pair.end.getBlockX(); x++)
			for (int y = pair.start.getBlockY() + 1; y < pair.end.getBlockY(); y++)
				for (int z = pair.start.getBlockZ() + 1; z < pair.end.getBlockZ(); z++)
				{
					chest = (Chest) w.getBlockAt(x, y, z).getState();
					inventory = chest.getBlockInventory();
					for (ItemStack s: inventory.getContents())
						if (s != null)
							items.add(s);
				}
		return items;
	}
	
	private List<ItemStack> putItemsInStorage(CoordinatePair pair, List<ItemStack> items)
	{
		Chest chest;
		Material mat;
		Inventory inventory;
		World w = pair.start.getWorld();
		int index = 0;
		boolean mustPut = items.size() > 0;
		List<ItemStack> itemsRemain = new ArrayList<ItemStack>();
		itemsRemain.addAll(items);
		for (int x = pair.start.getBlockX() + 1; x < pair.end.getBlockX(); x++)
			for (int y = pair.start.getBlockY() + 1; y < pair.end.getBlockY(); y++)
				for (int z = pair.start.getBlockZ() + 1; z < pair.end.getBlockZ(); z++)
				{
					mat = w.getBlockAt(x, y, z).getType();
					if (isChest(mat))
					{
						chest = (Chest) w.getBlockAt(x, y, z).getState();
						inventory = chest.getBlockInventory();
						inventory.clear();
						if (mustPut)
							for (int i = 0; i < inventory.getSize(); i++)
							{
								ItemStack item = items.get(index);
								if (item != null)
								{
									inventory.addItem(item);
									itemsRemain.remove(item);
								}
								index++;
								if (index >= items.size())
								{
									mustPut = false;
									break;
								}
							}
					}	
				}
		return itemsRemain;
	}
	
	private boolean isStorageInUse(CoordinatePair bounds)
	{
		for (StorageData sd: storageData.values())
			if (sd.bounds.equals(bounds))
				return true;
		return false;
	}
	
	private boolean isChest(Material mat)
	{
		return mat == Material.CHEST || mat == Material.TRAPPED_CHEST;
	}
}
